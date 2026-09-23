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
        Instant createdAt
) {
}
