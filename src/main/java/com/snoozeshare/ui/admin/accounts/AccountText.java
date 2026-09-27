package com.snoozeshare.ui.admin.accounts;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;

/** The texts the Accounts screen and its dialog display, kept in one place so search matches what is shown. */
public final class AccountText {

    private static final DateTimeFormatter JOINED = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private AccountText() {
    }

    /** {@code DD MMM YYYY}, e.g. {@code 05 Mar 2026} (C35). */
    public static String joined(Instant createdAt, ZoneId zone) {
        return JOINED.format(createdAt.atZone(zone));
    }

    public static String joined(Instant createdAt) {
        return joined(createdAt, ZoneId.systemDefault());
    }

    public static String role(Role role) {
        return switch (role) {
            case GUEST -> "Guest";
            case HOST -> "Host";
            case AGENT -> "Support Agent";
        };
    }

    public static String status(AccountStatus status) {
        return status == AccountStatus.SUSPENDED ? "Suspended" : "Active";
    }
}
