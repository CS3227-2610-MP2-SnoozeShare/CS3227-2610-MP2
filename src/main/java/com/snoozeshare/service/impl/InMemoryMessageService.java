package com.snoozeshare.service.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.service.MessageService;

/**
 * Temporary, session-only chat. Workstream W13 replaces it with a persistent implementation.
 */
public final class InMemoryMessageService implements MessageService {

    private final Clock clock;
    private final Map<String, List<Message>> threads = new ConcurrentHashMap<>();

    public InMemoryMessageService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<Message> thread(UUID ticketId, ThreadChannel channel) {
        return Collections.unmodifiableList(new ArrayList<>(
                threads.getOrDefault(key(ticketId, channel), List.of())));
    }

    @Override
    public Message post(UUID ticketId, ThreadChannel channel, UUID authorId, Role authorRole,
                        String body) {
        DomainValidation.requireText(body, "body");
        if (authorRole == Role.GUEST && channel != ThreadChannel.GUEST
                || authorRole == Role.HOST && channel != ThreadChannel.HOST) {
            throw new IllegalArgumentException("Author cannot post in this thread");
        }
        Message message = new Message(UUID.randomUUID(), ticketId, channel, authorId, authorRole,
                body.trim(), clock.instant());
        threads.computeIfAbsent(key(ticketId, channel), unused -> new CopyOnWriteArrayList<>())
                .add(message);
        return message;
    }

    private static String key(UUID ticketId, ThreadChannel channel) {
        return ticketId + "|" + channel;
    }
}
