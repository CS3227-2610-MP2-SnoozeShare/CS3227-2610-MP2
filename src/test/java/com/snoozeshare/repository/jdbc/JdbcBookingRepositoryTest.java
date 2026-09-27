package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;

class JdbcBookingRepositoryTest {

    @Test
    void findOverlappingReturnsOnlyPendingAndConfirmedBookings() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var repo = new JdbcBookingRepository(connection);
            LocalDate oct1 = LocalDate.of(2026, 10, 1);
            LocalDate oct5 = LocalDate.of(2026, 10, 5);

            repo.save(makeBooking(ctx.propertyId, ctx.guestId, oct1, oct5,
                    BookingStatus.CONFIRMED));
            repo.save(makeBooking(ctx.propertyId, ctx.guestId, oct1, oct5,
                    BookingStatus.CANCELLED_BY_GUEST));

            var overlapping = repo.findOverlapping(ctx.propertyId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 8));

            assertEquals(1, overlapping.size());
            assertEquals(BookingStatus.CONFIRMED, overlapping.get(0).status());
        }
    }

    @Test
    void findOverlappingReturnsEmptyWhenNoOverlap() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var repo = new JdbcBookingRepository(connection);
            repo.save(makeBooking(ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    BookingStatus.CONFIRMED));

            var overlapping = repo.findOverlapping(ctx.propertyId,
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8));

            assertTrue(overlapping.isEmpty());
        }
    }

    @Test
    void findByHostScopesHistoryAndPendingRequestsOldestFirst() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var other = seedContext(connection);
            var repo = new JdbcBookingRepository(connection);
            var first = makeBooking(ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), BookingStatus.PENDING);
            var second = makeBooking(ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), BookingStatus.PENDING);
            var history = makeBooking(ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), BookingStatus.CONFIRMED);
            repo.save(first);
            repo.save(second);
            repo.save(history);
            repo.save(makeBooking(other.propertyId, other.guestId,
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), BookingStatus.PENDING));

            assertEquals(3, repo.findByHost(ctx.hostId).size());
            assertEquals(first.bookingId(), repo.findByHostPending(ctx.hostId).get(0).bookingId());
            assertEquals(second.bookingId(), repo.findByHostPending(ctx.hostId).get(1).bookingId());
        }
    }

    private static Booking makeBooking(UUID propertyId, UUID guestId,
                                        LocalDate start, LocalDate end,
                                        BookingStatus status) {
        return new Booking(UUID.randomUUID(), propertyId, guestId, start, end,
                status, new BigDecimal("100.00"), new BigDecimal("400.00"),
                Instant.now(), null, null);
    }

    private record TestContext(UUID hostId, UUID propertyId, UUID guestId) {
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
        return new TestContext(host.userId(), property.propertyId(), guest.userId());
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
