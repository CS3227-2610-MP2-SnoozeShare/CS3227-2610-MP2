package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.impl.AvailabilityServiceImpl;

class AvailabilityServiceTest {

    @Test
    void availableWhenNoBlocksOrBookings() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            AvailabilityService service = createService(connection);

            assertTrue(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    @Test
    void unavailableWhenHostBlockOverlaps() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var blocks = new JdbcAvailabilityBlockRepository(connection);
            blocks.save(new AvailabilityBlock(UUID.randomUUID(), ctx.propertyId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 7),
                    "HOST_BLOCK", null, null));
            AvailabilityService service = createService(connection);

            assertFalse(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    @Test
    void unavailableWhenConfirmedBookingOverlaps() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var bookings = new JdbcBookingRepository(connection);
            bookings.save(new Booking(UUID.randomUUID(), ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 7),
                    BookingStatus.CONFIRMED, new BigDecimal("100.00"),
                    new BigDecimal("400.00"), Instant.now(), null, null));
            AvailabilityService service = createService(connection);

            assertFalse(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    @Test
    void availableWhenOverlappingBookingIsCancelled() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var bookings = new JdbcBookingRepository(connection);
            bookings.save(new Booking(UUID.randomUUID(), ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 7),
                    BookingStatus.CANCELLED_BY_GUEST, new BigDecimal("100.00"),
                    new BigDecimal("400.00"), Instant.now(), null, null));
            AvailabilityService service = createService(connection);

            assertTrue(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    @Test
    void createHostBlockAllowsSingleDateAndRejectsNonOwner() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            AvailabilityService service = createService(connection);

            AvailabilityBlock singleDate = service.createHostBlock(
                    ctx.propertyId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5),
                    ctx.hostId, "maintenance");
            assertEquals(LocalDate.of(2026, 10, 5), singleDate.startDate());
            assertEquals(LocalDate.of(2026, 10, 6), singleDate.endDate());
            assertThrows(IllegalStateException.class, () -> service.createHostBlock(
                    ctx.propertyId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7),
                    ctx.guestId, "maintenance"));
        }
    }

    @Test
    void createHostBlockTreatsEndDateAsInclusive() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            AvailabilityService service = createService(connection);

            service.createHostBlock(ctx.propertyId, LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 5), ctx.hostId, "maintenance");

            assertFalse(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6)));
            assertTrue(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)));
        }
    }

    @Test
    void createHostBlockRejectsActiveBookingAndExistingBlockOverlap() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var bookings = new JdbcBookingRepository(connection);
            bookings.save(new Booking(UUID.randomUUID(), ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 7),
                    BookingStatus.PENDING, new BigDecimal("100.00"),
                    new BigDecimal("400.00"), Instant.now(), null, null));
            AvailabilityService service = createService(connection);

            assertThrows(IllegalStateException.class, () -> service.createHostBlock(
                    ctx.propertyId, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8),
                    ctx.hostId, "maintenance"));

            new JdbcAvailabilityBlockRepository(connection).save(new AvailabilityBlock(
                    UUID.randomUUID(), ctx.propertyId, LocalDate.of(2026, 11, 3),
                    LocalDate.of(2026, 11, 7), "HOST_BLOCK", null, null));
            assertThrows(IllegalStateException.class, () -> service.createHostBlock(
                    ctx.propertyId, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 8),
                    ctx.hostId, "maintenance"));
        }
    }

    @Test
    void createHostBlockTrimsReasonAndAllowsNextDayBoundary() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            AvailabilityService service = createService(connection);
            service.createHostBlock(ctx.propertyId, LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 5), ctx.hostId, "  maintenance  ");

            AvailabilityBlock adjacent = service.createHostBlock(ctx.propertyId,
                    LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7), ctx.hostId, " ");

            assertEquals("maintenance", service.blocksFor(ctx.propertyId).get(0).reason());
            assertEquals(LocalDate.of(2026, 10, 8), adjacent.endDate());
            assertEquals(null, adjacent.reason());
        }
    }

    @Test
    void removeHostBlockRequiresOwnerAndManualSource() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            AvailabilityService service = createService(connection);
            AvailabilityBlock manual = service.createHostBlock(ctx.propertyId,
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), ctx.hostId, null);

            assertThrows(IllegalStateException.class, () -> service.removeHostBlock(
                    manual.blockId(), ctx.guestId));
            assertTrue(new JdbcAvailabilityBlockRepository(connection)
                    .findById(manual.blockId()).isPresent());

            AvailabilityBlock booking = new AvailabilityBlock(UUID.randomUUID(), ctx.propertyId,
                    LocalDate.of(2026, 12, 4), LocalDate.of(2026, 12, 6),
                    "BOOKING", null, null);
            new JdbcAvailabilityBlockRepository(connection).save(booking);
            assertThrows(IllegalStateException.class, () -> service.removeHostBlock(
                    booking.blockId(), ctx.hostId));
            service.removeHostBlock(manual.blockId(), ctx.hostId);
            assertTrue(new JdbcAvailabilityBlockRepository(connection)
                    .findById(manual.blockId()).isEmpty());
        }
    }

    @Test
    void removeHostBlockRejectsUnknownBlock() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            AvailabilityService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () -> service.removeHostBlock(
                    UUID.randomUUID(), ctx.hostId));
        }
    }

    private record TestContext(UUID propertyId, UUID hostId, UUID guestId) {
    }

    private static TestContext seedContext(Connection connection) {
        var users = new JdbcUserRepository(connection);
        var properties = new JdbcPropertyRepository(connection);
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", Instant.now());
        users.save(host);
        User guest = new User(UUID.randomUUID(), Role.GUEST, "Guest",
                "guest-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, null, Instant.now());
        users.save(guest);
        Property property = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Test", "desc", PropertyType.APARTMENT,
                "street", "city", "region", "000000", 2, 1, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(), Instant.now());
        properties.save(property);
        return new TestContext(property.propertyId(), host.userId(), guest.userId());
    }

    private static AvailabilityService createService(Connection connection) {
        return new AvailabilityServiceImpl(new JdbcPropertyRepository(connection),
                new JdbcAvailabilityBlockRepository(connection),
                new JdbcBookingRepository(connection));
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
