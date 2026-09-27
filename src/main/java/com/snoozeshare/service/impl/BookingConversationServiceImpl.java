package com.snoozeshare.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.BookingMessage;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.BookingMessagePostedEvent;
import com.snoozeshare.repository.BookingMessageRepository;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.BookingConversationService;
import com.snoozeshare.service.BookingConversationSummary;

public final class BookingConversationServiceImpl implements BookingConversationService {

    /** Days after check-out during which the chat stays writable: the dispute period. */
    static final int CHAT_DAYS_AFTER_CHECKOUT = 7;

    private final BookingMessageRepository messages;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final EventBus eventBus;
    private final Clock clock;

    public BookingConversationServiceImpl(BookingMessageRepository messages, BookingRepository bookings,
                                          PropertyRepository properties, UserRepository users, EventBus eventBus,
                                          Clock clock) {
        this.messages = messages;
        this.bookings = bookings;
        this.properties = properties;
        this.users = users;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    @Override
    public List<BookingMessage> thread(UUID bookingId, UUID viewerId, Role viewerRole) {
        requireParty(booking(bookingId), viewerId, viewerRole);
        return messages.findThread(bookingId);
    }

    @Override
    public BookingMessage post(UUID bookingId, UUID authorId, Role authorRole, String body) {
        DomainValidation.requireText(body, "body");
        Booking booking = booking(bookingId);
        requireParty(booking, authorId, authorRole);
        if (!isOpen(booking)) {
            throw new IllegalStateException("Chat is closed for this booking");
        }
        Instant now = clock.instant();
        BookingMessage saved = messages.save(new BookingMessage(UUID.randomUUID(), bookingId, authorId, authorRole,
                body.trim(), now));
        eventBus.publish(new BookingMessagePostedEvent(saved, now));
        return saved;
    }

    @Override
    public List<BookingConversationSummary> conversationsFor(UUID userId, Role role) {
        return partyBookings(userId, role).stream()
                .map(booking -> summarize(booking, userId, role))
                .filter(ranked -> ranked.summary().open() || ranked.summary().lastMessage() != null)
                .sorted(Comparator.comparing(Ranked::lastActivity).reversed())
                .map(Ranked::summary)
                .toList();
    }

    @Override
    public int unreadCount(UUID userId, Role role) {
        return partyBookings(userId, role).stream()
                .mapToInt(booking -> messages.countUnread(booking.bookingId(), userId))
                .sum();
    }

    @Override
    public void markRead(UUID bookingId, UUID userId, Role role) {
        requireParty(booking(bookingId), userId, role);
        messages.findLast(bookingId)
                .ifPresent(last -> messages.markRead(bookingId, userId, last.messageId()));
    }

    private List<Booking> partyBookings(UUID userId, Role role) {
        return switch (role) {
            case GUEST -> bookings.findByGuest(userId);
            case HOST -> bookings.findByHost(userId);
            default -> throw new IllegalArgumentException("Only guests and hosts have booking chats");
        };
    }

    private Ranked summarize(Booking booking, UUID userId, Role role) {
        Property property = property(booking);
        Optional<BookingMessage> last = messages.findLast(booking.bookingId());
        UUID counterpartId = role == Role.GUEST ? property.hostId() : booking.guestId();
        String counterpart = users.findById(counterpartId).map(User::displayName).orElse("Unknown user");
        BookingConversationSummary summary = new BookingConversationSummary(booking.bookingId(), property.title(),
                booking.startDate(), booking.endDate(), counterpart, last.orElse(null),
                messages.countUnread(booking.bookingId(), userId), isOpen(booking));
        Instant activity = last.map(BookingMessage::sentAt)
                .orElse(booking.decidedAt() != null ? booking.decidedAt() : booking.createdAt());
        return new Ranked(summary, activity);
    }

    private void requireParty(Booking booking, UUID userId, Role role) {
        User user = users.findById(userId)
                .filter(found -> found.role() == role)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user for this role"));
        boolean party = switch (role) {
            case GUEST -> booking.guestId().equals(user.userId());
            case HOST -> property(booking).hostId().equals(user.userId());
            default -> false;
        };
        if (!party) {
            throw new IllegalArgumentException("Not a participant in this booking chat");
        }
    }

    private boolean isOpen(Booking booking) {
        return booking.status() == BookingStatus.CONFIRMED
                && !LocalDate.now(clock).isAfter(booking.endDate().plusDays(CHAT_DAYS_AFTER_CHECKOUT));
    }

    private Booking booking(UUID bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
    }

    private Property property(Booking booking) {
        return properties.findById(booking.listingId())
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
    }

    private record Ranked(BookingConversationSummary summary, Instant lastActivity) {
    }
}
