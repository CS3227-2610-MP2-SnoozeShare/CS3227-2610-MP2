package com.snoozeshare.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One audited change. States hold status text only; money lives in walletAdjustment; a row is never both.
 * Name fields are snapshots taken when the row was written (null on legacy rows).
 */
public record AuditLogEntry(
        UUID logId,
        UUID actorUserId,
        String actorName,
        String actionType,
        String entityType,
        UUID entityId,
        String beforeState,
        String afterState,
        BigDecimal walletAdjustment,
        String reason,
        UUID subjectUserId,
        String subjectName,
        UUID bookingId,
        UUID ticketId,
        Instant timestamp
) {
}
