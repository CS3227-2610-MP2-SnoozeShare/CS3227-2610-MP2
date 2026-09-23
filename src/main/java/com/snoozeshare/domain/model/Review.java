package com.snoozeshare.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Review(
        UUID reviewId,
        UUID bookingId,
        UUID guestId,
        int rating,
        String comment,
        Instant createdAt
) {
}
