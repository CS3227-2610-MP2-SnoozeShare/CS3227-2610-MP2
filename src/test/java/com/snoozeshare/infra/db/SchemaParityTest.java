package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SchemaParityTest {

    private static final List<String> TABLES = List.of("users", "properties", "bookings", "wallets",
            "wallet_transactions", "tickets", "ticket_categories", "audit_log");

    @Test
    void referenceSchemaMatchesTheFoundationMigrationForEveryTableW10Uses() throws Exception {
        try (Connection migration = DatabaseTestSupport.openIsolatedDatabase();
             Connection reference = DatabaseTestSupport.openIsolatedDatabase()) {
            apply(migration, "src/main/resources/db/migration/V001__foundation.sql");
            apply(migration, "src/main/resources/db/migration/V002__audit_trail.sql");
            applySql(migration, "ALTER TABLE bookings ADD COLUMN hostDecisionMessage TEXT;");
            apply(reference, "db/schema.sql");

            for (String table : TABLES) {
                assertEquals(columns(migration, table), columns(reference, table), table);
            }
        }
    }

    private static void apply(Connection connection, String file) throws Exception {
        applySql(connection, Files.readString(Path.of(file)).lines()
                .map(line -> line.replaceAll("--.*", ""))
                .reduce("", (left, right) -> left + "\n" + right));
    }

    private static void applySql(Connection connection, String sql) throws Exception {
        for (String statementSql : sql.split(";")) {
            if (!statementSql.isBlank()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(statementSql);
                }
            }
        }
    }

    private static List<String> columns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                columns.add(result.getInt("cid") + "|" + result.getString("name") + "|"
                        + result.getString("type") + "|" + result.getInt("notnull") + "|"
                        + result.getString("dflt_value") + "|" + result.getInt("pk"));
            }
        }
        return columns;
    }
}
