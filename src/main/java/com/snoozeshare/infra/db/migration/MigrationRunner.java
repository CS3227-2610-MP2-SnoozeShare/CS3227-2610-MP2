package com.snoozeshare.infra.db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class MigrationRunner {

    private static final int FOUNDATION_VERSION = 1;

    private MigrationRunner() {
    }

    public static void migrate(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            createHistoryTable(connection);
            if (!migrationApplied(connection, FOUNDATION_VERSION)) {
                applyFoundationMigration(connection);
                recordMigration(connection, FOUNDATION_VERSION);
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

    private static void applyFoundationMigration(Connection connection) throws SQLException {
        String sql;
        try (InputStream input = MigrationRunner.class.getResourceAsStream(
                "/db/migration/V001__foundation.sql")) {
            if (input == null) {
                throw new SQLException("Foundation migration resource is missing");
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
