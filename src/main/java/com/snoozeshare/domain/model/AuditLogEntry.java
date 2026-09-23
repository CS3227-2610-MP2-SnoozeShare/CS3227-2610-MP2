package com.snoozeshare.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AuditLogEntry(
        UUID logId,
        UUID actorUserId,
        String actionType,
        String entityType,
        UUID entityId,
        String beforeState,
        String afterState,
        Instant timestamp
) {
}
