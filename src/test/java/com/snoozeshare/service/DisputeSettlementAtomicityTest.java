package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.infra.events.DomainEvent;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.DisputeSettlementServiceImpl;
import com.snoozeshare.testsupport.FailingAuditService;
import com.snoozeshare.testsupport.LedgerTestSupport;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeSettlementAtomicityTest {

    private static final String MONEY_ROWS = "SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL";

    /**
     * A settlement whose status rows and money rows all go through one audit service that fails on its Nth
     * call. MANUAL 60.00 on ticket 3 makes 5 calls: ticket, booking, guest money, host money, platform fee.
     */
    private static DisputeSettlementServiceImpl failingOn(MockDbFixture db, InProcessEventBus bus, int failingCall) {
        var connection = db.connection();
        var realAudit = new AuditServiceImpl(new JdbcAuditLogRepository(connection),
                new JdbcUserRepository(connection), SettlementFixtures.CLOCK);
        var failing = new FailingAuditService(realAudit, failingCall);
        return SettlementFixtures.settlement(db, bus, LedgerTestSupport.writer(connection, failing), failing);
    }

    private static BigDecimal systemBalance(MockDbFixture db) throws Exception {
        return new BigDecimal(db.scalarString("SELECT w.balance FROM wallets w JOIN users u ON u.userId = w.userId "
                + "WHERE u.role = 'SYSTEM'"));
    }

    @Test
    void aFailureAfterTheGuestRowRollsEverythingBackAndPublishesNothing(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            InProcessEventBus bus = new InProcessEventBus();
            List<DomainEvent> events = new ArrayList<>();
            bus.subscribe(TicketResolvedEvent.class, events::add);
            bus.subscribe(WalletTransactionRecordedEvent.class, events::add);
            // Call 4 is the host money row, the one after the guest's.
            var service = failingOn(db, bus, 4);
            long before = db.scalarLong(MONEY_ROWS);

            assertThrows(RuntimeException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.MANUAL, new BigDecimal("60.00"),
                            MockIds.AGENT_BEN, "split"));

            assertEquals(before, db.scalarLong(MONEY_ROWS));
            assertEquals(0, new BigDecimal("790").compareTo(db.walletBalance(MockIds.WALLET_SOPHIA)));
            assertEquals(0, new BigDecimal("150").compareTo(db.walletBalance(MockIds.WALLET_DIEGO)));
            assertEquals("IN_REVIEW", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
            assertEquals("CONFIRMED", db.scalarString(
                    "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_11));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'TICKET_RESOLVED' AND entityId = ?", MockIds.TICKET_3));
            assertTrue(events.isEmpty());
            db.assertLedgerInvariant();
        }
    }

    @Test
    void aFailureOnAnyAuditWriteRollsTicketBookingWalletsAndAuditRowsBack(@TempDir Path directory)
            throws Exception {
        // MANUAL 60.00 on ticket 3 writes 5 audit rows: ticket, booking, guest money, host money, platform fee.
        for (int failingCall = 1; failingCall <= 5; failingCall++) {
            Path folder = Files.createDirectory(directory.resolve("call-" + failingCall));
            try (MockDbFixture db = MockDbFixture.open(folder)) {
                InProcessEventBus bus = new InProcessEventBus();
                List<DomainEvent> events = new ArrayList<>();
                bus.subscribe(TicketResolvedEvent.class, events::add);
                bus.subscribe(WalletTransactionRecordedEvent.class, events::add);
                var service = failingOn(db, bus, failingCall);
                BigDecimal systemBefore = systemBalance(db);
                long auditBefore = db.scalarLong("SELECT COUNT(*) FROM audit_log");
                String label = "failing audit call " + failingCall;

                assertThrows(RuntimeException.class, () ->
                        service.settle(MockIds.TICKET_3, ResolutionMode.MANUAL, new BigDecimal("60.00"),
                                MockIds.AGENT_BEN, "split"), label);

                assertEquals(auditBefore, db.scalarLong("SELECT COUNT(*) FROM audit_log"), label);
                assertEquals(0, systemBefore.compareTo(systemBalance(db)), label);
                assertEquals(0, new BigDecimal("790").compareTo(db.walletBalance(MockIds.WALLET_SOPHIA)),
                        label);
                assertEquals(0, new BigDecimal("150").compareTo(db.walletBalance(MockIds.WALLET_DIEGO)),
                        label);
                assertEquals("IN_REVIEW", db.scalarString(
                        "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3), label);
                assertEquals("CONFIRMED", db.scalarString(
                        "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_11), label);
                assertTrue(events.isEmpty(), label);
                db.assertLedgerInvariant();
            }
        }
    }

    @Test
    void theConnectionIsUsableAfterARollback(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            var failing = failingOn(db, new InProcessEventBus(), 1);
            assertThrows(RuntimeException.class, () ->
                    failing.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO,
                            MockIds.AGENT_BEN, "boom"));

            var healthy = SettlementFixtures.settlement(db, new InProcessEventBus(),
                    LedgerTestSupport.writer(db.connection()));
            healthy.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO,
                    MockIds.AGENT_BEN, "retry succeeds");

            assertEquals("RESOLVED_REJECTED", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
            db.assertLedgerInvariant();
        }
    }

    @Test
    void aFailedPlatformFeeRowLeavesTheTicketUnresolvedAndTheHostUnpaid(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            // REJECT on ticket 3 makes 4 calls: ticket, booking, host money, platform fee (fails).
            var service = failingOn(db, new InProcessEventBus(), 4);
            BigDecimal systemBefore = systemBalance(db);

            assertThrows(RuntimeException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO,
                            MockIds.AGENT_BEN, "no evidence"));

            assertEquals("IN_REVIEW", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
            assertEquals(0, new BigDecimal("150").compareTo(db.walletBalance(MockIds.WALLET_DIEGO)));
            assertEquals(0, systemBefore.compareTo(systemBalance(db)));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? "
                    + "AND actionType IN ('BOOKING_PAYOUT', 'PLATFORM_FEE')", MockIds.TICKET_3));
            db.assertLedgerInvariant();
        }
    }
}
