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

    /**
     * legacyDatabase() plus a second payout fee at a different wallet, a null-fee payout, a
     * zero-fee payout, a row whose initiatedBy differs from the wallet owner, and two rows
     * whose transactionId order is the reverse of their timestamp order.
     */
    private static Connection legacyDatabaseWithMoreLedgerRows() throws Exception {
        Connection connection = legacyDatabase();
        run(connection, """
                INSERT INTO users (userId, role, displayName, email, accountStatus, createdAt) VALUES
                ('b2', 'HOST', 'Hana Host', 'h2@x.test', 'ACTIVE', '2026-01-01T00:00:00Z');
                INSERT INTO wallets VALUES ('w-h2', 'b2', 194, 'SGD', '2026-09-01T00:00:00Z');
                INSERT INTO wallet_transactions (transactionId, walletId, type, amount, feeAmount, balanceAfter,
                    createdAt) VALUES
                ('t4', 'w-h2', 'BOOKING_PAYOUT', 194, 6, 194, '2026-09-04T00:00:00Z'),
                ('t5', 'w-h', 'BOOKING_PAYOUT', 50, NULL, 147, '2026-09-05T00:00:00Z'),
                ('t6', 'w-h', 'BOOKING_PAYOUT', 20, 0, 167, '2026-09-06T00:00:00Z'),
                ('t-zz-early', 'w-g', 'TOP_UP', 5, NULL, 55, '2026-09-09T00:00:00Z'),
                ('t-aa-late', 'w-g', 'TOP_UP', 5, NULL, 60, '2026-09-10T00:00:00Z');
                INSERT INTO wallet_transactions (transactionId, walletId, type, amount, feeAmount, balanceAfter,
                    initiatedBy, createdAt) VALUES
                ('t7', 'w-g', 'WITHDRAWAL', -10, NULL, 50, 'b1', '2026-09-08T00:00:00Z')""");
        return connection;
    }

    /**
     * legacyDatabase() plus a BOOKING_PAYOUT that already has an audit_log row (pre-W12 style),
     * so the backfill skips the payout itself -- but it still needs balanceAfter copied and its
     * own PLATFORM_FEE row.
     */
    private static Connection legacyDatabaseWithAlreadyAuditedPayout() throws Exception {
        Connection connection = legacyDatabase();
        run(connection, """
                INSERT INTO wallet_transactions (transactionId, walletId, type, amount, feeAmount, balanceAfter,
                    createdAt) VALUES
                ('t8', 'w-h', 'BOOKING_PAYOUT', 150, 5, 242, '2026-09-11T00:00:00Z');
                INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId,
                    walletAdjustment, subjectUserId, subjectName, timestamp) VALUES
                ('l2', 'b1', 'Hal Host', 'BOOKING_PAYOUT', 'WalletTransaction', 't8', 150, 'b1', 'Hal Host',
                    '2026-09-11T00:00:00Z')""");
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

    @Test
    void runningFeeBalanceAccumulatesAcrossMultiplePayouts() throws Exception {
        try (Connection connection = legacyDatabaseWithMoreLedgerRows()) {
            MigrationRunner.migrate(connection);
            assertEquals("2", scalar(connection,
                    "SELECT COUNT(*) FROM audit_log WHERE actionType = 'PLATFORM_FEE'"));
            assertEquals("3.0", scalar(connection, "SELECT balanceAfter FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE' AND walletAdjustment = 3"));
            assertEquals("9.0", scalar(connection, "SELECT balanceAfter FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE' AND walletAdjustment = 6"));
            assertEquals("9.0", scalar(connection, "SELECT balance FROM wallets WHERE userId = '" + SYSTEM + "'"));
        }
    }

    @Test
    void payoutsWithNullOrZeroFeeGetNoPlatformFeeRow() throws Exception {
        try (Connection connection = legacyDatabaseWithMoreLedgerRows()) {
            MigrationRunner.migrate(connection);
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE' AND timestamp = '2026-09-05T00:00:00Z'"),
                    "a NULL feeAmount payout gets no PLATFORM_FEE row");
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE' AND timestamp = '2026-09-06T00:00:00Z'"),
                    "a zero feeAmount payout gets no PLATFORM_FEE row");
            assertEquals("2", scalar(connection,
                    "SELECT COUNT(*) FROM audit_log WHERE actionType = 'PLATFORM_FEE'"),
                    "only the two fee-bearing payouts produce a PLATFORM_FEE row");
        }
    }

    @Test
    void backfilledActorComesFromInitiatedByWhenSet() throws Exception {
        try (Connection connection = legacyDatabaseWithMoreLedgerRows()) {
            MigrationRunner.migrate(connection);
            assertEquals("b1", scalar(connection, "SELECT actorUserId FROM audit_log WHERE entityId = 't7'"));
            assertEquals("Hal Host", scalar(connection, "SELECT actorName FROM audit_log WHERE entityId = 't7'"));
            assertEquals("c1", scalar(connection, "SELECT subjectUserId FROM audit_log WHERE entityId = 't7'"),
                    "the subject stays the wallet owner even when someone else initiated it");
        }
    }

    @Test
    void backfilledRowsAreOrderedByTimestampNotTransactionId() throws Exception {
        try (Connection connection = legacyDatabaseWithMoreLedgerRows()) {
            MigrationRunner.migrate(connection);
            assertEquals("t-zz-early", scalar(connection, "SELECT entityId FROM audit_log "
                    + "WHERE entityId IN ('t-zz-early', 't-aa-late') ORDER BY rowid LIMIT 1"));
            assertEquals("t-aa-late", scalar(connection, "SELECT entityId FROM audit_log "
                    + "WHERE entityId IN ('t-zz-early', 't-aa-late') ORDER BY rowid DESC LIMIT 1"));
        }
    }

    @Test
    void alreadyAuditedPayoutStillGetsBalanceAfterAndFeeRow() throws Exception {
        try (Connection connection = legacyDatabaseWithAlreadyAuditedPayout()) {
            MigrationRunner.migrate(connection);
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM audit_log WHERE entityId = 't8'"),
                    "the payout itself is not duplicated by the backfill");
            assertEquals("242.0", scalar(connection, "SELECT balanceAfter FROM audit_log "
                    + "WHERE entityId = 't8' AND actionType = 'BOOKING_PAYOUT'"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE' AND timestamp = '2026-09-11T00:00:00Z'"),
                    "a fee row is written even though the payout itself was already audited");
            assertEquals("5.0", scalar(connection, "SELECT walletAdjustment FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE' AND timestamp = '2026-09-11T00:00:00Z'"));
        }
    }

    @Test
    void unifiedLedgerMigrationCreatesAuditMoneyIndex() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("idx_audit_money", scalar(connection,
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'idx_audit_money'"));
        }
    }
}
