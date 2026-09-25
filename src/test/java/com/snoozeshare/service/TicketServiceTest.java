package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.service.impl.TicketServiceImpl;
import com.snoozeshare.service.requests.ResolutionRequest;
import com.snoozeshare.testsupport.Fakes;

class TicketServiceTest {

    private final Fakes.InMemoryTickets tickets = new Fakes.InMemoryTickets();
    private final Fakes.InMemoryCategories categories = new Fakes.InMemoryCategories();
    private final Fakes.RecordingAudit audit = new Fakes.RecordingAudit();
    private final Fakes.RecordingSettlement settlement = new Fakes.RecordingSettlement();
    private final UUID amy = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();
    private final UUID host = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private TicketService service;

    private static User user(UUID id, Role role, String name) {
        return new User(id, role, name, name + "@x.test", AccountStatus.ACTIVE, null,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static Booking booking(UUID id) {
        return new Booking(id, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 4), BookingStatus.CONFIRMED, new BigDecimal("70.00"),
                new BigDecimal("210.00"), Instant.parse("2026-08-25T10:00:00Z"), null, null);
    }

    private Ticket ticket(TicketStatus status, UUID assigned, RemedyType remedy, String createdAt) {
        return tickets.save(new Ticket(UUID.randomUUID(), bookingId, UUID.randomUUID(), Role.GUEST,
                "Cleanliness", "title", "description", remedy, null, status, assigned, null, null,
                Instant.parse(createdAt), null));
    }

    @BeforeEach
    void setUp() {
        var users = new Fakes.StubUsers().with(user(amy, Role.AGENT, "Amy"))
                .with(user(ben, Role.AGENT, "Ben")).with(user(host, Role.HOST, "Hugo"));
        BookingRepository bookings = new BookingRepository() {
            @Override
            public Optional<Booking> findById(UUID id) {
                return Optional.of(booking(id));
            }

            @Override
            public List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end) {
                return List.of();
            }

            @Override
            public List<Booking> findByGuest(UUID guestId) {
                return List.of();
            }

            @Override
            public List<Booking> findByHostPending(UUID hostId) {
                return List.of();
            }

            @Override
            public Booking save(Booking booking) {
                return booking;
            }
        };
        service = new TicketServiceImpl(tickets, categories, bookings, users, settlement, audit,
                Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void queueIsOldestFirstAndFiltersByAssignee() {
        Ticket newer = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-10T00:00:00Z");
        Ticket older = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-01T00:00:00Z");
        Ticket mine = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.OTHER, "2026-09-05T00:00:00Z");

        assertEquals(List.of(older.ticketId(), mine.ticketId(), newer.ticketId()), ids(
                service.queueForAgent(null, AssigneeFilter.ALL, amy)));
        assertEquals(List.of(older.ticketId(), newer.ticketId()), ids(
                service.queueForAgent(null, AssigneeFilter.UNASSIGNED, amy)));
        assertEquals(List.of(mine.ticketId()), ids(
                service.queueForAgent(null, AssigneeFilter.MINE, amy)));
        assertEquals(List.of(mine.ticketId()), ids(
                service.queueForAgent(TicketStatus.UNDER_REVIEW, AssigneeFilter.ALL, amy)));
    }

    @Test
    void assignToMeMovesAnOpenTicketUnderReviewAndAuditsIt() {
        Ticket open = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-01T00:00:00Z");

        Ticket assigned = service.assignToMe(open.ticketId(), amy);

        assertEquals(TicketStatus.UNDER_REVIEW, assigned.status());
        assertEquals(amy, assigned.assignedAgentId());
        assertEquals(List.of("TICKET_ASSIGNED"), audit.actions());
    }

    @Test
    void assignRejectsNonAgentsNonOpenTicketsAndTicketsTakenByAnotherAgent() {
        Ticket open = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-01T00:00:00Z");
        Ticket taken = ticket(TicketStatus.OPEN, ben, RemedyType.OTHER, "2026-09-02T00:00:00Z");
        Ticket reviewing = ticket(TicketStatus.UNDER_REVIEW, ben, RemedyType.OTHER, "2026-09-03T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> service.assignToMe(open.ticketId(), host));
        assertThrows(IllegalStateException.class, () -> service.assignToMe(taken.ticketId(), amy));
        assertThrows(IllegalStateException.class, () -> service.assignToMe(reviewing.ticketId(), amy));
        assertThrows(IllegalArgumentException.class, () -> service.assignToMe(UUID.randomUUID(), amy));
        assertTrue(audit.actions().isEmpty());
    }

    @Test
    void notesAreAppendedWithAuthorAndTimestampOnlyByTheAssignedAgent() {
        Ticket mine = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.OTHER, "2026-09-01T00:00:00Z");

        service.addAgentNote(mine.ticketId(), "Requested photos", amy);
        Ticket updated = service.addAgentNote(mine.ticketId(), "Photos received", amy);

        assertEquals("[2026-09-25T04:00:00Z] Amy: Requested photos\n"
                + "[2026-09-25T04:00:00Z] Amy: Photos received", updated.agentNotes());
        assertThrows(IllegalStateException.class, () -> service.addAgentNote(mine.ticketId(), "x", ben));
        assertThrows(IllegalArgumentException.class, () -> service.addAgentNote(mine.ticketId(), " ", amy));
    }

    @Test
    void notesRequireAnUnderReviewTicket() {
        Ticket open = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> service.addAgentNote(open.ticketId(), "x", amy));
    }

