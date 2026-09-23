package com.snoozeshare.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Wallet(
        UUID walletId,
        UUID userId,
        BigDecimal balance,
        String currency,
        Instant updatedAt
) {
}
