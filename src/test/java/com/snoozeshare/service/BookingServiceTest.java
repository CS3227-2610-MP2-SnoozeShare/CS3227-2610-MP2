package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.AuditCriteria;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.BookingServiceImpl;

class BookingServiceTest {

    // --- submitRequest tests ---

    @Test
    void submitRequestCreatesPendingBooking() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);

            assertNotNull(booking);
            assertEquals(BookingStatus.PENDING, booking.status());
            assertEquals(ctx.guestId, booking.guestId());
            assertEquals(ctx.propertyId, booking.listingId());
            assertEquals(start, booking.startDate());
            assertEquals(end, booking.endDate());
            assertEquals(0, new BigDecimal("100.00").compareTo(booking.nightlyRateSnapshot()));
            assertEquals(0, new BigDecimal("300.00").compareTo(booking.totalAmount()));
        }
    }

    @Test
    void submitRequestCreatesAvailabilityBlock() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);

            var blocks = new JdbcAvailabilityBlockRepository(connection);
            var overlapping = blocks.findOverlapping(ctx.propertyId, start, end);
            assertEquals(1, overlapping.size());
            assertEquals("BOOKING", overlapping.get(0).source());
            assertEquals(booking.bookingId(), overlapping.get(0).bookingId());
        }
    }

    @Test
    void submitRequestDeductsEscrowFromWallet() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);

            var walletRepo = new JdbcWalletRepository(connection);
            Wallet wallet = walletRepo.findByUserId(ctx.guestId).orElseThrow();
            assertEquals(0, new BigDecimal("200.00").compareTo(wallet.balance()));

            var txnRepo = new JdbcWalletTransactionRepository(connection);
            var transactions = txnRepo.findByBookingId(booking.bookingId());
            assertEquals(1, transactions.size());
            assertEquals(WalletTransactionType.ESCROW_HOLD, transactions.get(0).type());
            assertEquals(0, new BigDecimal("-300.00").compareTo(transactions.get(0).amount()));
        }
    }

    @Test
    void submitRequestPublishesWalletTransactionEventAfterCommit() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            var eventBus = new InProcessEventBus();
            List<WalletTransactionRecordedEvent> events = new ArrayList<>();
            eventBus.subscribe(WalletTransactionRecordedEvent.class, events::add);
            BookingService service = createService(connection, eventBus);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            assertEquals(1, events.size());
            var transaction = new JdbcWalletTransactionRepository(connection)
                    .findByBookingId(booking.bookingId()).get(0);
            assertEquals(transaction.transactionId(), events.get(0).transactionId());
            assertEquals(transaction.walletId(), events.get(0).walletId());
        }
    }

    @Test
    void submitRequestRollsBackOnInsufficientFunds() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("100.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Executable action = () -> service.submitRequest(ctx.guestId, ctx.propertyId, start, end);
            assertThrows(IllegalArgumentException.class, action);

            var bookingRepo = new JdbcBookingRepository(connection);
            assertTrue(bookingRepo.findByGuest(ctx.guestId).isEmpty());

            var blockRepo = new JdbcAvailabilityBlockRepository(connection);
            assertTrue(blockRepo.findByPropertyId(ctx.propertyId).isEmpty());

            var walletRepo = new JdbcWalletRepository(connection);
            assertEquals(0, new BigDecimal("100.00").compareTo(
                    walletRepo.findByUserId(ctx.guestId).orElseThrow().balance()));
        }
    }

    @Test
    void submitRequestFailsOnOverlappingDates() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("1000.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            service.submitRequest(ctx.guestId, ctx.propertyId, start, end);

            assertThrows(IllegalArgumentException.class, () -> service.submitRequest(
                    ctx.guestId, ctx.propertyId, start.plusDays(1), end.plusDays(1)));
        }
    }

    @Test
    void submitRequestFailsOnInvalidDates() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate date = LocalDate.now().plusDays(10);

            Executable action = () -> service.submitRequest(ctx.guestId, ctx.propertyId, date, date);
            assertThrows(IllegalArgumentException.class, action);
        }
    }

    @Test
    void submitRequestFailsOnMissingProperty() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () -> service.submitRequest(
                    ctx.guestId, UUID.randomUUID(), LocalDate.now().plusDays(1),
                    LocalDate.now().plusDays(3)));
        }
    }

    // --- cancel tests ---

    @Test
    void cancelPendingBookingRefunds100Percent() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);
            Booking cancelled = service.cancel(booking.bookingId(), ctx.guestId);

            assertEquals(BookingStatus.CANCELLED_BY_GUEST, cancelled.status());
            assertNotNull(cancelled.decidedAt());

            var walletRepo = new JdbcWalletRepository(connection);
            assertEquals(0, new BigDecimal("500.00").compareTo(
                    walletRepo.findByUserId(ctx.guestId).orElseThrow().balance()));
        }
    }

    @Test
    void cancelConfirmedBookingMoreThan48hRefunds100Percent() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);
            // Manually confirm the booking for this test
            new JdbcBookingRepository(connection).save(new Booking(
                    booking.bookingId(), booking.listingId(), booking.guestId(),
                    booking.startDate(), booking.endDate(), BookingStatus.CONFIRMED,
                    booking.nightlyRateSnapshot(), booking.totalAmount(),
                    booking.createdAt(), Instant.now(), null));

            Booking cancelled = service.cancel(booking.bookingId(), ctx.guestId);

            assertEquals(BookingStatus.CANCELLED_BY_GUEST, cancelled.status());
            var walletRepo = new JdbcWalletRepository(connection);
            assertEquals(0, new BigDecimal("500.00").compareTo(
                    walletRepo.findByUserId(ctx.guestId).orElseThrow().balance()));
        }
    }

    @Test
    void cancelConfirmedBookingWithin48hRefunds50Percent() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end = LocalDate.now().plusDays(4);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);
            new JdbcBookingRepository(connection).save(new Booking(
                    booking.bookingId(), booking.listingId(), booking.guestId(),
                    booking.startDate(), booking.endDate(), BookingStatus.CONFIRMED,
                    booking.nightlyRateSnapshot(), booking.totalAmount(),
                    booking.createdAt(), Instant.now(), null));

            Booking cancelled = service.cancel(booking.bookingId(), ctx.guestId);

            assertEquals(BookingStatus.CANCELLED_BY_GUEST, cancelled.status());
            var walletRepo = new JdbcWalletRepository(connection);
            // 500 - 300 (escrow) + 150 (50% refund) = 350
            assertEquals(0, new BigDecimal("350.00").compareTo(
                    walletRepo.findByUserId(ctx.guestId).orElseThrow().balance()));
        }
    }

    @Test
    void cancelFailsForCompletedBooking() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);
            new JdbcBookingRepository(connection).save(new Booking(
                    booking.bookingId(), booking.listingId(), booking.guestId(),
                    booking.startDate(), booking.endDate(), BookingStatus.COMPLETED,
                    booking.nightlyRateSnapshot(), booking.totalAmount(),
                    booking.createdAt(), Instant.now(), Instant.now()));

            Executable action = () -> service.cancel(booking.bookingId(), ctx.guestId);
            assertThrows(IllegalStateException.class, action);
        }
    }

    @Test
    void cancelFailsForWrongGuest() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);

            Executable action = () -> service.cancel(booking.bookingId(), UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, action);
        }
    }

    @Test
    void cancelRemovesAvailabilityBlock() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            LocalDate start = LocalDate.now().plusDays(10);
            LocalDate end = LocalDate.now().plusDays(13);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId, start, end);
            service.cancel(booking.bookingId(), ctx.guestId);

            var blockRepo = new JdbcAvailabilityBlockRepository(connection);
            assertTrue(blockRepo.findByPropertyId(ctx.propertyId).isEmpty());
        }
    }

    // --- decide tests ---

    @Test
    void decideApproveTransitionsToConfirmed() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            Booking confirmed = service.decide(booking.bookingId(), true, ctx.hostId);

            assertEquals(BookingStatus.CONFIRMED, confirmed.status());
            assertNotNull(confirmed.decidedAt());
        }
    }

    @Test
    void decideRejectTransitionsToRejectedWithFullRefund() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            Booking rejected = service.decide(booking.bookingId(), false, ctx.hostId);

            assertEquals(BookingStatus.REJECTED, rejected.status());
            // Full refund per decision C8
            var walletRepo = new JdbcWalletRepository(connection);
            assertEquals(0, new BigDecimal("500.00").compareTo(
                    walletRepo.findByUserId(ctx.guestId).orElseThrow().balance()));
            // Availability block removed
            var blockRepo = new JdbcAvailabilityBlockRepository(connection);
            assertTrue(blockRepo.findByPropertyId(ctx.propertyId).isEmpty());
        }
    }

    @Test
    void decideFailsForWrongHost() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            Executable action = () -> service.decide(booking.bookingId(), true, UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, action);
        }
    }

    @Test
    void decideFailsForNonPendingBooking() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            service.decide(booking.bookingId(), true, ctx.hostId);

            Executable action = () -> service.decide(booking.bookingId(), true, ctx.hostId);
            assertThrows(IllegalStateException.class, action);
        }
    }

    // --- query tests ---

    @Test
    void tripsForReturnsGuestBookings() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("1000.00"));
            BookingService service = createService(connection);
            service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));
            service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(20), LocalDate.now().plusDays(23));

            var trips = service.tripsFor(ctx.guestId, null);

            assertEquals(2, trips.size());
        }
    }

    @Test
    void tripsForFiltersbyStatus() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("1000.00"));
            BookingService service = createService(connection);
            Booking b1 = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));
            service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(20), LocalDate.now().plusDays(23));
            service.decide(b1.bookingId(), true, ctx.hostId);

            var confirmed = service.tripsFor(ctx.guestId,
                    new TripFilter(BookingStatus.CONFIRMED));

            assertEquals(1, confirmed.size());
            assertEquals(BookingStatus.CONFIRMED, confirmed.get(0).status());
        }
    }

    @Test
    void pendingRequestsForReturnsHostPendingBookings() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            var pending = service.pendingRequestsFor(ctx.hostId);

            assertEquals(1, pending.size());
            assertEquals(BookingStatus.PENDING, pending.get(0).status());
        }
    }

    // --- audit tests ---

    private static List<AuditLogEntry> auditRows(Connection connection) {
        return new JdbcAuditLogRepository(connection).search(AuditCriteria.all(), 100, 0);
    }

    private static AuditLogEntry row(List<AuditLogEntry> rows, String action) {
        return rows.stream().filter(r -> r.actionType().equals(action)).findFirst().orElseThrow();
    }

    @Test
    void submitRequestAuditsTheRequestAndTheEscrowHoldAsSeparateRows() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            List<AuditLogEntry> rows = auditRows(connection);
            assertEquals(Set.of("BOOKING_REQUESTED", "ESCROW_HOLD"),
                    Set.copyOf(rows.stream().map(AuditLogEntry::actionType).toList()));
            assertEquals(2, rows.size());
            AuditLogEntry requested = row(rows, "BOOKING_REQUESTED");
            assertEquals("Booking", requested.entityType());
            assertEquals(booking.bookingId(), requested.entityId());
            assertNull(requested.beforeState());
            assertEquals("PENDING", requested.afterState());
            assertNull(requested.walletAdjustment());
            assertEquals(booking.bookingId(), requested.bookingId());
            assertEquals(ctx.guestId, requested.actorUserId());
            assertEquals(ctx.guestId, requested.subjectUserId());
            AuditLogEntry hold = row(rows, "ESCROW_HOLD");
            assertEquals("WalletTransaction", hold.entityType());
            assertEquals(0, new BigDecimal("-300.00").compareTo(hold.walletAdjustment()));
            assertNull(hold.afterState());
            assertEquals(booking.bookingId(), hold.bookingId());
            assertEquals(ctx.guestId, hold.subjectUserId());
        }
    }

    @Test
    void hostConfirmAuditsOneStatusRowByTheHost() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            service.decide(booking.bookingId(), true, ctx.hostId);

            AuditLogEntry confirmed = row(auditRows(connection), "BOOKING_CONFIRMED");
            assertEquals("PENDING", confirmed.beforeState());
            assertEquals("CONFIRMED", confirmed.afterState());
            assertEquals(ctx.hostId, confirmed.actorUserId());
            assertEquals(ctx.guestId, confirmed.subjectUserId());
            assertEquals(booking.bookingId(), confirmed.bookingId());
        }
    }

    @Test
    void hostRejectAuditsTheStatusChangeAndTheFullRefundSeparately() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            service.decide(booking.bookingId(), false, ctx.hostId);

            List<AuditLogEntry> rows = auditRows(connection);
            AuditLogEntry rejected = row(rows, "BOOKING_REJECTED");
            assertEquals("PENDING", rejected.beforeState());
            assertEquals("REJECTED", rejected.afterState());
            assertNull(rejected.walletAdjustment());
            AuditLogEntry refund = row(rows, "ESCROW_REFUND");
            assertEquals(0, new BigDecimal("300.00").compareTo(refund.walletAdjustment()));
            assertEquals(ctx.hostId, refund.actorUserId());
            assertEquals(ctx.guestId, refund.subjectUserId());
        }
    }

    @Test
    void guestCancelAuditsTheStatusChangeAndTheRefundSeparately() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            service.cancel(booking.bookingId(), ctx.guestId);

            List<AuditLogEntry> rows = auditRows(connection);
            AuditLogEntry cancelled = row(rows, "BOOKING_CANCELLED_BY_GUEST");
            assertEquals("PENDING", cancelled.beforeState());
            assertEquals("CANCELLED_BY_GUEST", cancelled.afterState());
            assertNull(cancelled.walletAdjustment());
            assertEquals("Full refund", cancelled.reason());
            assertEquals(0, new BigDecimal("300.00").compareTo(row(rows, "ESCROW_REFUND").walletAdjustment()));
        }
    }

    @Test
    void aFailedSubmitLeavesNoAuditRow() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("10.00"));
            BookingService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () -> service.submitRequest(ctx.guestId,
                    ctx.propertyId, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13)));

            assertEquals(0, auditRows(connection).size());
        }
    }

    // --- helpers ---

    private record TestContext(UUID guestId, UUID hostId, UUID propertyId) {
    }

    static TestContext seedContext(Connection connection, BigDecimal walletBalance) {
        var users = new JdbcUserRepository(connection);
        var properties = new JdbcPropertyRepository(connection);
        var walletRepo = new JdbcWalletRepository(connection);
        Instant now = Instant.now();

        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", now);
        users.save(host);

        User guest = new User(UUID.randomUUID(), Role.GUEST, "Guest",
                "guest-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, null, now);
        users.save(guest);

        walletRepo.save(new Wallet(UUID.randomUUID(), guest.userId(), walletBalance, "SGD", now));

        Property property = new Property(UUID.randomUUID(), host.userId(), ListingStatus.ACTIVE,
                "Test Property", "A nice place", PropertyType.APARTMENT,
                "123 Street", "Singapore", "Central", "123456",
                4, 2, 1.0, new BigDecimal("100.00"),
                LocalTime.of(14, 0), LocalTime.of(11, 0), Set.of(), now);
        properties.save(property);

        return new TestContext(guest.userId(), host.userId(), property.propertyId());
    }

    static BookingService createService(Connection connection) {
        return createService(connection, null);
    }

    static BookingService createService(Connection connection, InProcessEventBus eventBus) {
        return new BookingServiceImpl(connection,
                new JdbcBookingRepository(connection),
                new JdbcPropertyRepository(connection),
                new JdbcAvailabilityBlockRepository(connection),
                new JdbcWalletRepository(connection),
                new JdbcWalletTransactionRepository(connection),
                eventBus,
                new AuditServiceImpl(new JdbcAuditLogRepository(connection),
                        new JdbcUserRepository(connection), Clock.systemUTC()));
    }

    static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
