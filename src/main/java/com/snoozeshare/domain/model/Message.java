package com.snoozeshare.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;

public record Message(
        UUID messageId,
        UUID ticketId,
        ThreadChannel channel,
        UUID authorId,
        Role authorRole,
        String body,
        Instant sentAt
) {
}
