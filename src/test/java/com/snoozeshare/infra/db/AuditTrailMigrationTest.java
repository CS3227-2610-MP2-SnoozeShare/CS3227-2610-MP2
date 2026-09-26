package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.impl.UserServiceImpl;

class AuditTrailMigrationTest {

    @Test
    void v002AddsTheTypedColumnsAndTheSystemUserAndIsIdempotent() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);

            Set<String> columns = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA table_info(audit_log)")) {
                while (result.next()) {
                    columns.add(result.getString("name"));
                }
            }
            for (String column : new String[] {"actorName", "walletAdjustment", "reason", "subjectUserId",
                "subjectName", "bookingId", "ticketId"}) {
                assertTrue(columns.contains(column), column);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM schema_history WHERE version = 2")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void theSystemUserExistsButCanNeverSignIn() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            var users = new JdbcUserRepository(connection);

            var system = users.findById(AuditService.SYSTEM_ACTOR_ID).orElseThrow();

            assertEquals("SnoozeShare System", system.displayName());
            assertEquals(Role.AGENT, system.role());
            assertEquals(AccountStatus.SUSPENDED, system.accountStatus());
            assertThrows(IllegalStateException.class,
                    () -> new UserServiceImpl(connection, users,
                            new com.snoozeshare.repository.jdbc.JdbcWalletRepository(connection))
                            .authenticate(system.email()));
        }
    }
}
