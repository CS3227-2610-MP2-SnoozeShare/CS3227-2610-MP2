package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
            AvailabilityService service = new AvailabilityServiceImpl(
                    new JdbcAvailabilityBlockRepository(connection),
                    new JdbcBookingRepository(connection));

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
                    "HOST_BLOCK", null));
            AvailabilityService service = new AvailabilityServiceImpl(blocks,
                    new JdbcBookingRepository(connection));

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
            AvailabilityService service = new AvailabilityServiceImpl(
                    new JdbcAvailabilityBlockRepository(connection), bookings);

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
            AvailabilityService service = new AvailabilityServiceImpl(
                    new JdbcAvailabilityBlockRepository(connection), bookings);

            assertTrue(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    private record TestContext(UUID propertyId, UUID guestId) {
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
        return new TestContext(property.propertyId(), guest.userId());
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
