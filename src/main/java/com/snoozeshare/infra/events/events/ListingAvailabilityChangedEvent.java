package com.snoozeshare.infra.events.events;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.infra.events.DomainEvent;

public record ListingAvailabilityChangedEvent(UUID propertyId, UUID actorId, Instant occurredAt,
                                              UUID eventId) implements DomainEvent {
    public ListingAvailabilityChangedEvent(UUID propertyId, UUID actorId, Instant occurredAt) {
        this(propertyId, actorId, occurredAt, UUID.randomUUID());
    }
}
