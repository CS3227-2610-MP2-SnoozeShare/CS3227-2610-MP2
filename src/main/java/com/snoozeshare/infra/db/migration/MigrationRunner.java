package com.snoozeshare.infra.db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class MigrationRunner {

    private static final int FOUNDATION_VERSION = 1;
    private static final int BOOKING_DECISION_MESSAGE_VERSION = 2;

    private MigrationRunner() {
    }

    public static void migrate(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
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
            if (!migrationApplied(connection, BOOKING_DECISION_MESSAGE_VERSION)) {
                applySqlMigration(connection, "/db/migration/V002__booking_decision_message.sql");
                recordMigration(connection, BOOKING_DECISION_MESSAGE_VERSION);
            }
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
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

    private static void recordMigration(Connection connection, int version) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO schema_history (version, appliedAt) VALUES (?, datetime('now'))")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }
}
