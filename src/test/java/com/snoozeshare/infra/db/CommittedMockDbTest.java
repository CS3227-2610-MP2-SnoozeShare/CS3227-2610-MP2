package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
    void theCommittedFileHasTheAuditColumnsTheSystemUserAndMigrationVersionEight() throws Exception {
        try (Connection connection = openCommittedReadOnly();
             Statement statement = connection.createStatement()) {
            Set<String> columns = new HashSet<>();
            try (ResultSet result = statement.executeQuery("PRAGMA table_info(audit_log)")) {
                while (result.next()) {
                    columns.add(result.getString("name"));
                }
            }
            for (String column : new String[] {"actorName", "walletAdjustment", "reason", "subjectUserId",
                "subjectName", "bookingId", "ticketId", "balanceAfter"}) {
                assertTrue(columns.contains(column), "audit_log is missing " + column);
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users WHERE userId = "
                    + "'a0000000-0000-0000-0000-0000000000ff' AND displayName = 'SnoozeShare System' "
                    + "AND role = 'SYSTEM' AND accountStatus = 'ACTIVE'")) {
                result.next();
                assertEquals(1, result.getInt(1), "System user");
            }
            for (int version : new int[] {1, 2, 3, 4, 5, 6, 7, 8}) {
                try (ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*) FROM schema_history WHERE version = " + version)) {
                    result.next();
                    assertEquals(1, result.getInt(1), "schema_history version " + version);
                }
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE name = 'wallet_transactions'")) {
                result.next();
                assertEquals(0, result.getInt(1), "the old ledger table is gone (V008)");
            }
            try (ResultSet result = statement.executeQuery("SELECT balance FROM wallets "
                    + "WHERE userId = 'a0000000-0000-0000-0000-0000000000ff'")) {
                assertTrue(result.next(), "System wallet");
                assertEquals(0, new BigDecimal("16.35").compareTo(new BigDecimal(result.getString(1))),
                        "the System wallet holds the two seeded platform fees");
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users "
                    + "WHERE suspensionReason IS NOT NULL AND accountStatus = 'SUSPENDED'")) {
                result.next();
                assertEquals(2, result.getInt(1), "the two seeded suspended accounts carry a reason");
            }
        }
    }
}
