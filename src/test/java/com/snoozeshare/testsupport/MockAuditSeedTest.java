package com.snoozeshare.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.AuditAction;

class MockAuditSeedTest {

    @Test
    void everyWalletTransactionHasExactlyOneMoneyRowWithTheAppliedAmount(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions t WHERE "
                    + "(SELECT COUNT(*) FROM audit_log a WHERE a.entityType = 'WalletTransaction' "
                    + "AND a.entityId = t.transactionId) <> 1"));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions t JOIN audit_log a "
                    + "ON a.entityId = t.transactionId WHERE abs(a.walletAdjustment - t.amount) > 0.005"));
        }
    }

    @Test
    void noRowMixesAStatusChangeWithMoneyAndEveryRowHasNamesAndAKnownAction(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL "
                    + "AND (beforeState IS NOT NULL OR afterState IS NOT NULL)"));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE actorName IS NULL"));
            try (var statement = db.connection().createStatement();
                 ResultSet result = statement.executeQuery("SELECT DISTINCT actionType FROM audit_log")) {
                while (result.next()) {
                    AuditAction.valueOf(result.getString(1));
                }
            }
            long total = db.scalarLong("SELECT COUNT(*) FROM audit_log");
            assertTrue(total > 50 && total < 200, "seeded rows fit the first page: " + total);
        }
    }

    @Test
    void ticketFourManualSettlementReadsAsFourRows(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            for (String action : new String[] {"TICKET_OPENED", "TICKET_ASSIGNED", "TICKET_RESOLVED",
                "BOOKING_COMPLETED", "AGENT_OVERRIDE", "BOOKING_PAYOUT"}) {
                assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? "
                        + "AND actionType = ?", MockIds.TICKET_4, action), action);
            }
            assertEquals(165.0, Double.parseDouble(db.scalarString("SELECT walletAdjustment FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'AGENT_OVERRIDE'", MockIds.TICKET_4)), 0.001);
            assertEquals(160.05, Double.parseDouble(db.scalarString("SELECT walletAdjustment FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'BOOKING_PAYOUT'", MockIds.TICKET_4)), 0.001);
        }
    }

    @Test
    void accountGovernanceRowsAreSeededInTheShapeW11Will(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            assertEquals(2L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE actionType = 'ACCOUNT_SUSPENDED' "
                    + "AND entityType = 'User' AND beforeState = 'ACTIVE' AND afterState = 'SUSPENDED' "
                    + "AND reason IS NOT NULL AND subjectUserId = entityId"));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'BOOKING_FORCE_CANCELLED' AND bookingId IS NOT NULL"));
        }
    }
}
