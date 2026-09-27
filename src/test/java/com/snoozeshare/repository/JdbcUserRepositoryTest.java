package com.snoozeshare.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;

class JdbcUserRepositoryTest {

    private static Connection migrated() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }

    @Test
    void suspensionReasonPersistsAndClears() throws Exception {
        try (Connection connection = migrated()) {
            var users = new JdbcUserRepository(connection);
            UUID id = UUID.randomUUID();
            Instant created = Instant.parse("2026-03-05T08:00:00Z");
            users.save(new User(id, Role.GUEST, "Priya", "priya@test.com", AccountStatus.ACTIVE, null, created));
            assertNull(users.findById(id).orElseThrow().suspensionReason());

            users.save(new User(id, Role.GUEST, "Priya", "priya@test.com", AccountStatus.SUSPENDED, null, created,
                    "late cancellations"));
            User suspended = users.findById(id).orElseThrow();
            assertEquals(AccountStatus.SUSPENDED, suspended.accountStatus());
            assertEquals("late cancellations", suspended.suspensionReason());

            users.save(new User(id, Role.GUEST, "Priya", "priya@test.com", AccountStatus.ACTIVE, null, created,
                    null));
            assertNull(users.findById(id).orElseThrow().suspensionReason());
        }
    }

    @Test
    void findAllReturnsEveryUserOldestFirst() throws Exception {
        try (Connection connection = migrated()) {
            var users = new JdbcUserRepository(connection);
            User late = new User(UUID.randomUUID(), Role.HOST, "Late", "late@test.com", AccountStatus.ACTIVE,
                    "HOST2026", Instant.parse("2026-05-01T00:00:00Z"));
            User early = new User(UUID.randomUUID(), Role.GUEST, "Early", "early@test.com", AccountStatus.ACTIVE,
                    null, Instant.parse("2026-04-01T00:00:00Z"));
            users.save(late);
            users.save(early);

            List<String> names = users.findAll().stream().map(User::displayName).toList();

            // The migration seeds the System user (2026-01-01), which sorts first.
            assertEquals(List.of("SnoozeShare System", "Early", "Late"), names);
        }
    }
}
