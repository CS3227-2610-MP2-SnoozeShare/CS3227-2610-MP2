package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class SystemRoleMigrationTest {

    private static final String SYSTEM_ID = "a0000000-0000-0000-0000-0000000000ff";

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    @Test
    void theSeededSystemUserIsRoleSystemAndActive() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("SYSTEM", scalar(connection, "SELECT role FROM users WHERE userId = '" + SYSTEM_ID + "'"));
            assertEquals("ACTIVE",
                    scalar(connection, "SELECT accountStatus FROM users WHERE userId = '" + SYSTEM_ID + "'"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 6"));
        }
    }

    private static void runMigrationFile(Connection connection, String name) throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/" + name + ".sql"));
        try (Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) {
                    statement.executeUpdate(part);
                }
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static Connection v005Database() throws Exception {
        Connection connection = DatabaseTestSupport.openIsolatedDatabase();
        execute(connection, "PRAGMA foreign_keys = OFF");
        for (String name : new String[] {"V001__foundation", "V002__audit_trail", "V003__suspension_reason",
            "V004__messaging", "V005__booking_messaging"}) {
            runMigrationFile(connection, name);
        }
        execute(connection, "ALTER TABLE bookings ADD COLUMN hostDecisionMessage TEXT");
        execute(connection, "CREATE TABLE schema_history (version INTEGER PRIMARY KEY, appliedAt TEXT NOT NULL)");
        for (int version = 1; version <= 5; version++) {
            execute(connection, "INSERT INTO schema_history (version, appliedAt) VALUES (" + version
                    + ", '2026-01-01T00:00:00Z')");
        }
        execute(connection, "PRAGMA foreign_keys = ON");
        return connection;
    }

    @Test
    void aV005DatabaseKeepsItsUsersAndChildRowsThroughTheRebuild() throws Exception {
        try (Connection connection = v005Database()) {
            execute(connection, "INSERT INTO users (userId, role, displayName, email, accountStatus, createdAt) "
                    + "VALUES ('h1', 'HOST', 'H', 'h@x.test', 'ACTIVE', '2026-01-01T00:00:00Z')");
            execute(connection, "INSERT INTO users (userId, role, displayName, email, accountStatus, createdAt) "
                    + "VALUES ('g1', 'GUEST', 'G', 'g@x.test', 'ACTIVE', '2026-01-01T00:00:00Z')");
            execute(connection, "INSERT INTO wallets (walletId, userId, balance, currency, updatedAt) "
                    + "VALUES ('wh', 'h1', 10, 'SGD', '2026-01-01T00:00:00Z')");
            execute(connection, "INSERT INTO wallets (walletId, userId, balance, currency, updatedAt) "
                    + "VALUES ('wg', 'g1', 20, 'SGD', '2026-01-01T00:00:00Z')");
            execute(connection, "INSERT INTO properties (propertyId, hostId, status, title, description, "
                    + "propertyType, streetAddress, city, region, postalCode, maxGuests, bedrooms, bathrooms, "
                    + "baseNightlyRate, checkInTime, checkOutTime, amenities, createdAt) VALUES ('p1', 'h1', "
                    + "'ACTIVE', 'T', 'D', 'HOUSE', 'S', 'C', 'R', '1', 2, 1, 1, 100, '15:00', '11:00', '', "
                    + "'2026-01-01T00:00:00Z')");
            execute(connection, "INSERT INTO audit_log (logId, actorUserId, actionType, entityType, entityId, "
                    + "timestamp) VALUES ('l1', 'h1', 'X', 'USER', 'h1', '2026-01-01T00:00:00Z')");

            MigrationRunner.migrate(connection);

            try (Statement statement = connection.createStatement();
                 ResultSet violations = statement.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(violations.next());
            }
            assertEquals("2", scalar(connection, "SELECT COUNT(*) FROM users WHERE userId IN ('h1', 'g1')"));
            assertEquals("HOST", scalar(connection, "SELECT role FROM users WHERE userId = 'h1'"));
            assertEquals("2", scalar(connection, "SELECT COUNT(*) FROM wallets WHERE walletId IN ('wh', 'wg')"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM properties WHERE propertyId = 'p1'"));
            assertEquals("h1", scalar(connection, "SELECT actorUserId FROM audit_log WHERE logId = 'l1'"));
            assertEquals("SYSTEM", scalar(connection, "SELECT role FROM users WHERE userId = '" + SYSTEM_ID + "'"));
            assertEquals("ACTIVE",
                    scalar(connection, "SELECT accountStatus FROM users WHERE userId = '" + SYSTEM_ID + "'"));
            assertThrows(SQLException.class, () -> execute(connection, "INSERT INTO users (userId, role, "
                    + "displayName, email, accountStatus, createdAt) VALUES ('u2', 'ROBOT', 'R', 'r@x.test', "
                    + "'ACTIVE', '2026-01-01T00:00:00Z')"));
            assertEquals("1", scalar(connection, "PRAGMA foreign_keys"));
        }
    }

    @Test
    void theRebuildKeepsChildRowsAndAcceptsOnlyKnownRoles() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            execute(connection, "INSERT INTO users (userId, role, displayName, email, accountStatus, createdAt) "
                    + "VALUES ('u1', 'HOST', 'H', 'h@x.test', 'ACTIVE', '2026-01-01T00:00:00Z')");
            execute(connection, "INSERT INTO wallets (walletId, userId, balance, currency, updatedAt) "
                    + "VALUES ('w1', 'u1', 0, 'SGD', '2026-01-01T00:00:00Z')");
            try (Statement statement = connection.createStatement();
                 ResultSet violations = statement.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(violations.next());
            }
            assertThrows(SQLException.class, () -> execute(connection, "INSERT INTO users (userId, role, "
                    + "displayName, email, accountStatus, createdAt) VALUES ('u2', 'ROBOT', 'R', 'r@x.test', "
                    + "'ACTIVE', '2026-01-01T00:00:00Z')"));
        }
    }
}
