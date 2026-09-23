package com.snoozeshare.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.repository.AuditLogRepository;
import com.snoozeshare.service.AuditService;

public final class AuditServiceImpl implements AuditService {

    private final AuditLogRepository entries;

    public AuditServiceImpl(AuditLogRepository entries) {
        this.entries = entries;
    }

    @Override
    public void record(UUID actorId, String actionType, String entityType, UUID entityId,
                       Object before, Object after) {
        entries.save(new AuditLogEntry(UUID.randomUUID(), actorId, actionType, entityType, entityId,
                stringify(before), stringify(after), Instant.now()));
    }

    @Override
    public List<AuditLogEntry> query(UUID userId, UUID bookingId, String actionType) {
        return entries.query(userId, bookingId, actionType);
    }

    private static String stringify(Object state) {
        return state == null ? null : state.toString();
    }
}
