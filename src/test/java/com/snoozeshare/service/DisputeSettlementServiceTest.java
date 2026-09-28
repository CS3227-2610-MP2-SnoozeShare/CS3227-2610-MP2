package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.infra.events.DomainEvent;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.jdbc.JdbcLedgerRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.DisputeSettlementServiceImpl;
import com.snoozeshare.testsupport.LedgerTestSupport;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeSettlementServiceTest {

    private static final UUID BEN = MockIds.AGENT_BEN;
    private static final String NOW = "2026-09-25T04:00:00Z";
    /** The mock DB's System wallet already holds two seeded platform fees. */
    private static final BigDecimal SEEDED_FEES = new BigDecimal("16.35");
    private static final String MONEY_ROWS = "SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL";

    private static DisputeSettlementServiceImpl service(MockDbFixture db, InProcessEventBus bus) {
        return SettlementFixtures.settlement(db, bus, LedgerTestSupport.writer(db.connection()));
    }

    private static BigDecimal systemBalance(MockDbFixture db) {
        return new JdbcWalletRepository(db.connection()).findByUserId(AuditService.SYSTEM_ACTOR_ID)
                .orElseThrow().balance();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " vs " + actual);
    }

    @Test
    void rejectPaysTheHostInFullNetOfTheFeeAndRefundsNothing(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            InProcessEventBus bus = new InProcessEventBus();
            List<DomainEvent> events = new ArrayList<>();
            bus.subscribe(TicketResolvedEvent.class, events::add);
            bus.subscribe(WalletTransactionRecordedEvent.class, events::add);

            Settlement result = service(db, bus).settle(MockIds.TICKET_3, ResolutionMode.REJECT,
                    BigDecimal.ZERO, BEN, "No evidence of a violation");

            assertNull(result.guestTransaction());
            assertEquals(WalletTransactionType.BOOKING_PAYOUT, result.hostTransaction().type());
            assertMoney("203.70", result.hostTransaction().amount());
            assertMoney("6.30", result.breakdown().fee());
            assertMoney("353.70", result.hostTransaction().balanceAfter());
            assertEquals(MockIds.TICKET_3, result.hostTransaction().relatedTicketId());
            assertEquals(BEN, result.hostTransaction().initiatedBy());
            assertMoney("353.70", db.walletBalance(MockIds.WALLET_DIEGO));
            assertMoney("790", db.walletBalance(MockIds.WALLET_SOPHIA));
            assertEquals(TicketStatus.RESOLVED_REJECTED, result.ticket().status());
            assertEquals("No evidence of a violation", result.ticket().resolutionReason());
            assertEquals(SettlementFixtures.NOW, result.ticket().resolvedAt());
            assertEquals(BookingStatus.COMPLETED, result.booking().status());
            assertEquals(SettlementFixtures.NOW, result.booking().completedAt());
            assertEquals("COMPLETED", db.scalarString(
                    "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_11));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'TICKET_RESOLVED' AND entityId = ?", MockIds.TICKET_3));
            assertEquals(2, events.size(), "the System wallet's fee row publishes nothing");
            var system = new JdbcWalletRepository(db.connection()).findByUserId(AuditService.SYSTEM_ACTOR_ID)
                    .orElseThrow();
            var fees = new JdbcLedgerRepository(db.connection()).entriesForWallet(system.walletId());
            BigDecimal expectedFee = result.breakdown().fee();
            assertEquals(0, expectedFee.compareTo(system.balance().subtract(SEEDED_FEES)));
            var feeRow = fees.get(fees.size() - 1);
            assertEquals(WalletTransactionType.PLATFORM_FEE, feeRow.type());
            assertMoney("6.30", feeRow.amount());
            assertEquals(MockIds.BOOKING_11, feeRow.relatedBookingId());
            assertEquals(MockIds.TICKET_3, feeRow.relatedTicketId());
            db.assertLedgerInvariant();
        }
    }

    @Test
    void acceptOfAFullRefundReturnsTheWholeEscrowToTheGuestWithNoHostRow(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            Settlement result = service(db, new InProcessEventBus()).settle(MockIds.TICKET_3,
                    ResolutionMode.ACCEPT, new BigDecimal("210.00"), BEN, "Host never responded");

            assertNull(result.hostTransaction());
            assertEquals(WalletTransactionType.TICKET_REMEDY, result.guestTransaction().type());
            assertMoney("210", result.guestTransaction().amount());
            assertMoney("1000", result.guestTransaction().balanceAfter());
            assertMoney("1000", db.walletBalance(MockIds.WALLET_SOPHIA));
            assertMoney("150", db.walletBalance(MockIds.WALLET_DIEGO));
            assertEquals(TicketStatus.RESOLVED_APPROVED, result.ticket().status());
            assertEquals(0, SEEDED_FEES.compareTo(systemBalance(db)), "a full refund charges no fee");
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE actionType = 'PLATFORM_FEE' "
                    + "AND ticketId = ?", MockIds.TICKET_3));
            db.assertLedgerInvariant();
        }
    }

    @Test
    void manualCustomSplitSettlesBothWalletsAndRecordsAnAgentOverride(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            InProcessEventBus bus = new InProcessEventBus();
            List<DomainEvent> events = new ArrayList<>();
            bus.subscribe(TicketResolvedEvent.class, events::add);
            bus.subscribe(WalletTransactionRecordedEvent.class, events::add);

            Settlement result = service(db, bus).settle(MockIds.TICKET_3, ResolutionMode.MANUAL,
                    new BigDecimal("60.00"), BEN, "Partial credit agreed");

            assertEquals(WalletTransactionType.AGENT_OVERRIDE, result.guestTransaction().type());
            assertMoney("60", result.guestTransaction().amount());
            assertMoney("850", result.guestTransaction().balanceAfter());
            assertEquals(WalletTransactionType.BOOKING_PAYOUT, result.hostTransaction().type());
            assertMoney("145.50", result.hostTransaction().amount());
            assertMoney("4.50", result.breakdown().fee());
            assertMoney("295.50", result.hostTransaction().balanceAfter());
            assertMoney("850", db.walletBalance(MockIds.WALLET_SOPHIA));
            assertMoney("295.50", db.walletBalance(MockIds.WALLET_DIEGO));
            assertEquals(TicketStatus.RESOLVED_APPROVED, result.ticket().status());
            assertEquals(3, events.size());
            assertEquals(0, SEEDED_FEES.add(new BigDecimal("4.50")).compareTo(systemBalance(db)));
            db.assertLedgerInvariant();
        }
    }

    @Test
    void manualFullPayoutIsARejectedTicketThatPaysTheHost(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            Settlement result = service(db, new InProcessEventBus()).settle(MockIds.TICKET_3,
                    ResolutionMode.MANUAL, BigDecimal.ZERO, BEN, "Guest claim unfounded");

            assertNull(result.guestTransaction());
            assertNotNull(result.hostTransaction());
            assertMoney("203.70", result.hostTransaction().amount());
            assertEquals(TicketStatus.RESOLVED_REJECTED, result.ticket().status());
        }
    }

    @Test
    void refundAboveTheEscrowIsRejectedAndNothingChanges(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong(MONEY_ROWS);

            assertThrows(IllegalArgumentException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.MANUAL, new BigDecimal("210.01"),
                            BEN, "too much"));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void rejectWithARefundIsRejectedAndNothingChanges(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong(MONEY_ROWS);

            assertThrows(IllegalArgumentException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, new BigDecimal("50.00"),
                            BEN, "reject but refund"));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void acceptWithNoRefundIsOnlyAllowedForAHostPayoutRemedy(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("UPDATE tickets SET requestedRemedy = 'FULL_REFUND' WHERE ticketId = ?",
                    MockIds.TICKET_3);
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong(MONEY_ROWS);

            assertThrows(IllegalArgumentException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.ACCEPT, BigDecimal.ZERO, BEN,
                            "accept but no refund"));

            assertUnchanged(db, before, "CONFIRMED");

            db.execute("UPDATE tickets SET requestedRemedy = 'HOST_PAYOUT' WHERE ticketId = ?",
                    MockIds.TICKET_3);
            Settlement result = service.settle(MockIds.TICKET_3, ResolutionMode.ACCEPT, BigDecimal.ZERO,
                    BEN, "host is owed");

            assertEquals(TicketStatus.RESOLVED_APPROVED, result.ticket().status());
        }
    }

    @Test
    void aBlankReasonIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong(MONEY_ROWS);

            assertThrows(IllegalArgumentException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "   "));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void onlyAnAgentAssignedToTheTicketMaySettleIt(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong(MONEY_ROWS);

            assertThrows(IllegalStateException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO,
                            MockIds.AGENT_AMY, "not mine"));
            assertThrows(IllegalStateException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO,
                            MockIds.HOST_DIEGO, "a host"));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void anAlreadyResolvedTicketCannotBeSettledAgain(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "first");
            long afterFirst = db.scalarLong(MONEY_ROWS);

            assertThrows(IllegalStateException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN,
                            "second"));

            assertEquals(afterFirst, db.scalarLong(MONEY_ROWS));
        }
    }

    @Test
    void aBookingThatIsNoLongerConfirmedCannotBeSettled(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("UPDATE bookings SET status = 'CANCELLED_BY_HOST' WHERE bookingId = ?",
                    MockIds.BOOKING_11);
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong(MONEY_ROWS);

            assertThrows(IllegalStateException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN,
                            "cancelled meanwhile"));

            assertUnchanged(db, before, "CANCELLED_BY_HOST");
        }
    }

    @Test
    void escrowThatWasAlreadyReleasedCannotBeSettled(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            LedgerTestSupport.writer(db.connection()).post(MockIds.WALLET_SOPHIA,
                    WalletTransactionType.ESCROW_REFUND, new BigDecimal("0.01"), BEN, MockIds.BOOKING_11, null,
                    null, Instant.parse("2026-09-24T00:00:00Z"));
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong(MONEY_ROWS);

            assertThrows(IllegalStateException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN,
                            "already refunded"));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void anUnknownTicketIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());

            assertThrows(IllegalArgumentException.class, () ->
                    service.settle(UUID.randomUUID(), ResolutionMode.REJECT, BigDecimal.ZERO, BEN,
                            "who?"));
        }
    }

    private static List<String> resolutionActions(MockDbFixture db, UUID ticketId) throws Exception {
        List<String> actions = new ArrayList<>();
        try (var statement = db.connection().prepareStatement("SELECT actionType FROM audit_log "
                + "WHERE ticketId = ? AND timestamp = ? ORDER BY rowid")) {
            statement.setString(1, ticketId.toString());
            statement.setString(2, NOW);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    actions.add(result.getString(1));
                }
            }
        }
        return actions;
    }

    private static BigDecimal adjustment(MockDbFixture db, UUID ticketId, String action) throws Exception {
        return new BigDecimal(db.scalarString("SELECT walletAdjustment FROM audit_log "
                + "WHERE ticketId = ? AND actionType = ? AND timestamp = ?", ticketId, action, NOW));
    }

    @Test
    void aManualSplitWritesTicketBookingGuestAndHostRowsInCausalOrder(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            service(db, new InProcessEventBus()).settle(MockIds.TICKET_3, ResolutionMode.MANUAL,
                    new BigDecimal("60.00"), BEN, "split");

            assertEquals(List.of("TICKET_RESOLVED", "BOOKING_COMPLETED", "AGENT_OVERRIDE", "BOOKING_PAYOUT",
                    "PLATFORM_FEE"), resolutionActions(db, MockIds.TICKET_3));
            assertMoney("4.50", adjustment(db, MockIds.TICKET_3, "PLATFORM_FEE"));
            assertMoney("60", adjustment(db, MockIds.TICKET_3, "AGENT_OVERRIDE"));
            assertMoney("145.50", adjustment(db, MockIds.TICKET_3, "BOOKING_PAYOUT"));
            assertEquals("IN_REVIEW", db.scalarString("SELECT beforeState FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'TICKET_RESOLVED' AND timestamp = ?",
                    MockIds.TICKET_3, NOW));
            assertEquals("RESOLVED_APPROVED", db.scalarString("SELECT afterState FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'TICKET_RESOLVED' AND timestamp = ?",
                    MockIds.TICKET_3, NOW));
            assertEquals("COMPLETED", db.scalarString("SELECT afterState FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'BOOKING_COMPLETED' AND timestamp = ?",
                    MockIds.TICKET_3, NOW));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? AND timestamp = ? "
                    + "AND walletAdjustment IS NOT NULL AND subjectUserId = ?", MockIds.TICKET_3, NOW,
                    MockIds.GUEST_SOPHIA));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? AND timestamp = ? "
                    + "AND walletAdjustment IS NOT NULL AND subjectUserId = ?", MockIds.TICKET_3, NOW,
                    MockIds.HOST_DIEGO));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? AND timestamp = ? "
                    + "AND reason LIKE '%3%% platform fee (4.50)%'", MockIds.TICKET_3, NOW));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL "
                    + "AND (beforeState IS NOT NULL OR afterState IS NOT NULL)"));
        }
    }

    @Test
    void rejectSkipsTheZeroGuestRefundRow(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            service(db, new InProcessEventBus()).settle(MockIds.TICKET_3, ResolutionMode.REJECT,
                    BigDecimal.ZERO, BEN, "No evidence of a violation");

            assertEquals(List.of("TICKET_RESOLVED", "BOOKING_COMPLETED", "BOOKING_PAYOUT", "PLATFORM_FEE"),
                    resolutionActions(db, MockIds.TICKET_3));
            assertMoney("203.70", adjustment(db, MockIds.TICKET_3, "BOOKING_PAYOUT"));
        }
    }

    private static void assertUnchanged(MockDbFixture db, long transactionCount, String bookingStatus)
            throws Exception {
        assertEquals(transactionCount, db.scalarLong(MONEY_ROWS));
        assertEquals("IN_REVIEW", db.scalarString(
                "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
        assertEquals(bookingStatus, db.scalarString(
                "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_11));
        assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                + "WHERE actionType = 'TICKET_RESOLVED' AND entityId = ?", MockIds.TICKET_3));
    }
}
