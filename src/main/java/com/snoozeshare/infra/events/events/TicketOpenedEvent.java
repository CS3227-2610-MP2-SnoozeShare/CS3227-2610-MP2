package com.snoozeshare.infra.events.events;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.infra.events.DomainEvent;

public record TicketOpenedEvent(UUID ticketId, UUID actorId, Instant occurredAt,
                                UUID eventId) implements DomainEvent {
    public TicketOpenedEvent(UUID ticketId, UUID actorId, Instant occurredAt) {
        this(ticketId, actorId, occurredAt, UUID.randomUUID());
    }
}
