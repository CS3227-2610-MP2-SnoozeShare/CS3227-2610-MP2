package com.snoozeshare.infra.events.events;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.model.BookingMessage;
import com.snoozeshare.infra.events.DomainEvent;

/** A host and guest booking chat message was stored; published after the insert so open screens can refresh. */
public record BookingMessagePostedEvent(BookingMessage message, Instant occurredAt, UUID eventId)
        implements DomainEvent {
    public BookingMessagePostedEvent(BookingMessage message, Instant occurredAt) {
        this(message, occurredAt, UUID.randomUUID());
    }
}
