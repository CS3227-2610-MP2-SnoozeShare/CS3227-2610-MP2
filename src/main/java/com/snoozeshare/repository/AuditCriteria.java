package com.snoozeshare.repository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Repository-level audit query. When {@code textGiven}, a row must involve one of {@code userIds}
 * (as actor or subject) or contain {@code idFragment} in an id column; with neither, nothing matches.
 * {@code from} is inclusive, {@code toExclusive} exclusive (whole seconds).
 */
public record AuditCriteria(boolean textGiven, Set<UUID> userIds, String idFragment, String actionType,
                            Instant from, Instant toExclusive) {

    public AuditCriteria {
        userIds = userIds == null ? Set.of() : Set.copyOf(userIds);
    }

    public static AuditCriteria all() {
        return new AuditCriteria(false, Set.of(), null, null, null, null);
    }

    public static AuditCriteria forAction(String actionType) {
        return new AuditCriteria(false, Set.of(), null, actionType, null, null);
    }
}
