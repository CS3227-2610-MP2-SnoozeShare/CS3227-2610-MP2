package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.statemachine.TicketStateMachine;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.TicketCategoryRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.DisputeSettlementService;
import com.snoozeshare.service.TicketService;
import com.snoozeshare.service.requests.NewTicketRequest;
import com.snoozeshare.service.requests.ResolutionRequest;

public final class TicketServiceImpl implements TicketService {

    private final TicketRepository tickets;
    private final TicketCategoryRepository categories;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final DisputeSettlementService settlement;
    private final AuditService audit;
    private final Clock clock;

    public TicketServiceImpl(TicketRepository tickets, TicketCategoryRepository categories,
                             BookingRepository bookings, UserRepository users,
                             DisputeSettlementService settlement, AuditService audit, Clock clock) {
        this.tickets = tickets;
        this.categories = categories;
        this.bookings = bookings;
        this.users = users;
        this.settlement = settlement;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole) {
        throw new UnsupportedOperationException("Owned by W4");
    }

    @Override
    public List<TicketCategory> listCategories() {
        return categories.findActive();
    }

    @Override
    public List<TicketCategory> listAllCategories() {
        return categories.findAll();
    }

    @Override
    public TicketCategory createCategory(String label, UUID agentId) {
        requireAgent(agentId);
        String clean = DomainValidation.requireText(label, "label").trim();
        if (categories.labelInUse(clean, null)) {
            throw new IllegalArgumentException("Category label already exists");
        }
        TicketCategory created = categories.save(new TicketCategory(UUID.randomUUID(), clean, true));
        audit.record(agentId, "TICKET_CATEGORY_CREATED", "TicketCategory", created.categoryId(), null,
                created);
        return created;
    }

    @Override
    public TicketCategory renameCategory(UUID categoryId, String label, UUID agentId) {
        requireAgent(agentId);
        TicketCategory existing = loadCategory(categoryId);
        String clean = DomainValidation.requireText(label, "label").trim();
        if (categories.labelInUse(clean, categoryId)) {
            throw new IllegalArgumentException("Category label already exists");
        }
        TicketCategory renamed = categories.save(
                new TicketCategory(categoryId, clean, existing.active()));
        audit.record(agentId, "TICKET_CATEGORY_RENAMED", "TicketCategory", categoryId, existing, renamed);
        return renamed;
    }

    @Override
    public TicketCategory setCategoryActive(UUID categoryId, boolean active, UUID agentId) {
        requireAgent(agentId);
        TicketCategory existing = loadCategory(categoryId);
        TicketCategory updated = categories.save(
                new TicketCategory(categoryId, existing.label(), active));
        audit.record(agentId, "TICKET_CATEGORY_TOGGLED", "TicketCategory", categoryId, existing, updated);
        return updated;
    }

    @Override
    public List<Ticket> queueForAgent(TicketStatus statusFilter, AssigneeFilter assignee, UUID agentId) {
        return tickets.findQueue(statusFilter, assignee, agentId);
    }

    @Override
    public Ticket assignToMe(UUID ticketId, UUID agentId) {
        requireAgent(agentId);
        Ticket ticket = loadTicket(ticketId);
        if (ticket.status() != TicketStatus.OPEN || !TicketStateMachine.canTransition(
                ticket.status(), TicketStatus.UNDER_REVIEW, Role.AGENT)) {
            throw new IllegalStateException("Ticket is not open");
        }
        if (ticket.assignedAgentId() != null && !agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is already assigned to another agent");
        }
        Ticket updated = tickets.save(with(ticket, TicketStatus.UNDER_REVIEW, agentId,
                ticket.agentNotes()));
        audit.record(agentId, "TICKET_ASSIGNED", "Ticket", ticketId, ticket.status(), updated.status());
        return updated;
    }

    @Override
    public Ticket saveNotes(UUID ticketId, String notes, UUID agentId) {
        requireAgent(agentId);
        Ticket ticket = loadTicket(ticketId);
        if (ticket.status() != TicketStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Notes can only be saved while the ticket is under review");
        }
        if (!agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is not assigned to this agent");
        }
        String text = notes == null ? "" : notes;
        Ticket updated = tickets.save(with(ticket, ticket.status(), ticket.assignedAgentId(), text));
        audit.record(agentId, "TICKET_NOTE_SAVED", "Ticket", ticketId, ticket.agentNotes(), text);
        return updated;
    }

    @Override
    public Ticket unassign(UUID ticketId, UUID agentId) {
        requireAgent(agentId);
        Ticket ticket = loadTicket(ticketId);
        if (ticket.status() != TicketStatus.UNDER_REVIEW || !TicketStateMachine.canTransition(
                ticket.status(), TicketStatus.OPEN, Role.AGENT)) {
            throw new IllegalStateException("Ticket is not under review");
        }
        if (!agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is not assigned to this agent");
        }
        Ticket updated = tickets.save(with(ticket, TicketStatus.OPEN, null, ticket.agentNotes()));
        audit.record(agentId, "TICKET_UNASSIGNED", "Ticket", ticketId, ticket.status(), updated.status());
        return updated;
    }

    @Override
    @Deprecated
    public Ticket addHostResponse(UUID ticketId, String responseText, UUID hostId) {
        throw new UnsupportedOperationException("Superseded by MessageService");
    }

    @Override
    public Ticket resolve(UUID ticketId, ResolutionRequest request, UUID agentId) {
        requireAgent(agentId);
        Ticket ticket = loadTicket(ticketId);
        Booking booking = bookings.findById(ticket.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        BigDecimal refund = switch (request.mode()) {
            case REJECT -> BigDecimal.ZERO;
            case MANUAL -> requireAmount(request.guestRefund());
            case ACCEPT -> switch (ticket.requestedRemedy()) {
                case FULL_REFUND -> booking.totalAmount();
                case HOST_PAYOUT -> BigDecimal.ZERO;
                case PARTIAL_REFUND, OTHER -> requirePositive(request.guestRefund());
            };
        };
        return settlement.settle(ticketId, request.mode(), refund, agentId, request.reason()).ticket();
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Guest refund is required");
        }
        return amount;
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        if (requireAmount(amount).signum() <= 0) {
            throw new IllegalArgumentException(
                    "Accept needs a refund above zero; use Reject or Manual adjustment for a zero refund");
        }
        return amount;
    }

    private User requireAgent(UUID agentId) {
        User user = agentId == null ? null : users.findById(agentId).orElse(null);
        AuthorizationService.requireRole(Role.AGENT, user);
        return user;
    }

    private Ticket loadTicket(UUID ticketId) {
        return tickets.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket does not exist"));
    }

    private TicketCategory loadCategory(UUID categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category does not exist"));
    }

    private static Ticket with(Ticket t, TicketStatus status, UUID assignedAgentId, String agentNotes) {
        return new Ticket(t.ticketId(), t.bookingId(), t.raisedByUserId(), t.raisedByRole(), t.category(),
                t.title(), t.description(), t.requestedRemedy(), t.supportingText(), status,
                assignedAgentId, agentNotes, t.resolutionReason(), t.createdAt(), t.resolvedAt());
    }
}
