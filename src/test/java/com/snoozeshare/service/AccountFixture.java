package com.snoozeshare.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcLedgerRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.AccountGovernanceServiceImpl;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.LedgerWriter;
import com.snoozeshare.testsupport.LedgerTestSupport;

/** An in-memory migrated database with one agent, guests, hosts, listings and bookings at a fixed "today". */
final class AccountFixture implements AutoCloseable {

    static final Instant NOW = Instant.parse("2026-09-26T04:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 9, 26);
    static final BigDecimal START_BALANCE = new BigDecimal("5000.00");

    final Connection connection;
    final JdbcUserRepository users;
    final JdbcPropertyRepository properties;
    final JdbcBookingRepository bookings;
    final JdbcAvailabilityBlockRepository blocks;
    final JdbcWalletRepository wallets;
    final JdbcLedgerRepository ledgerEntries;
    final InProcessEventBus bus = new InProcessEventBus();
    final AuditService audit;
    final LedgerWriter ledger;
    final User agent;

    AccountFixture() throws Exception {
        connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        users = new JdbcUserRepository(connection);
        properties = new JdbcPropertyRepository(connection);
        bookings = new JdbcBookingRepository(connection);
        blocks = new JdbcAvailabilityBlockRepository(connection);
        wallets = new JdbcWalletRepository(connection);
        ledgerEntries = new JdbcLedgerRepository(connection);
        audit = new AuditServiceImpl(new JdbcAuditLogRepository(connection), users, CLOCK);
        ledger = LedgerTestSupport.writer(connection, audit);
        agent = user(Role.AGENT, "Amy Tanaka", "amy@test.com", "2026-01-10T09:00:00Z");
    }

    AccountGovernanceServiceImpl service() {
        return service(audit);
    }

    /** The service's money rows go through {@code auditService} too, so an injected failure covers them. */
    AccountGovernanceServiceImpl service(AuditService auditService) {
        LedgerWriter writer = auditService == audit ? ledger : LedgerTestSupport.writer(connection, auditService);
        return new AccountGovernanceServiceImpl(connection, users, bookings, properties, blocks, wallets,
                writer, bus, auditService, CLOCK);
    }

    /** Creates a user; guests and hosts get a wallet funded with {@link #START_BALANCE} through the ledger. */
    User user(Role role, String name, String email, String createdAt) {
        User user = users.save(new User(UUID.randomUUID(), role, name, email, AccountStatus.ACTIVE,
                role == Role.GUEST ? null : "CODE", Instant.parse(createdAt)));
        if (role != Role.AGENT) {
            Wallet wallet = wallets.save(new Wallet(UUID.randomUUID(), user.userId(), BigDecimal.ZERO, "SGD", NOW));
            ledger.post(wallet.walletId(), WalletTransactionType.TOP_UP, START_BALANCE, user.userId(), null, null,
                    null, NOW.minusSeconds(172_800));
        }
        return user;
    }

    Property property(User host, ListingStatus status) {
        return properties.save(new Property(UUID.randomUUID(), host.userId(), status, "Loft", "Nice",
                PropertyType.APARTMENT, "1 Street", "Singapore", "Central", "123456", 2, 1, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0), Set.of(), NOW));
    }

    /**
     * Creates a booking with its escrow hold already taken from the guest's wallet (and, for PENDING and
     * CONFIRMED, its availability block), so refunds keep the ledger balanced.
     */
    Booking booking(User guest, Property property, BookingStatus status, LocalDate start, LocalDate end) {
        long nights = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        BigDecimal total = property.baseNightlyRate().multiply(BigDecimal.valueOf(nights));
        Booking booking = bookings.save(new Booking(UUID.randomUUID(), property.propertyId(), guest.userId(), start,
                end, status, property.baseNightlyRate(), total, NOW.minusSeconds(86_400), null, null));
        Wallet wallet = wallets.findByUserId(guest.userId()).orElseThrow();
        ledger.post(wallet.walletId(), WalletTransactionType.ESCROW_HOLD, total.negate(), guest.userId(),
                booking.bookingId(), null, null, NOW.minusSeconds(86_400));
        if (status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED) {
            blocks.save(new AvailabilityBlock(UUID.randomUUID(), property.propertyId(), start, end, "BOOKING",
                    booking.bookingId(), null));
        }
        return booking;
    }

    BigDecimal balance(User user) {
        return wallets.findByUserId(user.userId()).orElseThrow().balance();
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
