package com.snoozeshare.infra.events.events;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.model.Message;
import com.snoozeshare.infra.events.DomainEvent;

/** A ticket chat message was stored; published after the insert so open chat screens can refresh. */
public record MessagePostedEvent(Message message, Instant occurredAt, UUID eventId) implements DomainEvent {
    public MessagePostedEvent(Message message, Instant occurredAt) {
        this(message, occurredAt, UUID.randomUUID());
    }
}
