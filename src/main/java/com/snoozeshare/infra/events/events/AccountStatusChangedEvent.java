package com.snoozeshare.infra.events.events;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.infra.events.DomainEvent;

/** An account was suspended or reactivated; published after the change commits so the Accounts screen refreshes. */
public record AccountStatusChangedEvent(UUID userId, AccountStatus newStatus, UUID actorId, Instant occurredAt,
                                        UUID eventId) implements DomainEvent {
    public AccountStatusChangedEvent(UUID userId, AccountStatus newStatus, UUID actorId, Instant occurredAt) {
        this(userId, newStatus, actorId, occurredAt, UUID.randomUUID());
    }
}
