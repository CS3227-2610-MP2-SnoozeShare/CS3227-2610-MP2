package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketCategoryRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.TicketServiceImpl;
import com.snoozeshare.service.requests.ResolutionRequest;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class TicketServiceIntegrationTest {

    private static TicketServiceImpl service(MockDbFixture db) {
        var connection = db.connection();
        var settlement = SettlementFixtures.settlement(db, new InProcessEventBus(),
                new JdbcWalletTransactionRepository(connection));
        return new TicketServiceImpl(new JdbcTicketRepository(connection),
                new JdbcTicketCategoryRepository(connection), new JdbcBookingRepository(connection),
                new JdbcUserRepository(connection), settlement,
                new AuditServiceImpl(new JdbcAuditLogRepository(connection)), SettlementFixtures.CLOCK);
    }

    @Test
    void assignNoteAndAcceptAPartialRefundEndToEndOnTheMockDatabase(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            TicketServiceImpl service = service(db);

            Ticket assigned = service.assignToMe(MockIds.TICKET_2, MockIds.AGENT_AMY);
            assertEquals(TicketStatus.UNDER_REVIEW, assigned.status());
            service.saveNotes(MockIds.TICKET_2, "Requested gate photos", MockIds.AGENT_AMY);

            Ticket resolved = service.resolve(MockIds.TICKET_2,
                    new ResolutionRequest(ResolutionMode.ACCEPT, new BigDecimal("175.00"),
                            "Gate was locked"), MockIds.AGENT_AMY);

            assertEquals(TicketStatus.RESOLVED_APPROVED, resolved.status());
            assertTrue(resolved.agentNotes().contains("Requested gate photos"));
            assertEquals(0, new BigDecimal("700").compareTo(db.walletBalance(MockIds.WALLET_ARIA)));
            assertEquals(0, new BigDecimal("679").compareTo(db.walletBalance(MockIds.WALLET_PRIYA)));
            assertEquals("COMPLETED", db.scalarString(
                    "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_9));
            db.assertLedgerInvariant();
        }
    }

    @Test
    void deleteCategoryRemovesAnUnusedOneButRefusesOneFiledOnTicketsOnTheMockDatabase(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            TicketServiceImpl service = service(db);
            var noise = service.createCategory("Noise", MockIds.AGENT_AMY);

            service.deleteCategory(noise.categoryId(), MockIds.AGENT_AMY);
            IllegalStateException refused = assertThrows(IllegalStateException.class, () ->
                    service.deleteCategory(MockIds.CATEGORY_CLEANLINESS, MockIds.AGENT_AMY));

            assertEquals("Category is in use by tickets; deactivate it instead", refused.getMessage());
            assertEquals(6, service.listAllCategories().size());
            assertEquals(1, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE actionType = 'CATEGORY_DELETED'"));
        }
    }

    @Test
    void unassignPersistsAnOpenTicketWithNoAssigneeOnTheMockDatabase(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            TicketServiceImpl service = service(db);
            service.assignToMe(MockIds.TICKET_2, MockIds.AGENT_AMY);
            service.saveNotes(MockIds.TICKET_2, "keep me", MockIds.AGENT_AMY);

            Ticket released = service.unassign(MockIds.TICKET_2, MockIds.AGENT_AMY);

            assertEquals(TicketStatus.OPEN, released.status());
            assertEquals("OPEN", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_2));
            assertEquals(0L, db.scalarLong(
                    "SELECT COUNT(*) FROM tickets WHERE ticketId = '" + MockIds.TICKET_2
                            + "' AND assignedAgentId IS NOT NULL"));
            assertEquals(1L, db.scalarLong(
                    "SELECT COUNT(*) FROM audit_log WHERE actionType = 'TICKET_UNASSIGNED'"));
            assertEquals("keep me", db.scalarString(
                    "SELECT agentNotes FROM tickets WHERE ticketId = ?", MockIds.TICKET_2));
            assertEquals(TicketStatus.UNDER_REVIEW,
                    service.assignToMe(MockIds.TICKET_2, MockIds.AGENT_BEN).status());
        }
    }

    @Test
    void acceptingAnOtherRequestWithoutAnAmountChangesNothing(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            TicketServiceImpl service = service(db);
            service.assignToMe(MockIds.TICKET_2, MockIds.AGENT_AMY);
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalArgumentException.class, () -> service.resolve(MockIds.TICKET_2,
                    new ResolutionRequest(ResolutionMode.ACCEPT, null, "no amount"),
                    MockIds.AGENT_AMY));

            assertEquals(before, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"));
            assertEquals("UNDER_REVIEW", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_2));
        }
    }

    @Test
    void anotherAgentCannotTakeOrResolveATicketAlreadyUnderReview(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            TicketServiceImpl service = service(db);

            assertThrows(IllegalStateException.class, () ->
                    service.assignToMe(MockIds.TICKET_3, MockIds.AGENT_AMY));
            assertThrows(IllegalStateException.class, () -> service.resolve(MockIds.TICKET_3,
                    new ResolutionRequest(ResolutionMode.REJECT, null, "not mine"), MockIds.AGENT_AMY));
        }
    }
}
