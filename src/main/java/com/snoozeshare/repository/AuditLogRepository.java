package com.snoozeshare.repository;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;

public interface AuditLogRepository {
    AuditLogEntry save(AuditLogEntry entry);

    List<AuditLogEntry> query(UUID userId, UUID bookingId, String actionType);
}
