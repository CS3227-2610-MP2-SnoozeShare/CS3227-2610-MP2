package com.snoozeshare.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;

public record User(
        UUID userId,
        Role role,
        String displayName,
        String email,
        AccountStatus accountStatus,
        String registrationCode,
        Instant createdAt,
        String suspensionReason
) {
    /** A user with no suspension reason (every account that is not suspended by an agent). */
    public User(UUID userId, Role role, String displayName, String email, AccountStatus accountStatus,
                String registrationCode, Instant createdAt) {
        this(userId, role, displayName, email, accountStatus, registrationCode, createdAt, null);
    }
}
