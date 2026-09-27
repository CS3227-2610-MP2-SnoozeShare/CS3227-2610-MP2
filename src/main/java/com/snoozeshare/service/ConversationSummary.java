package com.snoozeshare.service;

import java.util.UUID;

import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;

/**
 * One inbox row for a guest or host: a ticket thread they are a party to. {@code lastMessage} is null
 * until someone posts; {@code open} is false once the ticket is resolved (the thread is read-only).
 */
public record ConversationSummary(
        UUID ticketId,
        ThreadChannel channel,
        String ticketLabel,
        String ticketTitle,
        String listingTitle,
        String counterpartName,
        Message lastMessage,
        int unreadCount,
        boolean open
) {
}
