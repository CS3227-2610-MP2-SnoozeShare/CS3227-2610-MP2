package com.snoozeshare.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;

public record Ticket(
        UUID ticketId,
        UUID bookingId,
        UUID raisedByUserId,
        Role raisedByRole,
        String category,
        String title,
        String description,
        RemedyType requestedRemedy,
        String supportingText,
        TicketStatus status,
        UUID assignedAgentId,
        String agentNotes,
        String resolutionReason,
        Instant createdAt,
        Instant resolvedAt
) {
}