    @Test
    void acceptDerivesTheRefundFromTheRequestedRemedy() {
        Ticket full = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.FULL_REFUND, "2026-09-01T00:00:00Z");
        Ticket payout = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.HOST_PAYOUT, "2026-09-01T00:00:00Z");

        service.resolve(full.ticketId(), new ResolutionRequest(ResolutionMode.ACCEPT, null, "ok"), amy);
        assertEquals(0, new BigDecimal("210.00").compareTo(settlement.refund()));
        assertEquals(ResolutionMode.ACCEPT, settlement.mode());

        service.resolve(payout.ticketId(), new ResolutionRequest(ResolutionMode.ACCEPT, null, "ok"), amy);
        assertEquals(0, BigDecimal.ZERO.compareTo(settlement.refund()));
    }

    @Test
    void acceptOfAPartialOrOtherRequestNeedsAnAmount() {
        Ticket partial = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.PARTIAL_REFUND, "2026-09-01T00:00:00Z");
        Ticket other = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.OTHER, "2026-09-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> service.resolve(partial.ticketId(),
                new ResolutionRequest(ResolutionMode.ACCEPT, null, "ok"), amy));
        assertEquals(0, settlement.calls());

        service.resolve(other.ticketId(),
                new ResolutionRequest(ResolutionMode.ACCEPT, new BigDecimal("50"), "ok"), amy);
        assertEquals(0, new BigDecimal("50").compareTo(settlement.refund()));
    }

    @Test
    void rejectAlwaysRefundsNothingAndManualNeedsAnAmount() {
        Ticket t = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.FULL_REFUND, "2026-09-01T00:00:00Z");

        service.resolve(t.ticketId(),
                new ResolutionRequest(ResolutionMode.REJECT, new BigDecimal("99"), "no"), amy);
        assertEquals(0, BigDecimal.ZERO.compareTo(settlement.refund()));
        assertEquals("no", settlement.reason());

        assertThrows(IllegalArgumentException.class, () -> service.resolve(t.ticketId(),
                new ResolutionRequest(ResolutionMode.MANUAL, null, "x"), amy));
        service.resolve(t.ticketId(),
                new ResolutionRequest(ResolutionMode.MANUAL, new BigDecimal("12.34"), "custom"), amy);
        assertEquals(0, new BigDecimal("12.34").compareTo(settlement.refund()));
    }

    @Test
    void resolveRejectsNonAgents() {
        Ticket t = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.FULL_REFUND, "2026-09-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> service.resolve(t.ticketId(),
                new ResolutionRequest(ResolutionMode.REJECT, null, "x"), host));
        assertEquals(0, settlement.calls());
    }

    @Test
    void fileTicketAndHostResponseAreNotOwnedByW10() {
        assertThrows(UnsupportedOperationException.class, () ->
                service.fileTicket(null, host, Role.HOST));
        assertThrows(UnsupportedOperationException.class, () ->
                service.addHostResponse(UUID.randomUUID(), "x", host));
    }

    private static List<UUID> ids(List<Ticket> list) {
        return list.stream().map(Ticket::ticketId).toList();
    }
}
