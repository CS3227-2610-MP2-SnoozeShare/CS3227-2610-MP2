package com.snoozeshare.repository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Repository-level audit query. When {@code textGiven}, a row must involve one of {@code userIds}
 * (as actor or subject) or contain {@code idFragment} in an id column; with neither, nothing matches.
 * {@code from} is inclusive, {@code toExclusive} exclusive (whole seconds).
 * An empty {@code actionTypes} set matches every action.
 */
public record AuditCriteria(boolean textGiven, Set<UUID> userIds, String idFragment,
                            Set<String> actionTypes, Instant from, Instant toExclusive) {

    public AuditCriteria {
        userIds = userIds == null ? Set.of() : Set.copyOf(userIds);
        actionTypes = actionTypes == null ? Set.of() : Set.copyOf(actionTypes);
    }

    public static AuditCriteria all() {
        return new AuditCriteria(false, Set.of(), null, Set.of(), null, null);
    }

    public static AuditCriteria forAction(String actionType) {
        return new AuditCriteria(false, Set.of(), null, Set.of(actionType), null, null);
    }
}
