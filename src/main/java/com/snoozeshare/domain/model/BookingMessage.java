package com.snoozeshare.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;

/** One text message in the private host and guest chat on a booking; the author is the guest or the host. */
public record BookingMessage(
        UUID messageId,
        UUID bookingId,
        UUID authorId,
        Role authorRole,
        String body,
        Instant sentAt
) {
}
