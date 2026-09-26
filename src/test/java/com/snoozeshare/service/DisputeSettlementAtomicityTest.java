package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.events.DomainEvent;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.testsupport.FailingAuditService;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeSettlementAtomicityTest {

    /** Delegates to the real repository but fails on the Nth save, simulating a mid-flight crash. */
    private static final class FailingOnSave implements WalletTransactionRepository {
        private final WalletTransactionRepository delegate;
        private final int failingSave;
        private int saves;

        FailingOnSave(WalletTransactionRepository delegate, int failingSave) {
            this.delegate = delegate;
            this.failingSave = failingSave;
        }

        @Override
        public WalletTransaction save(WalletTransaction transaction) {
            saves++;
            if (saves == failingSave) {
                throw new IllegalStateException("Injected failure");
            }
            return delegate.save(transaction);
        }

        @Override
        public List<WalletTransaction> findByWalletId(UUID walletId) {
            return delegate.findByWalletId(walletId);
        }

        @Override
        public List<WalletTransaction> findByBookingId(UUID bookingId) {
            return delegate.findByBookingId(bookingId);
        }
    }

    @Test
    void aFailureAfterTheGuestRowRollsEverythingBackAndPublishesNothing(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            InProcessEventBus bus = new InProcessEventBus();
            List<DomainEvent> events = new ArrayList<>();
            bus.subscribe(TicketResolvedEvent.class, events::add);
            bus.subscribe(WalletTransactionRecordedEvent.class, events::add);
            var service = SettlementFixtures.settlement(db, bus,
                    new FailingOnSave(new JdbcWalletTransactionRepository(db.connection()), 2));
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalStateException.class, () ->
                    service.settle(MockIds.TICKET_3, ResolutionMode.MANUAL, new BigDecimal("60.00"),
                            MockIds.AGENT_BEN, "split"));

            assertEquals(before, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"));
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
        // MANUAL 60.00 on ticket 3 writes 4 audit rows: ticket, booking, guest money, host money.
        for (int failingCall = 1; failingCall <= 4; failingCall++) {
            Path folder = Files.createDirectory(directory.resolve("call-" + failingCall));
            try (MockDbFixture db = MockDbFixture.open(folder)) {
                InProcessEventBus bus = new InProcessEventBus();
                List<DomainEvent> events = new ArrayList<>();
                bus.subscribe(TicketResolvedEvent.class, events::add);
                bus.subscribe(WalletTransactionRecordedEvent.class, events::add);
                var connection = db.connection();
                var realAudit = new AuditServiceImpl(new JdbcAuditLogRepository(connection),
                        new JdbcUserRepository(connection), SettlementFixtures.CLOCK);
                var service = SettlementFixtures.settlement(db, bus,
                        new JdbcWalletTransactionRepository(connection),
                        new FailingAuditService(realAudit, failingCall));
                long transactionsBefore = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");
                long auditBefore = db.scalarLong("SELECT COUNT(*) FROM audit_log");
                String label = "failing audit call " + failingCall;

                assertThrows(RuntimeException.class, () ->
                        service.settle(MockIds.TICKET_3, ResolutionMode.MANUAL, new BigDecimal("60.00"),
                                MockIds.AGENT_BEN, "split"), label);

                assertEquals(auditBefore, db.scalarLong("SELECT COUNT(*) FROM audit_log"), label);
                assertEquals(transactionsBefore,
                        db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"), label);
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
            var failing = SettlementFixtures.settlement(db, new InProcessEventBus(),
                    new FailingOnSave(new JdbcWalletTransactionRepository(db.connection()), 1));
            assertThrows(IllegalStateException.class, () ->
                    failing.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO,
                            MockIds.AGENT_BEN, "boom"));

            var healthy = SettlementFixtures.settlement(db, new InProcessEventBus(),
                    new JdbcWalletTransactionRepository(db.connection()));
            healthy.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO,
                    MockIds.AGENT_BEN, "retry succeeds");

            assertEquals("RESOLVED_REJECTED", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
            db.assertLedgerInvariant();
        }
    }
}
