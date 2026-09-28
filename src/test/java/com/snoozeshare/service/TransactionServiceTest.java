package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcLedgerRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.LedgerWriter;
import com.snoozeshare.service.impl.TransactionServiceImpl;
import com.snoozeshare.testsupport.FailingAuditService;
import com.snoozeshare.testsupport.LedgerTestSupport;

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

            assertThrows(IllegalArgumentException.class, () -> service.holdEscrow(context.bookingId));
        }
    }

    @Test
    void holdEscrowFailsOnMissingBooking() throws Exception {
        try (Connection connection = migratedConnection()) {
            MigrationRunner.migrate(connection);
            TransactionService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () -> service.holdEscrow(UUID.randomUUID()));
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

    @Test
    void settleBookingCompletionPaysHostNetAndCreditsTheFeeToTheSystemWallet() throws Exception {
        try (Connection connection = migratedConnection()) {
            var context = seedBookingContext(connection, new BigDecimal("500.00"));
            confirm(connection, context.bookingId);
            TransactionService service = createService(connection);

            WalletTransaction payout = service.settleBookingCompletion(context.bookingId);

            assertEquals(WalletTransactionType.BOOKING_PAYOUT, payout.type());
            var system = new JdbcWalletRepository(connection).findByUserId(AuditService.SYSTEM_ACTOR_ID)
                    .orElseThrow();
            var feeRows = new JdbcLedgerRepository(connection).entriesForWallet(system.walletId());
            assertEquals(1, feeRows.size());
            assertEquals(WalletTransactionType.PLATFORM_FEE, feeRows.get(0).type());
            assertEquals(0, new BigDecimal("9.00").compareTo(feeRows.get(0).amount()));
            assertEquals(0, new BigDecimal("291.00").compareTo(payout.amount()));
            assertEquals(0, new BigDecimal("291.00").compareTo(hostBalance(connection, context)));
            assertEquals(BookingStatus.COMPLETED, new JdbcBookingRepository(connection)
                    .findById(context.bookingId).orElseThrow().status());
        }
    }

    @Test
    void settleBookingCompletionRollsBackWhenTheFeeRowFails() throws Exception {
        try (Connection connection = migratedConnection()) {
            var context = seedBookingContext(connection, new BigDecimal("500.00"));
            confirm(connection, context.bookingId);
            LedgerWriter failing = LedgerTestSupport.writer(connection,
                    new FailingAuditService(LedgerTestSupport.audit(connection), 2));
            TransactionService service = createService(connection, failing);

            assertThrows(RuntimeException.class, () -> service.settleBookingCompletion(context.bookingId));

            assertEquals(BookingStatus.CONFIRMED, new JdbcBookingRepository(connection)
                    .findById(context.bookingId).orElseThrow().status());
            assertEquals(0, BigDecimal.ZERO.compareTo(hostBalance(connection, context)));
            assertTrue(new JdbcLedgerRepository(connection).entriesForBooking(context.bookingId).stream()
                    .noneMatch(txn -> txn.type() == WalletTransactionType.BOOKING_PAYOUT));
        }
    }

    // --- helpers ---

    private static void confirm(Connection connection, UUID bookingId) {
        var bookings = new JdbcBookingRepository(connection);
        Booking booking = bookings.findById(bookingId).orElseThrow();
        bookings.save(new Booking(booking.bookingId(), booking.listingId(), booking.guestId(),
                booking.startDate(), booking.endDate(), BookingStatus.CONFIRMED,
                booking.nightlyRateSnapshot(), booking.totalAmount(), booking.createdAt(), Instant.now(), null));
    }

    private static BigDecimal hostBalance(Connection connection, BookingContext context) {
        return new JdbcWalletRepository(connection).findByUserId(context.hostId).orElseThrow().balance();
    }

    private record BookingContext(UUID guestId, UUID hostId, UUID bookingId, BigDecimal bookingTotal,
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
        new JdbcWalletRepository(connection).save(
                new Wallet(UUID.randomUUID(), host.userId(), BigDecimal.ZERO, "SGD", now));

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

        return new BookingContext(guest.userId(), host.userId(), booking.bookingId(), totalAmount,
                walletBalance);
    }

    private TransactionService createService(Connection connection) {
        return createService(connection, LedgerTestSupport.writer(connection));
    }

    private TransactionService createService(Connection connection, LedgerWriter ledger) {
        return new TransactionServiceImpl(connection,
                new JdbcBookingRepository(connection),
                new JdbcPropertyRepository(connection),
                new JdbcWalletRepository(connection),
                ledger, new JdbcLedgerRepository(connection), null);
    }

    private static Connection migratedConnection() throws Exception {
        return ConnectionFactory.open("jdbc:sqlite::memory:");
    }
}
