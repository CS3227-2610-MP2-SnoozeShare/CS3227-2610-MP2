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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.AvailabilityServiceImpl;
import com.snoozeshare.service.impl.ListingServiceImpl;
import com.snoozeshare.service.impl.UserServiceImpl;

class ListingServiceTest {

    @Test
    void validHostCanCreateActiveListing() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);

            Property draft = validDraft();
            Property created = service.create(draft, ctx.hostId());

            assertTrue(created.propertyId() != null);
            assertEquals(ctx.hostId(), created.hostId());
            assertEquals(ListingStatus.ACTIVE, created.status());
            assertTrue(new JdbcPropertyRepository(connection)
                    .findById(created.propertyId()).isPresent());
            assertEquals(1, new JdbcAuditLogRepository(connection)
                    .query(ctx.hostId(), null, "LISTING_CREATED").size());
        }
    }

    @ParameterizedTest
    @MethodSource("invalidListingDrafts")
    void invalidListingDraftIsRejectedBeforePersistence(Property draft) throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () -> service.create(draft, ctx.hostId()));
        }
    }

    @Test
    void guestCannotCreateListing() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);

            assertThrows(IllegalStateException.class, () -> service.create(validDraft(), ctx.guestId()));
        }
    }

    @Test
    void suspendedHostCannotCreateListing() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            var users = new JdbcUserRepository(connection);
            User host = users.findById(ctx.hostId()).orElseThrow();
            users.save(new User(host.userId(), host.role(), host.displayName(), host.email(),
                    AccountStatus.SUSPENDED, host.registrationCode(), host.createdAt()));
            ListingService service = createService(connection);

            assertThrows(IllegalStateException.class, () -> service.create(validDraft(), ctx.hostId()));
        }
    }

    @Test
    void createRejectsAnExistingPropertyId() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);
            Property draft = new Property(ctx.singaporeId(), null, ListingStatus.INACTIVE,
                    validDraft().title(), validDraft().description(), validDraft().propertyType(),
                    validDraft().streetAddress(), validDraft().city(), validDraft().region(),
                    validDraft().postalCode(), validDraft().maxGuests(), validDraft().bedrooms(),
                    validDraft().bathrooms(), validDraft().baseNightlyRate(),
                    validDraft().checkInTime(), validDraft().checkOutTime(), Set.of(),
                    Instant.now());

            assertThrows(IllegalArgumentException.class, () -> service.create(draft, ctx.hostId()));
        }
    }

    @Test
    void owningHostCanUpdateListingDetails() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);
            Property existing = new JdbcPropertyRepository(connection)
                    .findById(ctx.singaporeId()).orElseThrow();
            Property edited = new Property(existing.propertyId(), null, existing.status(),
                    "Updated title", "Updated description", existing.propertyType(),
                    existing.streetAddress(), existing.city(), existing.region(),
                    existing.postalCode(), 6, existing.bedrooms(), existing.bathrooms(),
                    new BigDecimal("199.00"), existing.checkInTime(), existing.checkOutTime(),
                    existing.amenities(), existing.createdAt());

            Property saved = service.update(edited, ctx.hostId());

            assertEquals("Updated title", saved.title());
            assertEquals(6, saved.maxGuests());
            assertEquals(0, new BigDecimal("199.00").compareTo(saved.baseNightlyRate()));
            assertEquals("Updated description",
                    new JdbcPropertyRepository(connection).findById(ctx.singaporeId())
                            .orElseThrow().description());
        }
    }

    @Test
    void anotherHostCannotUpdateListingDetails() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            var users = new JdbcUserRepository(connection);
            User otherHost = new User(UUID.randomUUID(), Role.HOST, "Other Host",
                    "edit-host-" + UUID.randomUUID() + "@test.com", AccountStatus.ACTIVE,
                    "HOST2026", Instant.now());
            users.save(otherHost);
            ListingService service = createService(connection);
            Property existing = new JdbcPropertyRepository(connection)
                    .findById(ctx.singaporeId()).orElseThrow();

            assertThrows(IllegalStateException.class, () ->
                    service.update(existing, otherHost.userId()));
        }
    }

    @Test
    void owningHostCanToggleListingStatusAndAuditTheChange() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);

            Property deactivated = service.updateStatus(ctx.singaporeId(), ListingStatus.INACTIVE,
                    ctx.hostId());

            assertEquals(ListingStatus.INACTIVE, deactivated.status());
            assertEquals(ListingStatus.INACTIVE,
                    new JdbcPropertyRepository(connection).findById(ctx.singaporeId())
                            .orElseThrow().status());
            var audit = new JdbcAuditLogRepository(connection)
                    .query(ctx.hostId(), null, "LISTING_STATUS_CHANGED");
            assertEquals(1, audit.size());
            assertTrue(audit.get(0).beforeState().contains("ACTIVE"));
            assertTrue(audit.get(0).afterState().contains("INACTIVE"));

            Property reactivated = service.updateStatus(ctx.singaporeId(), ListingStatus.ACTIVE,
                    ctx.hostId());
            assertEquals(ListingStatus.ACTIVE, reactivated.status());
        }
    }

    @Test
    void repeatingListingStatusIsIdempotentWithoutAnotherAuditEntry() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);

            service.updateStatus(ctx.singaporeId(), ListingStatus.ACTIVE, ctx.hostId());

            assertEquals(0, new JdbcAuditLogRepository(connection)
                    .query(ctx.hostId(), null, "LISTING_STATUS_CHANGED").size());
        }
    }

    @Test
    void anotherHostCannotUpdateListingStatus() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            var users = new JdbcUserRepository(connection);
            User otherHost = new User(UUID.randomUUID(), Role.HOST, "Other Host",
                    "other-host-" + UUID.randomUUID() + "@test.com", AccountStatus.ACTIVE,
                    "HOST2026", Instant.now());
            users.save(otherHost);
            ListingService service = createService(connection);

            assertThrows(IllegalStateException.class, () -> service.updateStatus(
                    ctx.singaporeId(), ListingStatus.INACTIVE, otherHost.userId()));
        }
    }

    @Test
    void missingListingCannotUpdateStatus() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () -> service.updateStatus(
                    UUID.randomUUID(), ListingStatus.INACTIVE, ctx.hostId()));
        }
    }

    @Test
    void hostQueryReturnsOnlyOwnedListings() throws Exception {
        try (Connection connection = migratedConnection()) {
            TestContext ctx = seedContext(connection);
            ListingService service = createService(connection);

            assertEquals(2, service.findByHostId(ctx.hostId()).size());
            assertTrue(service.findByHostId(ctx.guestId()).isEmpty());
        }
    }

    private static Stream<Arguments> invalidListingDrafts() {
        Property valid = validDraft();
        return Stream.of(
                Arguments.of(with(valid, null, valid.description(), valid.streetAddress(),
                        valid.city(), valid.region(), valid.postalCode(), valid.maxGuests(),
                        valid.bedrooms(), valid.bathrooms(), valid.baseNightlyRate(),
                        valid.propertyType(), valid.checkInTime(), valid.checkOutTime())),
                Arguments.of(with(valid, valid.title(), valid.description(), valid.streetAddress(),
                        valid.city(), valid.region(), valid.postalCode(), 0, valid.bedrooms(),
                        valid.bathrooms(), valid.baseNightlyRate(), valid.propertyType(),
                        valid.checkInTime(), valid.checkOutTime())),
                Arguments.of(with(valid, valid.title(), valid.description(), valid.streetAddress(),
                        valid.city(), valid.region(), valid.postalCode(), valid.maxGuests(),
                        valid.bedrooms(), valid.bathrooms(), new BigDecimal("-1.00"),
                        valid.propertyType(), valid.checkInTime(), valid.checkOutTime())),
                Arguments.of(with(valid, valid.title(), valid.description(), valid.streetAddress(),
                        valid.city(), valid.region(), valid.postalCode(), valid.maxGuests(),
                        valid.bedrooms(), valid.bathrooms(), valid.baseNightlyRate(), null,
                        valid.checkInTime(), valid.checkOutTime())));
    }

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

            assertThrows(IllegalArgumentException.class, () -> service.getDetail(UUID.randomUUID()));
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

            assertThrows(IllegalArgumentException.class, () -> service.estimateCost(
                    ctx.singaporeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)));
            assertThrows(IllegalArgumentException.class, () -> service.estimateCost(
                    ctx.singaporeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3)));
        }
    }

    private record TestContext(UUID singaporeId, UUID tokyoId, UUID guestId, UUID hostId) {
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
        return new TestContext(singapore.propertyId(), tokyo.propertyId(), guest.userId(), host.userId());
    }

    private static Property validDraft() {
        return new Property(null, null, ListingStatus.INACTIVE, "New Listing", "A new place",
                PropertyType.CONDO, "1 Main Street", "Singapore", "Central", "123456", 3,
                2, 1.5, new BigDecimal("175.00"), LocalTime.of(15, 0), LocalTime.of(11, 0),
                Set.of(), Instant.now());
    }

    private static Property with(Property source, String title, String description,
                                 String streetAddress, String city, String region,
                                 String postalCode, int maxGuests, int bedrooms,
                                 double bathrooms, BigDecimal rate, PropertyType propertyType,
                                 LocalTime checkIn, LocalTime checkOut) {
        return new Property(source.propertyId(), source.hostId(), source.status(), title,
                description, propertyType, streetAddress, city, region, postalCode, maxGuests,
                bedrooms, bathrooms, rate, checkIn, checkOut, source.amenities(),
                source.createdAt());
    }

    private static ListingService createService(Connection connection) {
        var properties = new JdbcPropertyRepository(connection);
        var blocks = new JdbcAvailabilityBlockRepository(connection);
        var bookings = new JdbcBookingRepository(connection);
        var availability = new AvailabilityServiceImpl(blocks, bookings);
        var users = new JdbcUserRepository(connection);
        var userService = new UserServiceImpl(connection, users,
                new JdbcWalletRepository(connection));
        var auditService = new AuditServiceImpl(new JdbcAuditLogRepository(connection));
        return new ListingServiceImpl(properties, availability, userService, auditService);
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
