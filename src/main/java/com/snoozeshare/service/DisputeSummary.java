package com.snoozeshare.service;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.TicketStatus;

public record DisputeSummary(
        UUID ticketId,
        String ticketLabel,
        String title,
        String category,
        String listingTitle,
        String guestName,
        String hostName,
        String assignedAgentName,
        TicketStatus status,
        Instant createdAt
) {
}
