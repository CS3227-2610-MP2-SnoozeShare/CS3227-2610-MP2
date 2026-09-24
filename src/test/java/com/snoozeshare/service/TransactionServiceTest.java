package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.TransactionServiceImpl;

import java.time.LocalTime;
import java.util.Set;
import com.snoozeshare.domain.model.Property;

class TransactionServiceTest {

    @Test
    void holdEscrowDeductsGuestWalletAndCreatesTransaction() throws Exception {
        try (Connection connection = migratedConnection()) {
            var context = seedBookingContext(connection, new BigDecimal("500.00"));
            TransactionService service = createService(connection);

            WalletTransaction transaction = service.holdEscrow(context.bookingId);

            assertNotNull(transaction);
            assertEquals(WalletTransactionType.ESCROW_HOLD, transaction.type());
            assertEquals(0, context.bookingTotal.negate().compareTo(transaction.amount()));
            assertEquals(context.bookingId, transaction.relatedBookingId());
            BigDecimal expectedBalance = context.walletBalance.subtract(context.bookingTotal);
            assertEquals(0, expectedBalance.compareTo(transaction.balanceAfter()));
        }
    }

    @Test
    void holdEscrowFailsOnInsufficientFunds() throws Exception {
        try (Connection connection = migratedConnection()) {
            var context = seedBookingContext(connection, new BigDecimal("100.00"));
            TransactionService service = createService(connection);

            assertThrows(IllegalArgumentException.class,
                    () -> service.holdEscrow(context.bookingId));
        }
    }

    @Test
    void holdEscrowFailsOnMissingBooking() throws Exception {
        try (Connection connection = migratedConnection()) {
            MigrationRunner.migrate(connection);
            TransactionService service = createService(connection);

            assertThrows(IllegalArgumentException.class,
                    () -> service.holdEscrow(UUID.randomUUID()));
        }
    }

    @Test
    void refundEscrowCreditsGuestWallet() throws Exception {
        try (Connection connection = migratedConnection()) {
            var context = seedBookingContext(connection, new BigDecimal("500.00"));
            TransactionService service = createService(connection);

            service.holdEscrow(context.bookingId);
            WalletTransaction refund = service.refundEscrow(context.bookingId,
                    context.bookingTotal);

            assertNotNull(refund);
            assertEquals(WalletTransactionType.ESCROW_REFUND, refund.type());
            assertEquals(0, context.bookingTotal.compareTo(refund.amount()));
            assertEquals(0, context.walletBalance.compareTo(refund.balanceAfter()));
        }
    }

    @Test
    void refundEscrowHandlesPartialRefund() throws Exception {
        try (Connection connection = migratedConnection()) {
            var context = seedBookingContext(connection, new BigDecimal("500.00"));
            TransactionService service = createService(connection);

            service.holdEscrow(context.bookingId);
            BigDecimal halfRefund = context.bookingTotal.divide(new BigDecimal("2"));
            WalletTransaction refund = service.refundEscrow(context.bookingId, halfRefund);

            assertEquals(0, halfRefund.compareTo(refund.amount()));
            BigDecimal expectedBalance = context.walletBalance
                    .subtract(context.bookingTotal).add(halfRefund);
            assertEquals(0, expectedBalance.compareTo(refund.balanceAfter()));
        }
    }

    @Test
    void historyForReturnsAllTransactionsForBooking() throws Exception {
        try (Connection connection = migratedConnection()) {
            var context = seedBookingContext(connection, new BigDecimal("500.00"));
            TransactionService service = createService(connection);

            service.holdEscrow(context.bookingId);
            service.refundEscrow(context.bookingId, context.bookingTotal);

            var history = service.historyFor(context.bookingId);

            assertEquals(2, history.size());
            assertEquals(WalletTransactionType.ESCROW_HOLD, history.get(0).type());
            assertEquals(WalletTransactionType.ESCROW_REFUND, history.get(1).type());
        }
    }

    // --- helpers ---

    private record BookingContext(UUID guestId, UUID bookingId, BigDecimal bookingTotal,
                                  BigDecimal walletBalance) {
    }

    private BookingContext seedBookingContext(Connection connection, BigDecimal walletBalance)
            throws Exception {
        MigrationRunner.migrate(connection);
        Instant now = Instant.now();

        User host = new User(UUID.randomUUID(), Role.HOST, "Host", "host@example.com",
                AccountStatus.ACTIVE, null, now);
        new JdbcUserRepository(connection).save(host);

        User guest = new User(UUID.randomUUID(), Role.GUEST, "Guest", "guest@example.com",
                AccountStatus.ACTIVE, null, now);
        new JdbcUserRepository(connection).save(guest);

        UUID walletId = UUID.randomUUID();
        new JdbcWalletRepository(connection).save(
                new Wallet(walletId, guest.userId(), walletBalance, "SGD", now));

        Property property = new Property(UUID.randomUUID(), host.userId(), ListingStatus.ACTIVE,
                "Test Property", "A nice place", PropertyType.APARTMENT,
                "123 Street", "Singapore", "Central", "123456",
                4, 2, 1.0, new BigDecimal("100.00"),
                LocalTime.of(14, 0), LocalTime.of(11, 0), Set.of(), now);
        new JdbcPropertyRepository(connection).save(property);

        BigDecimal totalAmount = new BigDecimal("300.00");
        Booking booking = new Booking(UUID.randomUUID(), property.propertyId(), guest.userId(),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(13),
                BookingStatus.PENDING, new BigDecimal("100.00"), totalAmount, now, null, null);
        new JdbcBookingRepository(connection).save(booking);

        return new BookingContext(guest.userId(), booking.bookingId(), totalAmount, walletBalance);
    }

    private TransactionService createService(Connection connection) {
        return new TransactionServiceImpl(connection,
                new JdbcBookingRepository(connection),
                new JdbcWalletRepository(connection),
                new JdbcWalletTransactionRepository(connection),
                null);
    }

    private static Connection migratedConnection() throws Exception {
        return ConnectionFactory.open("jdbc:sqlite::memory:");
    }
}
