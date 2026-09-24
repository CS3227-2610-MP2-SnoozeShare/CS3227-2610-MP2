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
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;

class JdbcAvailabilityBlockRepositoryTest {

    @Test
    void saveAndFindByPropertyId() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var properties = new JdbcPropertyRepository(connection);
            var repo = new JdbcAvailabilityBlockRepository(connection);
            UUID propertyId = seedProperty(users, properties);

            AvailabilityBlock block = new AvailabilityBlock(UUID.randomUUID(), propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    "HOST_BLOCK", null);
            repo.save(block);

            var found = repo.findByPropertyId(propertyId);
            assertEquals(1, found.size());
            assertEquals(block.blockId(), found.get(0).blockId());
        }
    }

    @Test
    void findOverlappingDetectsPartialOverlap() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var properties = new JdbcPropertyRepository(connection);
            var repo = new JdbcAvailabilityBlockRepository(connection);
            UUID propertyId = seedProperty(users, properties);
            repo.save(new AvailabilityBlock(UUID.randomUUID(), propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    "HOST_BLOCK", null));

            var overlapping = repo.findOverlapping(propertyId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 8));

            assertEquals(1, overlapping.size());
        }
    }

    @Test
    void findOverlappingReturnsEmptyWhenNoOverlap() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var properties = new JdbcPropertyRepository(connection);
            var repo = new JdbcAvailabilityBlockRepository(connection);
            UUID propertyId = seedProperty(users, properties);
            repo.save(new AvailabilityBlock(UUID.randomUUID(), propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    "HOST_BLOCK", null));

            var overlapping = repo.findOverlapping(propertyId,
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8));

            assertTrue(overlapping.isEmpty());
        }
    }

    private static UUID seedProperty(JdbcUserRepository users,
                                      JdbcPropertyRepository properties) {
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", Instant.now());
        users.save(host);
        Property property = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Test", "desc", PropertyType.APARTMENT,
                "street", "city", "region", "000000", 2, 1, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(), Instant.now());
        properties.save(property);
        return property.propertyId();
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
