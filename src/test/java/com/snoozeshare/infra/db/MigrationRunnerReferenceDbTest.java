package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.testsupport.MockDbFixture;

class MigrationRunnerReferenceDbTest {

    private static long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void runScript(Connection connection, Path file) throws Exception {
        String sql = Files.readString(file).lines().map(line -> line.replaceAll("--.*", ""))
                .reduce("", (left, right) -> left + "\n" + right);
        for (String statementSql : sql.split(";")) {
            if (!statementSql.isBlank()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(statementSql);
                }
            }
        }
    }

    @Test
    void theShippedReferenceDatabaseIsAlreadyMigratedSoMigrateChangesNothing(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            long historyBefore = db.scalarLong("SELECT COUNT(*) FROM schema_history");

            MigrationRunner.migrate(db.connection());
            MigrationRunner.migrate(db.connection());

            assertEquals(historyBefore, db.scalarLong("SELECT COUNT(*) FROM schema_history"));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM schema_history WHERE version = 1"));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM schema_history WHERE version = 2"));
            assertEquals(17L, db.scalarLong("SELECT COUNT(*) FROM users"));
        }
    }

    @Test
    void adoptsAFoundationOnlyReferenceDatabaseAndUpgradesItToV002(@TempDir Path directory) throws Exception {
        // The pre-V002 shape: V001 tables (old audit_log) and no schema_history, like the old committed file.
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite:" + directory.resolve("old.db"))) {
            runScript(connection, Path.of("src/main/resources/db/migration/V001__foundation.sql"));
            assertEquals(0L, scalar(connection,
                    "SELECT COUNT(*) FROM pragma_table_info('audit_log') WHERE name = 'walletAdjustment'"));

            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);

            assertEquals(1L, scalar(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 1"));
            assertEquals(1L, scalar(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 2"));
            assertEquals(1L, scalar(connection,
                    "SELECT COUNT(*) FROM pragma_table_info('audit_log') WHERE name = 'walletAdjustment'"));
            assertEquals(1L, scalar(connection, "SELECT COUNT(*) FROM users WHERE userId = "
                    + "'a0000000-0000-0000-0000-0000000000ff'"));
        }
    }

    @Test
    void adoptsAReferenceDatabaseRebuiltFromSchemaSqlWithoutHistory(@TempDir Path directory) throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite:" + directory.resolve("rebuilt.db"))) {
            runScript(connection, Path.of("db/schema.sql"));
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE schema_history");
            }

            MigrationRunner.migrate(connection);

            assertEquals(2L, scalar(connection, "SELECT COUNT(*) FROM schema_history"));
            assertEquals(1L, scalar(connection,
                    "SELECT COUNT(*) FROM pragma_table_info('audit_log') WHERE name = 'walletAdjustment'"));
        }
    }
}
