package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.DisputeQueryServiceImpl;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeQueryServiceTest {

    private static DisputeQueryServiceImpl service(MockDbFixture db) {
        var c = db.connection();
        return new DisputeQueryServiceImpl(new JdbcTicketRepository(c), new JdbcBookingRepository(c),
                new JdbcPropertyRepository(c), new JdbcUserRepository(c),
                new JdbcWalletTransactionRepository(c), SettlementFixtures.CLOCK);
    }

    @Test
    void queueIsOldestFirstWithNamesResolved(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            List<DisputeSummary> queue = service(db).queue(null, AssigneeFilter.ALL, MockIds.AGENT_AMY);

            assertEquals(6, queue.size());
            assertEquals(MockIds.TICKET_4, queue.get(0).ticketId());
            assertEquals(MockIds.TICKET_5, queue.get(5).ticketId());
            DisputeSummary open = queue.get(1);
            assertEquals(MockIds.TICKET_2, open.ticketId());
            assertEquals("#0002", open.ticketLabel());
            assertEquals("Missing promised beach access", open.title());
            assertEquals("Beachfront Bungalow", open.listingTitle());
            assertEquals("Aria Costa", open.guestName());
            assertEquals("Priya Nair", open.hostName());
            assertNull(open.assignedAgentName());
            assertEquals(TicketStatus.OPEN, open.status());
        }
    }

    @Test
    void queueHonoursTheAssigneeFilter(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeQueryServiceImpl service = service(db);

            assertEquals(List.of(MockIds.TICKET_2), service.queue(null, AssigneeFilter.UNASSIGNED,
                    MockIds.AGENT_AMY).stream().map(DisputeSummary::ticketId).toList());
            List<DisputeSummary> mine = service.queue(null, AssigneeFilter.MINE, MockIds.AGENT_BEN);
            assertEquals(1, mine.size());
            assertEquals("Ben Alvarez", mine.get(0).assignedAgentName());
        }
    }

    @Test
    void detailOfAHeldBookingShowsTheEscrowAndTheStayEndedPhase(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeDetail detail = service(db).detail(MockIds.TICKET_3);

            assertEquals(BookingStatus.CONFIRMED, detail.bookingStatus());
            assertTrue(detail.escrowHeld());
            assertEquals(0, new BigDecimal("210").compareTo(detail.escrowAmount()));
            assertEquals("Stay ended \u2014 escrow held", detail.phaseLabel());
            assertEquals("Sophia Rossi", detail.guestName());
            assertEquals("Diego Fernandez", detail.hostName());
            assertEquals("Modern Studio Near Metro", detail.listingTitle());
            assertEquals("Ben Alvarez", detail.assignedAgentName());
        }
    }

    @Test
    void detailOfASettledBookingShowsNoHeldEscrow(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeDetail detail = service(db).detail(MockIds.TICKET_1);

            assertFalse(detail.escrowHeld());
            assertEquals(BookingStatus.COMPLETED, detail.bookingStatus());
            assertEquals("Completed", detail.phaseLabel());
        }
    }

    @Test
    void anUnknownTicketIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeQueryServiceImpl service = service(db);

            assertThrows(IllegalArgumentException.class, () -> service.detail(UUID.randomUUID()));
        }
    }
}
