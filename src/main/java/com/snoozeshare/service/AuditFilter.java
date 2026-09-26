package com.snoozeshare.service;

import java.time.LocalDate;

import com.snoozeshare.domain.enums.AuditAction;

/** Audit Log screen filter. Text is a user name/email or an id fragment; dates are inclusive. */
public record AuditFilter(String text, AuditAction action, LocalDate from, LocalDate to) {

    public static AuditFilter none() {
        return new AuditFilter(null, null, null, null);
    }
}
