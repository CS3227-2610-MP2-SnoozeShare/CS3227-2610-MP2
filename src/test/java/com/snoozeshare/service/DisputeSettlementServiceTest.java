package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
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
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.DisputeSettlementServiceImpl;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeSettlementServiceTest {

    private static final UUID BEN = MockIds.AGENT_BEN;

    private static DisputeSettlementServiceImpl service(MockDbFixture db, InProcessEventBus bus) {
        return SettlementFixtures.settlement(db, bus,
                new JdbcWalletTransactionRepository(db.connection()));
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
            assertMoney("6.30", result.hostTransaction().feeAmount());
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
            assertEquals(2, events.size());
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
            assertMoney("0", result.guestTransaction().feeAmount());
            assertMoney("1000", result.guestTransaction().balanceAfter());
            assertMoney("1000", db.walletBalance(MockIds.WALLET_SOPHIA));
            assertMoney("150", db.walletBalance(MockIds.WALLET_DIEGO));
            assertEquals(TicketStatus.RESOLVED_APPROVED, result.ticket().status());
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
            assertMoney("4.50", result.hostTransaction().feeAmount());
            assertMoney("295.50", result.hostTransaction().balanceAfter());
            assertMoney("850", db.walletBalance(MockIds.WALLET_SOPHIA));
            assertMoney("295.50", db.walletBalance(MockIds.WALLET_DIEGO));
            assertEquals(TicketStatus.RESOLVED_APPROVED, result.ticket().status());
            assertEquals(3, events.size());
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
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalArgumentException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.MANUAL, new BigDecimal("210.01"),
                            BEN, "too much"));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void aBlankReasonIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalArgumentException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "   "));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void onlyAnAgentAssignedToTheTicketMaySettleIt(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

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
            long afterFirst = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalStateException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN,
                            "second"));

            assertEquals(afterFirst, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"));
        }
    }

    @Test
    void aBookingThatIsNoLongerConfirmedCannotBeSettled(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("UPDATE bookings SET status = 'CANCELLED_BY_HOST' WHERE bookingId = ?",
                    MockIds.BOOKING_11);
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalStateException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN,
                            "cancelled meanwhile"));

            assertUnchanged(db, before, "CANCELLED_BY_HOST");
        }
    }

    @Test
    void escrowThatWasAlreadyReleasedCannotBeSettled(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("INSERT INTO wallet_transactions (transactionId, walletId, type, amount, "
                    + "feeAmount, balanceAfter, relatedBookingId, relatedTicketId, initiatedBy, "
                    + "createdAt) VALUES ('f0000000-0000-0000-0000-000000000001', ?, "
                    + "'ESCROW_REFUND', 0.01, NULL, 790.01, ?, NULL, NULL, '2026-09-24T00:00:00Z')",
                    MockIds.WALLET_SOPHIA, MockIds.BOOKING_11);
            db.execute("UPDATE wallets SET balance = 790.01 WHERE walletId = ?", MockIds.WALLET_SOPHIA);
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

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

    private static void assertUnchanged(MockDbFixture db, long transactionCount, String bookingStatus)
            throws Exception {
        assertEquals(transactionCount, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"));
        assertEquals("UNDER_REVIEW", db.scalarString(
                "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
        assertEquals(bookingStatus, db.scalarString(
                "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_11));
        assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                + "WHERE actionType = 'TICKET_RESOLVED' AND entityId = ?", MockIds.TICKET_3));
    }
}
