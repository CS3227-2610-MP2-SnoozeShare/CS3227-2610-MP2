package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;

public interface AuditService {
    void record(UUID actorId, String actionType, String entityType, UUID entityId,
                Object before, Object after);

    List<AuditLogEntry> query(UUID userId, UUID bookingId, String actionType);
}
