package com.snoozeshare.infra.db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class MigrationRunner {

    private static final int FOUNDATION_VERSION = 1;
    private static final int AUDIT_TRAIL_VERSION = 2;
    private static final int SUSPENSION_REASON_VERSION = 3;
    private static final int MESSAGING_VERSION = 4;
    private static final int BOOKING_MESSAGING_VERSION = 5;
    private static final int SYSTEM_ROLE_VERSION = 6;
    private static final int UNIFIED_LEDGER_VERSION = 7;

    private MigrationRunner() {
    }

    public static void migrate(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        // The pragma is a no-op inside a transaction, so it must be switched before setAutoCommit(false).
        boolean restoreForeignKeys = originalAutoCommit && foreignKeysEnabled(connection);
        if (restoreForeignKeys) {
            setForeignKeys(connection, false);
        }
        connection.setAutoCommit(false);
        try {
            createHistoryTable(connection);
            if (!migrationApplied(connection, FOUNDATION_VERSION)) {
                if (!tableExists(connection, "users")) {
                    applyFoundationMigration(connection);
                }
                // else: a pre-provisioned reference database (db/snoozeshare-mock.db) already has the schema.
                recordMigration(connection, FOUNDATION_VERSION);
            }
            if (!migrationApplied(connection, AUDIT_TRAIL_VERSION)
                    && !columnExists(connection, "audit_log", "walletAdjustment")) {
                applySqlMigration(connection, "/db/migration/V002__audit_trail.sql");
            }
            if (!columnExists(connection, "bookings", "hostDecisionMessage")) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE bookings ADD COLUMN hostDecisionMessage TEXT");
                }
            }
            if (!migrationApplied(connection, AUDIT_TRAIL_VERSION)) {
                recordMigration(connection, AUDIT_TRAIL_VERSION);
            }
            if (!migrationApplied(connection, SUSPENSION_REASON_VERSION)) {
                if (!columnExists(connection, "users", "suspensionReason")) {
                    applySqlMigration(connection, "/db/migration/V003__suspension_reason.sql");
                }
                // else: a reference database rebuilt from db/schema.sql already has the column.
                recordMigration(connection, SUSPENSION_REASON_VERSION);
            }
            if (!migrationApplied(connection, MESSAGING_VERSION)) {
                if (!tableExists(connection, "messages")) {
                    applySqlMigration(connection, "/db/migration/V004__messaging.sql");
                }
                // else: a reference database rebuilt from db/schema.sql already has the tables.
                recordMigration(connection, MESSAGING_VERSION);
            }
            if (!migrationApplied(connection, BOOKING_MESSAGING_VERSION)) {
                if (!tableExists(connection, "booking_messages")) {
                    applySqlMigration(connection, "/db/migration/V005__booking_messaging.sql");
                }
                // else: a reference database rebuilt from db/schema.sql already has the tables.
                recordMigration(connection, BOOKING_MESSAGING_VERSION);
            }
            boolean rebuilt = false;
            if (!migrationApplied(connection, SYSTEM_ROLE_VERSION)) {
                if (!usersAllowSystemRole(connection)) {
                    applySqlMigration(connection, "/db/migration/V006__system_role.sql");
                    rebuilt = true;
                }
                // else: a reference database rebuilt from db/schema.sql already allows SYSTEM.
                recordMigration(connection, SYSTEM_ROLE_VERSION);
            }
            if (rebuilt) {
                requireForeignKeysIntact(connection);
            }
            if (!migrationApplied(connection, UNIFIED_LEDGER_VERSION)) {
                if (!columnExists(connection, "audit_log", "balanceAfter")) {
                    applySqlMigration(connection, "/db/migration/V007__unified_ledger.sql");
                }
                // else: a reference database rebuilt from db/schema.sql already has the column.
                recordMigration(connection, UNIFIED_LEDGER_VERSION);
            }
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
            if (restoreForeignKeys) {
                setForeignKeys(connection, true);
            }
        }
    }

    private static boolean foreignKeysEnabled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA foreign_keys")) {
            return result.next() && result.getInt(1) == 1;
        }
    }

    private static void setForeignKeys(Connection connection, boolean on) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = " + (on ? "ON" : "OFF"));
        }
    }

    /** Fails the migration if any row now points at a missing parent. */
    private static void requireForeignKeysIntact(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new SQLException("Foreign key violation after migration in table "
                        + result.getString("table") + " (row " + result.getLong("rowid") + ")");
            }
        }
    }

    private static boolean usersAllowSystemRole(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'users'");
             var result = statement.executeQuery()) {
            return result.next() && result.getString(1).contains("'SYSTEM'");
        }
    }

    private static void createHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_history ("
                    + "version INTEGER PRIMARY KEY, appliedAt TEXT NOT NULL)");
        }
    }

    private static boolean migrationApplied(Connection connection, int version)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM schema_history WHERE version = ?")) {
            statement.setInt(1, version);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void applyFoundationMigration(Connection connection) throws SQLException {
        applySqlMigration(connection, "/db/migration/V001__foundation.sql");
    }

    private static void applySqlMigration(Connection connection, String resource) throws SQLException {
        String sql;
        try (InputStream input = MigrationRunner.class.getResourceAsStream(
                resource)) {
            if (input == null) {
                throw new SQLException("Migration resource is missing: " + resource);
            }
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SQLException("Unable to read foundation migration", exception);
        }

        for (String statementSql : sql.split(";")) {
            if (!statementSql.isBlank()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(statementSql);
                }
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equals(result.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void recordMigration(Connection connection, int version) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO schema_history (version, appliedAt) VALUES (?, datetime('now'))")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }
}
