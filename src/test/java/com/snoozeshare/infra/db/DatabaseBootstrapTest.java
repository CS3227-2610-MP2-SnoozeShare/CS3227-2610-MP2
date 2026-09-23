package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class DatabaseBootstrapTest {

    @Test
    void freshDatabaseRunsMigrationsAndEnablesForeignKeys() throws SQLException {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);

            assertTrue(foreignKeysAreEnabled(connection));
            assertEquals(Set.of(
                    "users", "properties", "availability_blocks", "bookings", "wallets",
                    "wallet_transactions", "ticket_categories", "tickets", "reviews",
                    "audit_log"), tableNames(connection));
        }
    }

    @Test
    void migrationRunnerIsSafeToCallMoreThanOnce() throws SQLException {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);

            assertEquals(1, migrationCount(connection));
        }
    }

    @Test
    void transactionRollsBackAllWorkWhenTheCallbackFails() throws SQLException {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            TransactionManager manager = new TransactionManager(connection);

            assertThrows(IllegalStateException.class, () -> manager.inTransaction(current -> {
                try (Statement statement = current.createStatement()) {
                    statement.executeUpdate("INSERT INTO users "
                            + "(userId, role, displayName, email, accountStatus, createdAt) "
                            + "VALUES ('u1', 'GUEST', 'Guest', 'guest@example.com', "
                            + "'ACTIVE', '2026-09-23T00:00:00Z')");
                }
                throw new IllegalStateException("force rollback");
            }));

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    void transactionCommitsSuccessfulWork() throws SQLException {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            TransactionManager manager = new TransactionManager(connection);

            manager.inTransaction(current -> {
                try (Statement statement = current.createStatement()) {
                    statement.executeUpdate("INSERT INTO users "
                            + "(userId, role, displayName, email, accountStatus, createdAt) "
                            + "VALUES ('u2', 'HOST', 'Host', 'host@example.com', "
                            + "'ACTIVE', '2026-09-23T00:00:00Z')");
                }
                return null;
            });

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }
    }

    private static boolean foreignKeysAreEnabled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            result.next();
            return result.getInt(1) == 1;
        }
    }

    private static Set<String> tableNames(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table' "
                             + "AND name <> 'schema_history'")) {
            Set<String> names = new java.util.HashSet<>();
            while (result.next()) {
                names.add(result.getString(1));
            }
            return names;
        }
    }

    private static int migrationCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM schema_history")) {
            result.next();
            return result.getInt(1);
        }
    }
}
