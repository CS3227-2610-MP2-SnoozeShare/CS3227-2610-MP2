package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.statemachine.TicketStateMachine;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.TicketOpenedEvent;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.TicketCategoryRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.AuditRecord;
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
    private final EventBus eventBus;

    public TicketServiceImpl(TicketRepository tickets, TicketCategoryRepository categories,
                             BookingRepository bookings, UserRepository users,
                             DisputeSettlementService settlement, AuditService audit,
                             Clock clock, EventBus eventBus) {
        this.tickets = tickets;
        this.categories = categories;
        this.bookings = bookings;
        this.users = users;
        this.settlement = settlement;
        this.audit = audit;
        this.clock = clock;
        this.eventBus = eventBus;
    }

    public TicketServiceImpl(TicketRepository tickets, TicketCategoryRepository categories,
                             BookingRepository bookings, UserRepository users,
                             DisputeSettlementService settlement, AuditService audit,
                             Clock clock) {
        this(tickets, categories, bookings, users, settlement, audit, clock,
                new com.snoozeshare.infra.events.InProcessEventBus());
    }

    @Override
    public List<Ticket> myTickets(UUID userId) {
        return tickets.findByRaisedByUserId(userId);
    }

    @Override
    public Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole) {
        Booking booking = bookings.findById(request.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));

        if (!booking.guestId().equals(raisedByUserId)) {
            throw new IllegalArgumentException("Only the booking guest may file a ticket");
        }

        LocalDate today = LocalDate.now(clock);
        boolean stayEnded = !booking.endDate().isAfter(today);
        boolean statusEligible = booking.status() == BookingStatus.CONFIRMED
                || booking.status() == BookingStatus.COMPLETED;
        if (!statusEligible || (booking.status() == BookingStatus.CONFIRMED && !stayEnded)) {
            throw new IllegalStateException("Booking is not eligible for a dispute");
        }

        if (today.isAfter(booking.endDate().plusDays(7))) {
            throw new IllegalStateException("Dispute window has closed (7 days after stay end)");
        }

        String category = DomainValidation.requireText(request.category(), "category");
        boolean validCategory = categories.findActive().stream()
                .anyMatch(c -> c.label().equalsIgnoreCase(category.trim()));
        if (!validCategory) {
            throw new IllegalArgumentException("Invalid ticket category");
        }

        String title = DomainValidation.requireText(request.title(), "title");
        String description = DomainValidation.requireText(request.description(), "description");

        boolean duplicate = tickets.findByRaisedByUserId(raisedByUserId).stream()
                .anyMatch(t -> t.bookingId().equals(request.bookingId()));
        if (duplicate) {
            throw new IllegalStateException("A ticket already exists for this booking");
        }

        Instant now = clock.instant();
        Ticket ticket = new Ticket(UUID.randomUUID(), request.bookingId(), raisedByUserId,
                raisedByRole, category.trim(), title.trim(), description.trim(),
                request.requestedRemedy(), request.supportingText(),
                TicketStatus.OPEN, null, null, null, now, null);

        Ticket saved = tickets.save(ticket);
        audit.record(AuditRecord.builder(raisedByUserId, AuditAction.TICKET_OPENED, "Ticket", saved.ticketId())
                .subject(raisedByUserId).booking(saved.bookingId()).ticket(saved.ticketId()).at(now).build());
        eventBus.publish(new TicketOpenedEvent(saved.ticketId(), raisedByUserId, now));
        return saved;
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
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_CATEGORY_CREATED, "TicketCategory",
                        created.categoryId())
                .status(null, "ACTIVE").reason("Category created: " + created.label()).build());
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
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_CATEGORY_RENAMED, "TicketCategory",
                        categoryId)
                .reason("Renamed \"" + existing.label() + "\" to \"" + renamed.label() + "\"").build());
        return renamed;
    }

    @Override
    public TicketCategory setCategoryActive(UUID categoryId, boolean active, UUID agentId) {
        requireAgent(agentId);
        TicketCategory existing = loadCategory(categoryId);
        TicketCategory updated = categories.save(
                new TicketCategory(categoryId, existing.label(), active));
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_CATEGORY_TOGGLED, "TicketCategory",
                        categoryId)
                .status(existing.active() ? "ACTIVE" : "INACTIVE", updated.active() ? "ACTIVE" : "INACTIVE")
                .reason("Category: " + existing.label()).build());
        return updated;
    }

    @Override
    public void deleteCategory(UUID categoryId, UUID agentId) {
        requireAgent(agentId);
        TicketCategory existing = loadCategory(categoryId);
        if (tickets.existsByCategory(existing.label())) {
            throw new IllegalStateException("Category is in use by tickets; deactivate it instead");
        }
        categories.deleteById(categoryId);
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_CATEGORY_DELETED, "TicketCategory",
                        categoryId)
                .status(existing.active() ? "ACTIVE" : "INACTIVE", null)
                .reason("Category deleted: " + existing.label()).build());
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
                ticket.status(), TicketStatus.IN_REVIEW, Role.AGENT)) {
            throw new IllegalStateException("Ticket is not open");
        }
        if (ticket.assignedAgentId() != null && !agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is already assigned to another agent");
        }
        Ticket updated = tickets.save(with(ticket, TicketStatus.IN_REVIEW, agentId,
                ticket.agentNotes()));
        audit.record(ticketRow(agentId, AuditAction.TICKET_ASSIGNED, ticket)
                .status(ticket.status(), updated.status()).reason("Assigned for review").build());
        return updated;
    }

    @Override
    public Ticket saveNotes(UUID ticketId, String notes, UUID agentId) {
        requireAgent(agentId);
        Ticket ticket = loadTicket(ticketId);
        if (ticket.status() != TicketStatus.IN_REVIEW) {
            throw new IllegalStateException("Notes can only be saved while the ticket is in review");
        }
        if (!agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is not assigned to this agent");
        }
        String text = notes == null ? "" : notes;
        Ticket updated = tickets.save(with(ticket, ticket.status(), ticket.assignedAgentId(), text));
        audit.record(ticketRow(agentId, AuditAction.TICKET_NOTE_SAVED, ticket)
                .reason("Internal note updated").build());
        return updated;
    }

    @Override
    public Ticket unassign(UUID ticketId, UUID agentId) {
        requireAgent(agentId);
        Ticket ticket = loadTicket(ticketId);
        if (ticket.status() != TicketStatus.IN_REVIEW || !TicketStateMachine.canTransition(
                ticket.status(), TicketStatus.OPEN, Role.AGENT)) {
            throw new IllegalStateException("Ticket is not in review");
        }
        if (!agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is not assigned to this agent");
        }
        Ticket updated = tickets.save(with(ticket, TicketStatus.OPEN, null, ticket.agentNotes()));
        audit.record(ticketRow(agentId, AuditAction.TICKET_UNASSIGNED, ticket)
                .status(ticket.status(), updated.status()).reason("Returned to the queue").build());
        return updated;
    }

    private static AuditRecord.Builder ticketRow(UUID agentId, AuditAction action, Ticket ticket) {
        return AuditRecord.builder(agentId, action, "Ticket", ticket.ticketId())
                .subject(ticket.raisedByUserId()).booking(ticket.bookingId()).ticket(ticket.ticketId());
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
