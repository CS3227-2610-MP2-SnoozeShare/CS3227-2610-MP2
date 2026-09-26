package com.snoozeshare.service;

import java.time.LocalDate;
import java.util.Set;

import com.snoozeshare.domain.enums.AuditAction;

/**
 * Audit Log screen filter. Text is a user name/email or an id fragment; dates are inclusive;
 * an empty (or null) set of actions means every action type.
 */
public record AuditFilter(String text, Set<AuditAction> actions, LocalDate from, LocalDate to) {

    public AuditFilter {
        actions = actions == null ? Set.of() : Set.copyOf(actions);
    }

    public static AuditFilter none() {
        return new AuditFilter(null, Set.of(), null, null);
    }
}
