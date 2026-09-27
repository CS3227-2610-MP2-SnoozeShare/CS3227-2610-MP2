package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
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
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;

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

    private static Booking booking(UUID propertyId, UUID guestId) {
        return new Booking(UUID.randomUUID(), propertyId, guestId,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
                BookingStatus.COMPLETED, new BigDecimal("100.00"), new BigDecimal("200.00"),
                Instant.now(), Instant.now(), Instant.now());
    }

    private static void insertReview(Connection connection, UUID bookingId, UUID guestId,
                                     int rating) throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO reviews (reviewId, bookingId, guestId, rating, comment, createdAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, bookingId.toString());
            statement.setString(3, guestId.toString());
            statement.setInt(4, rating);
            statement.setString(5, "Good stay");
            statement.setString(6, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private record TestContext(UUID propertyId, UUID guestId) {
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
                "000000", 2, 1, 1.0, new BigDecimal("100.00"),
                LocalTime.of(14, 0), LocalTime.of(11, 0), Set.of(), Instant.now());
        properties.save(property);
        return new TestContext(property.propertyId(), guest.userId());
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
