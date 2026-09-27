package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;

/**
 * Ticket chat (F12). Each ticket has a GUEST thread and a HOST thread; the guest may use only the first,
 * the host only the second, any agent both. Every call checks the caller against the ticket's booking.
 */
public interface MessageService {
    /** Oldest first. The viewer must be a party of the channel or an agent, else IllegalArgumentException. */
    List<Message> thread(UUID ticketId, ThreadChannel channel, UUID viewerId, Role viewerRole);

    /**
     * Stores and publishes a message. A blank body, a non-party author or a channel the role may not use
     * throws IllegalArgumentException; a resolved ticket throws IllegalStateException (threads are read-only).
     */
    Message post(UUID ticketId, ThreadChannel channel, UUID authorId, Role authorRole, String body);

    /** Guest or host inbox: one entry per ticket the user is a party to, most recent activity first. */
    List<ConversationSummary> conversationsFor(UUID userId, Role role);

    /** Total unread messages across the user's conversations, for a tab badge. */
    int unreadCount(UUID userId, Role role);

    /** Marks the thread read up to its newest message; a no-op on an empty thread. */
    void markRead(UUID ticketId, ThreadChannel channel, UUID userId, Role role);
}
