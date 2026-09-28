package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.settlement.EscrowPolicy;
import com.snoozeshare.domain.settlement.SettlementBreakdown;
import com.snoozeshare.domain.settlement.SettlementCalculator;
import com.snoozeshare.domain.statemachine.BookingStateMachine;
import com.snoozeshare.domain.statemachine.TicketStateMachine;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.LedgerRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.DisputeSettlementService;
import com.snoozeshare.service.Settlement;

/**
 * Settles the whole held escrow when an agent resolves a dispute (C17, C20). The ticket and booking status
 * changes and every money row (guest refund, host payout, platform fee) are written in one transaction
 * through the {@link LedgerWriter}; events are published only after it commits.
 */
public final class DisputeSettlementServiceImpl implements DisputeSettlementService {

    private final Connection connection;
    private final TicketRepository tickets;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final LedgerWriter ledger;
    private final LedgerRepository ledgerEntries;
    private final AuditService audit;
    private final EventBus eventBus;
    private final Clock clock;

    public DisputeSettlementServiceImpl(Connection connection, TicketRepository tickets,
                                        BookingRepository bookings, PropertyRepository properties,
                                        UserRepository users, WalletRepository wallets,
                                        LedgerWriter ledger, LedgerRepository ledgerEntries,
                                        AuditService audit, EventBus eventBus, Clock clock) {
        this.connection = connection;
        this.tickets = tickets;
        this.bookings = bookings;
        this.properties = properties;
        this.users = users;
        this.wallets = wallets;
        this.ledger = ledger;
        this.ledgerEntries = ledgerEntries;
        this.audit = audit;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    @Override
    public Settlement settle(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId,
                             String reason) {
        DomainValidation.requireText(reason, "reason");
        User agent = agentId == null ? null : users.findById(agentId).orElse(null);
        AuthorizationService.requireRole(Role.AGENT, agent);
        Instant now = clock.instant();
        Settlement settlement;
        try {
            settlement = new TransactionManager(connection).inTransaction(
                    current -> apply(ticketId, mode, guestRefund, agentId, reason.trim(), now));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to settle dispute", exception);
        }
        eventBus.publish(new TicketResolvedEvent(ticketId, agentId, now));
        publish(settlement.guestTransaction());
        publish(settlement.hostTransaction());
        return settlement;
    }

    private Settlement apply(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId,
                             String reason, Instant now) {
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket does not exist"));
        if (ticket.status() != TicketStatus.IN_REVIEW) {
            throw new IllegalStateException("Ticket is not in review");
        }
        if (!agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is not assigned to this agent");
        }
        Booking booking = bookings.findById(ticket.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        if (booking.status() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking is not confirmed");
        }
        if (!EscrowPolicy.isHeld(ledgerEntries.entriesForBooking(booking.bookingId()))) {
            throw new IllegalStateException("Escrow is not held for this booking");
        }
        SettlementBreakdown split = SettlementCalculator.split(booking.totalAmount(), guestRefund);
        requireConsistent(mode, split, ticket);
        TicketStatus resolved = statusFor(mode, split);
        if (!TicketStateMachine.canTransition(ticket.status(), resolved, Role.AGENT)
                || !BookingStateMachine.canTransition(booking.status(), BookingStatus.COMPLETED,
                        Role.AGENT)) {
            throw new IllegalStateException("Transition is not allowed");
        }
        Wallet guestWallet = wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
        Property property = properties.findById(booking.listingId())
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
        Wallet hostWallet = wallets.findByUserId(property.hostId())
                .orElseThrow(() -> new IllegalArgumentException("Host wallet does not exist"));

        Ticket updatedTicket = tickets.save(new Ticket(ticket.ticketId(), ticket.bookingId(),
                ticket.raisedByUserId(), ticket.raisedByRole(), ticket.category(), ticket.title(),
                ticket.description(), ticket.requestedRemedy(), ticket.supportingText(), resolved,
                ticket.assignedAgentId(), ticket.agentNotes(), reason, ticket.createdAt(), now));
        Booking updatedBooking = bookings.save(new Booking(booking.bookingId(), booking.listingId(),
                booking.guestId(), booking.startDate(), booking.endDate(), BookingStatus.COMPLETED,
                booking.nightlyRateSnapshot(), booking.totalAmount(), booking.createdAt(),
                booking.decidedAt(), now));
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_RESOLVED, "Ticket", ticketId)
                .status(ticket.status(), resolved).reason(reason).subject(ticket.raisedByUserId())
                .booking(booking.bookingId()).ticket(ticketId).at(now).build());
        audit.record(AuditRecord.builder(agentId, AuditAction.BOOKING_COMPLETED, "Booking", booking.bookingId())
                .status(booking.status(), BookingStatus.COMPLETED).reason("Escrow settled by ticket resolution")
                .subject(booking.guestId()).booking(booking.bookingId()).ticket(ticketId).at(now).build());

        // Money rows (and the host payout's PLATFORM_FEE row) are written by the ledger, in this transaction.
        WalletTransaction guestTransaction = null;
        WalletTransaction hostTransaction = null;
        if (split.guestRefund().signum() > 0) {
            WalletTransactionType type = mode == ResolutionMode.MANUAL
                    ? WalletTransactionType.AGENT_OVERRIDE : WalletTransactionType.TICKET_REMEDY;
            guestTransaction = ledger.post(guestWallet.walletId(), type, split.guestRefund(), agentId,
                    booking.bookingId(), ticket.ticketId(), null, now);
        }
        if (split.hostGross().signum() > 0) {
            hostTransaction = ledger.postPayout(hostWallet.walletId(), split.hostNet(), split.fee(), agentId,
                    booking.bookingId(), ticket.ticketId(), now);
        }
        return new Settlement(updatedTicket, updatedBooking, split, guestTransaction, hostTransaction);
    }

    private static void requireConsistent(ResolutionMode mode, SettlementBreakdown split, Ticket ticket) {
        boolean refunds = split.guestRefund().signum() > 0;
        if (mode == ResolutionMode.REJECT && refunds) {
            throw new IllegalArgumentException("Reject cannot refund the guest");
        }
        if (mode == ResolutionMode.ACCEPT && !refunds && ticket.requestedRemedy() != RemedyType.HOST_PAYOUT) {
            throw new IllegalArgumentException(
                    "Accept needs a refund above zero unless the requested remedy is a host payout");
        }
    }

    private static TicketStatus statusFor(ResolutionMode mode, SettlementBreakdown split) {
        return switch (mode) {
            case ACCEPT -> TicketStatus.RESOLVED_APPROVED;
            case REJECT -> TicketStatus.RESOLVED_REJECTED;
            case MANUAL -> split.guestRefund().signum() > 0
                    ? TicketStatus.RESOLVED_APPROVED : TicketStatus.RESOLVED_REJECTED;
        };
    }

    private void publish(WalletTransaction transaction) {
        if (transaction != null) {
            eventBus.publish(new WalletTransactionRecordedEvent(transaction.transactionId(),
                    transaction.walletId(), transaction.createdAt()));
        }
    }
}
