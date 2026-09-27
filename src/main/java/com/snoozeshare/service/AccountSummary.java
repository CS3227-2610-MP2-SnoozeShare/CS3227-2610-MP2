package com.snoozeshare.service;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

/** One row of the agent Accounts screen. */
public record AccountSummary(UUID userId, String displayName, String email, Role role, Instant createdAt,
                             AccountStatus status, String suspensionReason) {

    public static AccountSummary from(User user) {
        return new AccountSummary(user.userId(), user.displayName(), user.email(), user.role(), user.createdAt(),
                user.accountStatus(), user.suspensionReason());
    }

    /** Only Guest and Host accounts can be suspended or reactivated (C34). */
    public boolean governable() {
        return role != Role.AGENT;
    }
}
