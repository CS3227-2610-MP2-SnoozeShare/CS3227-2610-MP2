package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class UnifiedLedgerMigrationTest {

    private static final String SYSTEM = "a0000000-0000-0000-0000-0000000000ff";

    private static void run(Connection connection, String sql) throws SQLException {
        for (String part : sql.split(";")) {
            if (!part.isBlank()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(part);
                }
            }
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    /** A database exactly as V006 leaves it, with legacy ledger rows and one already-audited transaction. */
    private static Connection legacyDatabase() throws Exception {
        Connection connection = DatabaseTestSupport.openIsolatedDatabase();
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
        }
        for (String file : new String[] {"V001__foundation", "V002__audit_trail", "V003__suspension_reason",
            "V004__messaging", "V005__booking_messaging", "V006__system_role"}) {
            run(connection, Files.readString(Path.of("src/main/resources/db/migration/" + file + ".sql")));
        }
        run(connection, "ALTER TABLE bookings ADD COLUMN hostDecisionMessage TEXT");
        run(connection, "CREATE TABLE IF NOT EXISTS schema_history "
                + "(version INTEGER PRIMARY KEY, appliedAt TEXT NOT NULL)");
        for (int version = 1; version <= 6; version++) {
            run(connection, "INSERT INTO schema_history VALUES (" + version + ", '2026-09-27 00:00:00')");
        }
        run(connection, """
                INSERT INTO users (userId, role, displayName, email, accountStatus, createdAt) VALUES
                ('c1', 'GUEST', 'Gia Guest', 'g@x.test', 'ACTIVE', '2026-01-01T00:00:00Z'),
                ('b1', 'HOST', 'Hal Host', 'h@x.test', 'ACTIVE', '2026-01-01T00:00:00Z');
                INSERT INTO wallets VALUES ('w-g', 'c1', 60, 'SGD', '2026-09-01T00:00:00Z');
                INSERT INTO wallets VALUES ('w-h', 'b1', 97, 'SGD', '2026-09-01T00:00:00Z');
                INSERT INTO wallet_transactions (transactionId, walletId, type, amount, feeAmount, balanceAfter,
                    createdAt) VALUES
                ('t1', 'w-g', 'TOP_UP', 100, NULL, 100, '2026-09-01T00:00:00Z'),
                ('t2', 'w-g', 'ESCROW_HOLD', -40, NULL, 60, '2026-09-02T00:00:00Z'),
                ('t3', 'w-h', 'BOOKING_PAYOUT', 97, 3, 97, '2026-09-03T00:00:00Z');
                INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId,
                    walletAdjustment, subjectUserId, subjectName, timestamp) VALUES
                ('l1', 'c1', 'Gia Guest', 'TOP_UP', 'WalletTransaction', 't1', 100, 'c1', 'Gia Guest',
                    '2026-09-01T00:00:00Z')""");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    @Test
    void existingMoneyRowsGetBalanceAfterAndMissingOnesAreBackfilled() throws Exception {
        try (Connection connection = legacyDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("100.0", scalar(connection, "SELECT balanceAfter FROM audit_log WHERE entityId = 't1'"));
            assertEquals("60.0", scalar(connection,
                    "SELECT balanceAfter FROM audit_log WHERE entityId = 't2' AND actionType = 'ESCROW_HOLD'"));
            assertEquals("-40.0", scalar(connection,
                    "SELECT walletAdjustment FROM audit_log WHERE entityId = 't2'"));
            assertEquals("c1", scalar(connection, "SELECT subjectUserId FROM audit_log WHERE entityId = 't2'"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM audit_log WHERE entityId = 't1'"),
                    "an already-audited transaction is not duplicated");
        }
    }

    @Test
    void legacyPayoutFeesBecomeSystemFeeRowsAndTheSystemWalletHoldsThem() throws Exception {
        try (Connection connection = legacyDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("1", scalar(connection,
                    "SELECT COUNT(*) FROM audit_log WHERE actionType = 'PLATFORM_FEE' AND subjectUserId = '"
                            + SYSTEM + "'"));
            assertEquals("3.0", scalar(connection, "SELECT walletAdjustment FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE'"));
            assertEquals("3.0", scalar(connection, "SELECT balanceAfter FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE'"));
            assertEquals("3.0", scalar(connection, "SELECT balance FROM wallets WHERE userId = '" + SYSTEM + "'"));
        }
    }

    @Test
    void aFreshDatabaseGetsAnEmptySystemWalletAndVersionSeven() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("0.0", scalar(connection, "SELECT balance FROM wallets WHERE userId = '" + SYSTEM + "'"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 7"));
            MigrationRunner.migrate(connection);
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM wallets WHERE userId = '" + SYSTEM + "'"));
        }
    }
}
