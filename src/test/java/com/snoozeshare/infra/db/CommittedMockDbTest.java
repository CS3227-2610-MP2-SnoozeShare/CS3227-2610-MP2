package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;

/**
 * Opens the committed db/snoozeshare-mock.db directly, read-only and without MigrationRunner, so a stale
 * file cannot be hidden by a migration applied to a copy.
 */
class CommittedMockDbTest {

    private static Connection openCommittedReadOnly() throws Exception {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        Path file = Path.of("db/snoozeshare-mock.db").toAbsolutePath();
        return config.createConnection("jdbc:sqlite:" + file);
    }

    @Test
    void theCommittedFileHasTheAuditColumnsTheSystemUserAndMigrationVersionTwo() throws Exception {
        try (Connection connection = openCommittedReadOnly();
             Statement statement = connection.createStatement()) {
            Set<String> columns = new HashSet<>();
            try (ResultSet result = statement.executeQuery("PRAGMA table_info(audit_log)")) {
                while (result.next()) {
                    columns.add(result.getString("name"));
                }
            }
            for (String column : new String[] {"actorName", "walletAdjustment", "reason", "subjectUserId",
                "subjectName", "bookingId", "ticketId"}) {
                assertTrue(columns.contains(column), "audit_log is missing " + column);
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users WHERE userId = "
                    + "'a0000000-0000-0000-0000-0000000000ff' AND displayName = 'SnoozeShare System' "
                    + "AND accountStatus = 'SUSPENDED'")) {
                result.next();
                assertEquals(1, result.getInt(1), "System user");
            }
            for (int version : new int[] {1, 2}) {
                try (ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*) FROM schema_history WHERE version = " + version)) {
                    result.next();
                    assertEquals(1, result.getInt(1), "schema_history version " + version);
                }
            }
        }
    }
}
