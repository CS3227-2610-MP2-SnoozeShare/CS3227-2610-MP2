package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class MessagingMigrationTest {

    @Test
    void freshDatabaseGetsTheMessagingTablesAndVersionFourOnce() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);

            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'messages'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'message_reads'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 4"));
        }
    }

    @Test
    void aDatabaseRebuiltFromTheReferenceSchemaIsAdoptedNotRecreated() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM schema_history WHERE version = 4");
            }
            MigrationRunner.migrate(connection);

            assertEquals(1, count(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 4"));
        }
    }

    @Test
    void freshDatabaseGetsTheBookingChatTablesAndVersionFiveOnce() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);

            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'booking_messages'"));
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM sqlite_master WHERE name = 'booking_message_reads'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 5"));
        }
    }

    @Test
    void messagesRejectAnUnknownChannel() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = OFF");
                boolean rejected = false;
                try {
                    statement.executeUpdate("INSERT INTO messages VALUES ('m','t','OTHER','u','AGENT','x','now')");
                } catch (java.sql.SQLException expected) {
                    rejected = true;
                }
                assertTrue(rejected);
            }
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
