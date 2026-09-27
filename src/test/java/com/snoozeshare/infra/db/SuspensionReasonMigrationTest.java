package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class SuspensionReasonMigrationTest {

    @Test
    void freshDatabaseGetsANullableSuspensionReasonColumnAndVersionThree() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);

            try (Statement statement = connection.createStatement();
                 ResultSet column = statement.executeQuery(
                         "SELECT \"notnull\" FROM pragma_table_info('users') WHERE name = 'suspensionReason'")) {
                assertEquals(true, column.next(), "users.suspensionReason exists");
                assertEquals(0, column.getInt(1), "and is nullable");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet history = statement.executeQuery(
                         "SELECT COUNT(*) FROM schema_history WHERE version = 3")) {
                history.next();
                assertEquals(1, history.getInt(1));
            }
        }
    }
}
