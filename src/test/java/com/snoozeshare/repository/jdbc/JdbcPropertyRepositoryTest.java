package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.service.SearchCriteria;
import com.snoozeshare.domain.enums.AccountStatus;

class JdbcPropertyRepositoryTest {

    @Test
    void saveAndFindByIdRoundTripsAllListingFields() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            Property property = new Property(UUID.randomUUID(), host.userId(), ListingStatus.ACTIVE,
                    "Full Listing", "Detailed description", PropertyType.CONDO,
                    "45 Orchard Road", "Singapore", "Central", "238879", 5, 3, 2.5,
                    new BigDecimal("245.50"), LocalTime.of(15, 30), LocalTime.of(10, 30),
                    Set.of(AmenityType.PARKING, AmenityType.WASHER), Instant.now());
            repo.save(property);

            Property found = repo.findById(property.propertyId()).orElseThrow();

            assertEquals(property.propertyId(), found.propertyId());
            assertEquals(property.hostId(), found.hostId());
            assertEquals(property.status(), found.status());
            assertEquals(property.title(), found.title());
            assertEquals(property.description(), found.description());
            assertEquals(property.propertyType(), found.propertyType());
            assertEquals(property.streetAddress(), found.streetAddress());
            assertEquals(property.city(), found.city());
            assertEquals(property.region(), found.region());
            assertEquals(property.postalCode(), found.postalCode());
            assertEquals(property.maxGuests(), found.maxGuests());
            assertEquals(property.bedrooms(), found.bedrooms());
            assertEquals(property.bathrooms(), found.bathrooms());
            assertEquals(0, property.baseNightlyRate().compareTo(found.baseNightlyRate()));
            assertEquals(property.checkInTime(), found.checkInTime());
            assertEquals(property.checkOutTime(), found.checkOutTime());
            assertEquals(property.amenities(), found.amenities());
        }
    }

    @Test
    void saveStatusUpdatePersistsInactiveWithoutChangingListingFields() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            Property active = makeProperty(host.userId(), "Status Listing", "Singapore",
                    ListingStatus.ACTIVE, 2);
            repo.save(active);

            repo.save(new Property(active.propertyId(), active.hostId(), ListingStatus.INACTIVE,
                    active.title(), active.description(), active.propertyType(),
                    active.streetAddress(), active.city(), active.region(), active.postalCode(),
                    active.maxGuests(), active.bedrooms(), active.bathrooms(),
                    active.baseNightlyRate(), active.checkInTime(), active.checkOutTime(),
                    active.amenities(), active.createdAt()));

            Property found = repo.findById(active.propertyId()).orElseThrow();

            assertEquals(ListingStatus.INACTIVE, found.status());
            assertEquals(active.hostId(), found.hostId());
            assertEquals(active.title(), found.title());
            assertEquals(0, active.baseNightlyRate().compareTo(found.baseNightlyRate()));
        }
    }

    @Test
    void saveAndFindById() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            Property property = makeProperty(host.userId(), "Beach House", "Singapore",
                    ListingStatus.ACTIVE, 4);
            repo.save(property);

            var found = repo.findById(property.propertyId());

            assertTrue(found.isPresent());
            assertEquals("Beach House", found.get().title());
            assertEquals("Singapore", found.get().city());
            assertEquals(Set.of(AmenityType.WIFI, AmenityType.KITCHEN), found.get().amenities());
        }
    }

    @Test
    void findBySearchCriteriaFiltersByCitySubstringCaseInsensitive() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            repo.save(makeProperty(host.userId(), "Singapore Apt", "Singapore",
                    ListingStatus.ACTIVE, 2));
            repo.save(makeProperty(host.userId(), "KL Condo", "Kuala Lumpur",
                    ListingStatus.ACTIVE, 2));

            var results = repo.findBySearchCriteria(
                    new SearchCriteria("singapore", null, null, null));

            assertEquals(1, results.size());
            assertEquals("Singapore Apt", results.get(0).title());
        }
    }

    @Test
    void findBySearchCriteriaFiltersByGuestCapacity() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            repo.save(makeProperty(host.userId(), "Small Room", "Singapore",
                    ListingStatus.ACTIVE, 1));
            repo.save(makeProperty(host.userId(), "Big House", "Singapore",
                    ListingStatus.ACTIVE, 6));

            var results = repo.findBySearchCriteria(
                    new SearchCriteria(null, 4, null, null));

            assertEquals(1, results.size());
            assertEquals("Big House", results.get(0).title());
        }
    }

    @Test
    void findBySearchCriteriaReturnsOnlyActiveListings() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            repo.save(makeProperty(host.userId(), "Active", "Singapore",
                    ListingStatus.ACTIVE, 2));
            repo.save(makeProperty(host.userId(), "Inactive", "Singapore",
                    ListingStatus.INACTIVE, 2));

            var results = repo.findBySearchCriteria(
                    new SearchCriteria(null, null, null, null));

            assertEquals(1, results.size());
            assertEquals("Active", results.get(0).title());
        }
    }

    @Test
    void findBySearchCriteriaWithNoCriteriaReturnsAllActive() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            repo.save(makeProperty(host.userId(), "A", "Singapore",
                    ListingStatus.ACTIVE, 2));
            repo.save(makeProperty(host.userId(), "B", "Tokyo",
                    ListingStatus.ACTIVE, 4));

            var results = repo.findBySearchCriteria(
                    new SearchCriteria(null, null, null, null));

            assertEquals(2, results.size());
        }
    }

    @Test
    void unknownAmenityInDatabaseIsIgnoredGracefully() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            Property property = makeProperty(host.userId(), "Test", "Singapore",
                    ListingStatus.ACTIVE, 2);
            repo.save(property);

            // Manually insert an unknown amenity value into the DB
            try (var stmt = connection.prepareStatement(
                    "UPDATE properties SET amenities = 'WIFI,UNKNOWN_AMENITY,KITCHEN' WHERE propertyId = ?")) {
                stmt.setString(1, property.propertyId().toString());
                stmt.executeUpdate();
            }

            var found = repo.findById(property.propertyId());

            assertTrue(found.isPresent());
            assertEquals(Set.of(AmenityType.WIFI, AmenityType.KITCHEN), found.get().amenities());
        }
    }

    private static User saveHost(JdbcUserRepository users) {
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", Instant.now());
        return users.save(host);
    }

    private static Property makeProperty(UUID hostId, String title, String city,
                                          ListingStatus status, int maxGuests) {
        return new Property(UUID.randomUUID(), hostId, status, title,
                "A nice place", PropertyType.APARTMENT, "123 Street", city,
                "Central", "123456", maxGuests, 2, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(AmenityType.WIFI, AmenityType.KITCHEN), Instant.now());
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
