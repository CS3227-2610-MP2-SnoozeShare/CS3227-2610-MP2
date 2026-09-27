package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.BookingMessage;

/**
 * Private host and guest chat on a booking (F12, W13). Text only; messages cannot be edited or deleted.
 * The chat is writable from the moment the host confirms the booking until 7 days after check-out (the
 * dispute period) and read-only afterwards. Only the guest and host of the booking take part; agents do not.
 */
public interface BookingConversationService {
    /** Oldest first. The viewer must be the guest or host of the booking, else IllegalArgumentException. */
    List<BookingMessage> thread(UUID bookingId, UUID viewerId, Role viewerRole);

    /**
     * Stores and publishes a message. A blank body or a non-party author throws IllegalArgumentException; a
     * booking outside its chat window (not confirmed, or more than 7 days past check-out) throws
     * IllegalStateException.
     */
    BookingMessage post(UUID bookingId, UUID authorId, Role authorRole, String body);

    /**
     * Guest or host inbox: every booking of the user that is open or has messages, most recent activity first.
     * Agents are rejected.
     */
    List<BookingConversationSummary> conversationsFor(UUID userId, Role role);

    /** Total unread messages across the user's booking chats, for a tab badge. */
    int unreadCount(UUID userId, Role role);

    /** Marks the chat read up to its newest message; a no-op on an empty chat. */
    void markRead(UUID bookingId, UUID userId, Role role);
}
