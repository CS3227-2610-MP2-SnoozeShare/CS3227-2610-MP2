package com.snoozeshare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;

public interface MessageRepository {
    /** Oldest first, by insertion order rather than the timestamp text. */
    List<Message> findThread(UUID ticketId, ThreadChannel channel);

    Optional<Message> findLast(UUID ticketId, ThreadChannel channel);

    Message save(Message message);

    void markRead(UUID ticketId, ThreadChannel channel, UUID userId, UUID lastReadMessageId);

    /** Messages in the thread after the user's last-read one, excluding the user's own. */
    int countUnread(UUID ticketId, ThreadChannel channel, UUID userId);
}
