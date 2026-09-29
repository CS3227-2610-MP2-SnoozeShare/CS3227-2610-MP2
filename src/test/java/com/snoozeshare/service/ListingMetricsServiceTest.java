package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
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
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;

class ListingMetricsServiceTest {

    @Test
    void metricsCountBookingsAndAverageReviewRatings() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext context = seedContext(connection);
            JdbcBookingRepository bookings = new JdbcBookingRepository(connection);
            Booking first = booking(context.propertyId(), context.guestId());
            Booking second = booking(context.propertyId(), context.guestId());
            bookings.save(first);
            bookings.save(second);
            insertReview(connection, first.bookingId(), context.guestId(), 4);
            insertReview(connection, second.bookingId(), context.guestId(), 5);

            Object service = metricsService(connection);
            Object metrics = service.getClass().getMethod("metricsFor", UUID.class)
                    .invoke(service, context.propertyId());

            assertEquals(2, metricValue(metrics, "bookingCount"));
            assertEquals(4.5, (double) metricValue(metrics, "averageRating"), 0.001);
        }
    }

    @Test
    void metricsUseZeroValuesWhenListingHasNoBookingsOrReviews() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext context = seedContext(connection);
            Object service = metricsService(connection);
            Object metrics = service.getClass().getMethod("metricsFor", UUID.class)
                    .invoke(service, context.propertyId());

            assertEquals(0, metricValue(metrics, "bookingCount"));
            assertEquals(0.0, (double) metricValue(metrics, "averageRating"), 0.001);
            assertEquals(0, ((List<?>) metricValue(metrics, "reviews")).size());
            assertEquals(0.0, (double) metricValue(metrics, "occupancyPercentage"), 0.001);
            assertEquals(BigDecimal.ZERO, metricValue(metrics, "earnings"));
        }
    }

    @Test
    void metricsProjectReviewsOccupancyAndPayoutEarningsForTrailingThirtyDays() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext context = seedContext(connection);
            JdbcBookingRepository bookings = new JdbcBookingRepository(connection);
            Booking overlap = booking(context.propertyId(), context.guestId(),
                    LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 3));
            Booking inside = booking(context.propertyId(), context.guestId(),
                    LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20));
            bookings.save(overlap);
            bookings.save(inside);
            insertReview(connection, overlap.bookingId(), context.guestId(), 5, "Lovely stay", "Guest");
            insertPayout(connection, context.guestId(), overlap.bookingId(),
                    new BigDecimal("120.00"), Instant.parse("2026-10-20T10:00:00Z"));
            insertPayout(connection, context.guestId(), inside.bookingId(),
                    new BigDecimal("75.00"), Instant.parse("2026-09-01T10:00:00Z"));

            Object metrics = metricsService(connection,
                    Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC))
                    .getClass().getMethod("metricsFor", UUID.class)
                    .invoke(metricsService(connection,
                            Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC)),
                            context.propertyId());

            assertEquals(2, metricValue(metrics, "bookingCount"));
            assertEquals(40.0, (double) metricValue(metrics, "occupancyPercentage"), 0.001);
            assertEquals(0, ((BigDecimal) metricValue(metrics, "earnings"))
                    .compareTo(new BigDecimal("120.00")));
            Object review = ((List<?>) metricValue(metrics, "reviews")).get(0);
            assertEquals("Guest", review.getClass().getMethod("guestName").invoke(review));
            assertEquals("Lovely stay", review.getClass().getMethod("comment").invoke(review));
        }
    }

    @Test
    void metricsCapOverlappingOccupancyAtOneHundredPercent() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext context = seedContext(connection);
            JdbcBookingRepository bookings = new JdbcBookingRepository(connection);
            bookings.save(booking(context.propertyId(), context.guestId(),
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 15)));
            bookings.save(booking(context.propertyId(), context.guestId(),
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 15)));

            Object service = metricsService(connection,
                    Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC));
            Object metrics = service.getClass().getMethod("metricsFor", UUID.class)
                    .invoke(service, context.propertyId());

            assertEquals(100.0, (double) metricValue(metrics, "occupancyPercentage"), 0.001);
        }
    }

    private static Object metricValue(Object metrics, String methodName) throws Exception {
        Method method = metrics.getClass().getMethod(methodName);
        return method.invoke(metrics);
    }

    private static Object metricsService(Connection connection) throws Exception {
        Class<?> implementation = Class.forName(
                "com.snoozeshare.service.impl.ListingMetricsServiceImpl");
        return implementation.getConstructor(Connection.class).newInstance(connection);
    }

    private static Object metricsService(Connection connection, Clock clock) throws Exception {
        Class<?> implementation = Class.forName(
                "com.snoozeshare.service.impl.ListingMetricsServiceImpl");
        return implementation.getConstructor(Connection.class, Clock.class)
                .newInstance(connection, clock);
    }

    private static Booking booking(UUID propertyId, UUID guestId) {
        return booking(propertyId, guestId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
    }

    private static Booking booking(UUID propertyId, UUID guestId, LocalDate start, LocalDate end) {
        return new Booking(UUID.randomUUID(), propertyId, guestId, start, end,
                BookingStatus.COMPLETED, new BigDecimal("100.00"), new BigDecimal("200.00"),
                Instant.now(), Instant.now(), Instant.now());
    }

    private static void insertReview(Connection connection, UUID bookingId, UUID guestId,
                                     int rating) throws Exception {
        insertReview(connection, bookingId, guestId, rating, "Good stay", "Guest");
    }

    private static void insertReview(Connection connection, UUID bookingId, UUID guestId,
                                     int rating, String comment, String ignoredGuestName) throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO reviews (reviewId, bookingId, guestId, rating, comment, createdAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, bookingId.toString());
            statement.setString(3, guestId.toString());
            statement.setInt(4, rating);
            statement.setString(5, comment);
            statement.setString(6, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private static void insertPayout(Connection connection, UUID actorUserId, UUID bookingId,
                                     BigDecimal amount, Instant createdAt) throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO audit_log (logId, actorUserId, actionType, entityType, entityId, "
                        + "timestamp, walletAdjustment, bookingId, balanceAfter) "
                        + "VALUES (?, ?, 'BOOKING_PAYOUT', 'WalletTransaction', ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, actorUserId.toString());
            statement.setString(3, UUID.randomUUID().toString());
            statement.setString(4, createdAt.toString());
            statement.setBigDecimal(5, amount);
            statement.setString(6, bookingId.toString());
            statement.setBigDecimal(7, amount);
            statement.executeUpdate();
        }
    }

    private record TestContext(UUID propertyId, UUID guestId, UUID walletId) {
    }

    private static TestContext seedContext(Connection connection) {
        var users = new JdbcUserRepository(connection);
        var properties = new JdbcPropertyRepository(connection);
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com", AccountStatus.ACTIVE,
                "HOST2026", Instant.now());
        users.save(host);
        User guest = new User(UUID.randomUUID(), Role.GUEST, "Guest",
                "guest-" + UUID.randomUUID() + "@test.com", AccountStatus.ACTIVE,
                null, Instant.now());
        users.save(guest);
        Property property = new Property(UUID.randomUUID(), host.userId(), ListingStatus.ACTIVE,
                "Test", "desc", PropertyType.APARTMENT, "street", "city", "region",
                0, 2, 1, 1, new BigDecimal("100.00"),
                LocalTime.of(14, 0), LocalTime.of(11, 0), Set.of(), Instant.now());
        properties.save(property);
        UUID walletId = UUID.randomUUID();
        new JdbcWalletRepository(connection).save(new Wallet(walletId, host.userId(),
                new BigDecimal("0.00"), "SGD", Instant.now()));
        return new TestContext(property.propertyId(), guest.userId(), walletId);
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
