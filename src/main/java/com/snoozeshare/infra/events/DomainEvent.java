package com.snoozeshare.infra.events;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();

    Instant occurredAt();
}
