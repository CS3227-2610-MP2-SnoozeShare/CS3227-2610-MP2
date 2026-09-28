package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.testsupport.LedgerTestSupport;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class LedgerConservationTest {

    /** The mock DB's System wallet already holds two seeded platform fees. */
    private static final BigDecimal SEEDED_FEES = new BigDecimal("16.35");

    /** Money is never created: balances add up, no booking releases more than it held, paid bookings net to 0. */
    static void assertConserved(MockDbFixture db) throws SQLException {
        double balances = Double.parseDouble(db.scalarString("SELECT COALESCE(SUM(balance), 0) FROM wallets"));
        double adjustments = Double.parseDouble(db.scalarString(
                "SELECT COALESCE(SUM(walletAdjustment), 0) FROM audit_log WHERE walletAdjustment IS NOT NULL"));
        assertEquals(adjustments, balances, 0.005, "wallet balances must equal the sum of all money rows");

        assertEquals(0, db.scalarLong("SELECT COUNT(*) FROM (SELECT bookingId FROM audit_log "
                + "WHERE walletAdjustment IS NOT NULL AND bookingId IS NOT NULL GROUP BY bookingId "
                + "HAVING SUM(walletAdjustment) > 0.005)"), "a booking released more than it held");

        assertEquals(0, db.scalarLong("SELECT COUNT(*) FROM (SELECT bookingId FROM audit_log "
                + "WHERE walletAdjustment IS NOT NULL AND bookingId IN "
                + "(SELECT bookingId FROM audit_log WHERE actionType = 'BOOKING_PAYOUT') "
                + "GROUP BY bookingId HAVING ABS(SUM(walletAdjustment)) > 0.005)"),
                "a booking that paid its host must settle to exactly zero");

        assertEquals(0, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL "
                + "AND bookingId IS NULL AND actionType NOT IN ('TOP_UP', 'WITHDRAWAL')"),
                "only top-ups and withdrawals may have no booking");
    }

    @Test
    void theShippedMockDbConservesMoney(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.assertLedgerInvariant();
            assertConserved(db);
        }
    }

    @Test
    void aRealSettlementStillConservesMoney(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            var systemWalletBefore = new JdbcWalletRepository(db.connection())
                    .findByUserId(AuditService.SYSTEM_ACTOR_ID).orElseThrow().balance();
            assertEquals(0, SEEDED_FEES.compareTo(systemWalletBefore));

            var service = SettlementFixtures.settlement(db, new InProcessEventBus(),
                    LedgerTestSupport.writer(db.connection()));
            Settlement result = service.settle(MockIds.TICKET_3, ResolutionMode.MANUAL,
                    new BigDecimal("60.00"), MockIds.AGENT_BEN, "Partial credit agreed");

            db.assertLedgerInvariant();
            assertConserved(db);

            BigDecimal systemWalletAfter = new JdbcWalletRepository(db.connection())
                    .findByUserId(AuditService.SYSTEM_ACTOR_ID).orElseThrow().balance();
            assertEquals(0, SEEDED_FEES.add(result.breakdown().fee()).compareTo(systemWalletAfter));
        }
    }
}
