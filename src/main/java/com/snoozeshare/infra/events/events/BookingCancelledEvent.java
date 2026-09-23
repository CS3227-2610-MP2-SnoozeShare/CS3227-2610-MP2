package com.snoozeshare.infra.events.events;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.infra.events.DomainEvent;

public record BookingCancelledEvent(UUID bookingId, UUID actorId, Instant occurredAt,
                                    UUID eventId) implements DomainEvent {
    public BookingCancelledEvent(UUID bookingId, UUID actorId, Instant occurredAt) {
        this(bookingId, actorId, occurredAt, UUID.randomUUID());
    }
}
