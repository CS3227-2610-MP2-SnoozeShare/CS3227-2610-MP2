package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;

/**
 * Ticket chat. Contract owned by workstream W13 (Messaging); W10 consumes it.
 */
public interface MessageService {
    List<Message> thread(UUID ticketId, ThreadChannel channel);

    Message post(UUID ticketId, ThreadChannel channel, UUID authorId, Role authorRole, String body);
}
