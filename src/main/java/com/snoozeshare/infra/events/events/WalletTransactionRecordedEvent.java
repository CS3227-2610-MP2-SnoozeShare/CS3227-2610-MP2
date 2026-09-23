package com.snoozeshare.infra.events.events;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.infra.events.DomainEvent;

public record WalletTransactionRecordedEvent(UUID transactionId, UUID walletId,
                                             Instant occurredAt, UUID eventId)
        implements DomainEvent {
    public WalletTransactionRecordedEvent(UUID transactionId, UUID walletId, Instant occurredAt) {
        this(transactionId, walletId, occurredAt, UUID.randomUUID());
    }
}
