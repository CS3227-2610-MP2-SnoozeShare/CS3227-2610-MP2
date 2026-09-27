package com.snoozeshare.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.MessagePostedEvent;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.MessageRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.ConversationSummary;
import com.snoozeshare.service.MessageService;

public final class MessageServiceImpl implements MessageService {

    private static final String SUPPORT_TEAM = "Support team";

    private final MessageRepository messages;
    private final TicketRepository tickets;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final EventBus eventBus;
    private final Clock clock;

    public MessageServiceImpl(MessageRepository messages, TicketRepository tickets, BookingRepository bookings,
                              PropertyRepository properties, UserRepository users, EventBus eventBus,
                              Clock clock) {
        this.messages = messages;
        this.tickets = tickets;
        this.bookings = bookings;
        this.properties = properties;
        this.users = users;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    @Override
    public List<Message> thread(UUID ticketId, ThreadChannel channel, UUID viewerId, Role viewerRole) {
        Ticket ticket = ticket(ticketId);
        requireParty(ticket, channel, viewerId, viewerRole);
        return messages.findThread(ticketId, channel);
    }

    @Override
    public Message post(UUID ticketId, ThreadChannel channel, UUID authorId, Role authorRole, String body) {
        DomainValidation.requireText(body, "body");
        Ticket ticket = ticket(ticketId);
        requireParty(ticket, channel, authorId, authorRole);
        if (!isOpen(ticket)) {
            throw new IllegalStateException("Ticket is resolved");
        }
        Instant now = clock.instant();
        Message saved = messages.save(new Message(UUID.randomUUID(), ticketId, channel, authorId, authorRole,
                body.trim(), now));
        eventBus.publish(new MessagePostedEvent(saved, now));
        return saved;
    }

    @Override
    public List<ConversationSummary> conversationsFor(UUID userId, Role role) {
        ThreadChannel channel = channelOf(role);
        return tickets.findByParty(userId, role).stream()
                .map(ticket -> summarize(ticket, channel, userId, role))
                .sorted(Comparator.comparing(Ranked::lastActivity).reversed())
                .map(Ranked::summary)
                .toList();
    }

    @Override
    public int unreadCount(UUID userId, Role role) {
        ThreadChannel channel = channelOf(role);
        return tickets.findByParty(userId, role).stream()
                .mapToInt(ticket -> messages.countUnread(ticket.ticketId(), channel, userId))
                .sum();
    }

    @Override
    public void markRead(UUID ticketId, ThreadChannel channel, UUID userId, Role role) {
        requireParty(ticket(ticketId), channel, userId, role);
        messages.findLast(ticketId, channel)
                .ifPresent(last -> messages.markRead(ticketId, channel, userId, last.messageId()));
    }

    private Ranked summarize(Ticket ticket, ThreadChannel channel, UUID userId, Role role) {
        Booking booking = booking(ticket);
        Property property = property(booking);
        Optional<Message> last = messages.findLast(ticket.ticketId(), channel);
        String counterpart = ticket.assignedAgentId() == null ? SUPPORT_TEAM
                : users.findById(ticket.assignedAgentId()).map(User::displayName).orElse(SUPPORT_TEAM);
        ConversationSummary summary = new ConversationSummary(ticket.ticketId(), channel,
                DisputeQueryServiceImpl.label(ticket.ticketId()), ticket.title(), property.title(), counterpart,
                last.orElse(null), messages.countUnread(ticket.ticketId(), channel, userId), isOpen(ticket));
        return new Ranked(summary, last.map(Message::sentAt).orElse(ticket.createdAt()));
    }

    private void requireParty(Ticket ticket, ThreadChannel channel, UUID userId, Role role) {
        User user = users.findById(userId)
                .filter(found -> found.role() == role)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user for this role"));
        if (role == Role.AGENT) {
            return;
        }
        Booking booking = booking(ticket);
        boolean party = switch (role) {
            case GUEST -> channel == ThreadChannel.GUEST && booking.guestId().equals(user.userId());
            case HOST -> channel == ThreadChannel.HOST && property(booking).hostId().equals(user.userId());
            default -> false;
        };
        if (!party) {
            throw new IllegalArgumentException("Not a participant in this thread");
        }
    }

    private static ThreadChannel channelOf(Role role) {
        return switch (role) {
            case GUEST -> ThreadChannel.GUEST;
            case HOST -> ThreadChannel.HOST;
            default -> throw new IllegalArgumentException("Only guests and hosts have a conversation inbox");
        };
    }

    private static boolean isOpen(Ticket ticket) {
        return ticket.status() == TicketStatus.OPEN || ticket.status() == TicketStatus.IN_REVIEW;
    }

    private Ticket ticket(UUID ticketId) {
        return tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("Ticket does not exist"));
    }

    private Booking booking(Ticket ticket) {
        return bookings.findById(ticket.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
    }

    private Property property(Booking booking) {
        return properties.findById(booking.listingId())
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
    }

    private record Ranked(ConversationSummary summary, Instant lastActivity) {
    }
}
