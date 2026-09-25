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
import com.snoozeshare.service.impl.ListingServiceImpl;

class ListingServiceTest {

    @Test
    void searchWithNoCriteriaReturnsAllActive() throws Exception {
        try (Connection connection = migratedConnection()) {
            seedContext(connection);
            ListingService service = createService(connection);

            var results = service.search(new SearchCriteria(null, null, null, null));

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(SearchResult::available));
        }
    }

    @Test
    void searchByCityFiltersResults() throws Exception {
        try (Connection connection = migratedConnection()) {
            seedContext(connection);
            ListingService service = createService(connection);

            var results = service.search(
                    new SearchCriteria("singapore", null, null, null));

            assertEquals(1, results.size());
            assertEquals("Singapore Apt", results.get(0).property().title());
        }
    }

    @Test
    void searchWithDatesShowsAvailableBeforeUnavailable() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var bookings = new JdbcBookingRepository(connection);
            bookings.save(new Booking(UUID.randomUUID(), ctx.singaporeId, ctx.guestId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    BookingStatus.CONFIRMED, new BigDecimal("150.00"),
                    new BigDecimal("600.00"), Instant.now(), null, null));
            ListingService service = createService(connection);

            var results = service.search(new SearchCriteria(null, null,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));

            assertEquals(2, results.size());
            assertTrue(results.get(0).available());
            assertEquals("Tokyo House", results.get(0).property().title());
            assertFalse(results.get(1).available());
            assertEquals("Singapore Apt", results.get(1).property().title());
        }
    }

    @Test
    void getDetailReturnsProperty() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            ListingService service = createService(connection);

            Property detail = service.getDetail(ctx.singaporeId);

            assertEquals("Singapore Apt", detail.title());
        }
    }

    @Test
    void getDetailThrowsWhenNotFound() throws Exception {
        try (Connection connection = migratedConnection()) {
            ListingService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () ->
                    service.getDetail(UUID.randomUUID()));
        }
    }

    @Test
    void estimateCostComputesCorrectly() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            ListingService service = createService(connection);

            PriceBreakdown breakdown = service.estimateCost(ctx.singaporeId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4));

            assertEquals(0, new BigDecimal("150.00").compareTo(breakdown.nightlyRate()));
            assertEquals(3, breakdown.nights());
            assertEquals(0, new BigDecimal("450.00").compareTo(breakdown.totalAmount()));
        }
    }

    @Test
    void estimateCostThrowsWhenStartNotBeforeEnd() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            ListingService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () ->
                    service.estimateCost(ctx.singaporeId,
                            LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)));
            assertThrows(IllegalArgumentException.class, () ->
                    service.estimateCost(ctx.singaporeId,
                            LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3)));
        }
    }

    private record TestContext(UUID singaporeId, UUID tokyoId, UUID guestId) {
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
        Property singapore = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Singapore Apt", "Nice", PropertyType.APARTMENT,
                "street", "Singapore", "Central", "123456", 2, 1, 1.0,
                new BigDecimal("150.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(), Instant.now());
        properties.save(singapore);
        Property tokyo = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Tokyo House", "Great", PropertyType.HOUSE,
                "street", "Tokyo", "Shibuya", "100000", 4, 2, 1.5,
                new BigDecimal("200.00"), LocalTime.of(15, 0), LocalTime.of(10, 0),
                Set.of(), Instant.now());
        properties.save(tokyo);
        return new TestContext(singapore.propertyId(), tokyo.propertyId(), guest.userId());
    }

    private static ListingService createService(Connection connection) {
        var properties = new JdbcPropertyRepository(connection);
        var blocks = new JdbcAvailabilityBlockRepository(connection);
        var bookings = new JdbcBookingRepository(connection);
        var availability = new AvailabilityServiceImpl(blocks, bookings);
        return new ListingServiceImpl(properties, availability);
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
