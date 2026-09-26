package com.snoozeshare.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.validation.DomainValidation;

/** What a service asks the audit log to write. One record is one row is one change. */
public record AuditRecord(
        UUID actorId,
        AuditAction action,
        String entityType,
        UUID entityId,
        String beforeState,
        String afterState,
        BigDecimal walletAdjustment,
        String reason,
        UUID subjectUserId,
        UUID bookingId,
        UUID ticketId,
        Instant at
) {

    public AuditRecord {
        if (actorId == null || action == null || entityId == null) {
            throw new IllegalArgumentException("Actor, action and entity are required");
        }
        DomainValidation.requireText(entityType, "entityType");
        if (walletAdjustment != null && (beforeState != null || afterState != null)) {
            throw new IllegalArgumentException(
                    "An audit row records one change: a status change or a wallet adjustment, not both");
        }
    }

    public static Builder builder(UUID actorId, AuditAction action, String entityType, UUID entityId) {
        return new Builder(actorId, action, entityType, entityId);
    }

    public static final class Builder {
        private final UUID actorId;
        private final AuditAction action;
        private final String entityType;
        private final UUID entityId;
        private String beforeState;
        private String afterState;
        private BigDecimal walletAdjustment;
        private String reason;
        private UUID subjectUserId;
        private UUID bookingId;
        private UUID ticketId;
        private Instant at;

        private Builder(UUID actorId, AuditAction action, String entityType, UUID entityId) {
            this.actorId = actorId;
            this.action = action;
            this.entityType = entityType;
            this.entityId = entityId;
        }

        /** Status text before/after; enums are stored by name, null means "none". */
        public Builder status(Object before, Object after) {
            this.beforeState = text(before);
            this.afterState = text(after);
            return this;
        }

        public Builder wallet(BigDecimal adjustment) {
            this.walletAdjustment = adjustment;
            return this;
        }

        public Builder reason(String text) {
            this.reason = text == null || text.isBlank() ? null : text.trim();
            return this;
        }

        public Builder subject(UUID userId) {
            this.subjectUserId = userId;
            return this;
        }

        public Builder booking(UUID id) {
            this.bookingId = id;
            return this;
        }

        public Builder ticket(UUID id) {
            this.ticketId = id;
            return this;
        }

        public Builder at(Instant instant) {
            this.at = instant;
            return this;
        }

        public AuditRecord build() {
            return new AuditRecord(actorId, action, entityType, entityId, beforeState, afterState,
                    walletAdjustment, reason, subjectUserId, bookingId, ticketId, at);
        }

        private static String text(Object state) {
            if (state == null) {
                return null;
            }
            return state instanceof Enum<?> value ? value.name() : state.toString();
        }
    }
}
