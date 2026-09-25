package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class JdbcTicketRepositoryTest {

    @Test
    void readsEveryFieldOfAnOpenMockTicket(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            Ticket ticket = new JdbcTicketRepository(db.connection())
                    .findById(MockIds.TICKET_2).orElseThrow();

            assertEquals(MockIds.BOOKING_9, ticket.bookingId());
            assertEquals(MockIds.GUEST_ARIA, ticket.raisedByUserId());
            assertEquals(Role.GUEST, ticket.raisedByRole());
            assertEquals("Property Mismatch", ticket.category());
            assertEquals("Missing promised beach access", ticket.title());
            assertEquals(RemedyType.OTHER, ticket.requestedRemedy());
            assertNull(ticket.supportingText());
            assertEquals(TicketStatus.OPEN, ticket.status());
            assertNull(ticket.assignedAgentId());
            assertNull(ticket.agentNotes());
            assertNull(ticket.resolutionReason());
            assertEquals(Instant.parse("2026-08-10T09:00:00Z"), ticket.createdAt());
            assertNull(ticket.resolvedAt());
        }
    }

    @Test
    void queueIsOldestFirst(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            List<UUID> ids = new JdbcTicketRepository(db.connection())
                    .findQueue(null, AssigneeFilter.ALL, null).stream().map(Ticket::ticketId).toList();

            assertEquals(List.of(MockIds.TICKET_4, MockIds.TICKET_2, MockIds.TICKET_1,
                    MockIds.TICKET_6, MockIds.TICKET_3, MockIds.TICKET_5), ids);
        }
    }

    @Test
    void queueFiltersByAssigneeAndStatus(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketRepository repository = new JdbcTicketRepository(db.connection());

            assertEquals(List.of(MockIds.TICKET_2), ids(repository.findQueue(
                    null, AssigneeFilter.UNASSIGNED, null)));
            assertEquals(List.of(MockIds.TICKET_3), ids(repository.findQueue(
                    null, AssigneeFilter.MINE, MockIds.AGENT_BEN)));
            assertEquals(List.of(MockIds.TICKET_6, MockIds.TICKET_5), ids(repository.findQueue(
                    null, AssigneeFilter.MINE, MockIds.AGENT_CHEN)));
            assertEquals(List.of(MockIds.TICKET_2), ids(repository.findQueue(
                    TicketStatus.OPEN, AssigneeFilter.ALL, null)));
            assertEquals(List.of(MockIds.TICKET_3), ids(repository.findQueue(
                    TicketStatus.UNDER_REVIEW, AssigneeFilter.ALL, null)));
        }
    }

    @Test
    void mineWithoutAnAgentIdIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketRepository repository = new JdbcTicketRepository(db.connection());

            assertThrows(IllegalArgumentException.class,
                    () -> repository.findQueue(null, AssigneeFilter.MINE, null));
        }
    }

    @Test
    void saveUpdatesStatusAssigneeNotesAndResolution(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketRepository repository = new JdbcTicketRepository(db.connection());
            Ticket open = repository.findById(MockIds.TICKET_2).orElseThrow();
            Instant resolvedAt = Instant.parse("2026-09-25T04:00:00Z");

            repository.save(new Ticket(open.ticketId(), open.bookingId(), open.raisedByUserId(),
                    open.raisedByRole(), open.category(), open.title(), open.description(),
                    open.requestedRemedy(), open.supportingText(), TicketStatus.RESOLVED_APPROVED,
                    MockIds.AGENT_AMY, "checked photos", "gate was locked", open.createdAt(), resolvedAt));

            Ticket reread = repository.findById(MockIds.TICKET_2).orElseThrow();
            assertEquals(TicketStatus.RESOLVED_APPROVED, reread.status());
            assertEquals(MockIds.AGENT_AMY, reread.assignedAgentId());
            assertEquals("checked photos", reread.agentNotes());
            assertEquals("gate was locked", reread.resolutionReason());
            assertEquals(resolvedAt, reread.resolvedAt());
            assertEquals(open.title(), reread.title());
        }
    }

    private static List<UUID> ids(List<Ticket> tickets) {
        return tickets.stream().map(Ticket::ticketId).toList();
    }
}
