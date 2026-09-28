package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class MigrationForeignKeyTest {

    private static int foreignKeys(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            result.next();
            return result.getInt(1);
        }
    }

    @Test
    void foreignKeyEnforcementIsRestoredAfterMigrating() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            assertEquals(1, foreignKeys(connection), "the connection factory turns enforcement on");
            MigrationRunner.migrate(connection);
            assertEquals(1, foreignKeys(connection));
            assertThrows(SQLException.class, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO wallets (walletId, userId, balance, currency, updatedAt) "
                            + "VALUES ('w', 'no-such-user', 0, 'SGD', '2026-01-01T00:00:00Z')");
                }
            });
        }
    }
}
