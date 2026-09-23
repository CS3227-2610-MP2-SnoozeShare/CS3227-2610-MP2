package com.snoozeshare.service.requests;

import java.util.UUID;

import com.snoozeshare.domain.enums.RemedyType;

public record NewTicketRequest(
        UUID bookingId,
        String category,
        String title,
        String description,
        RemedyType requestedRemedy,
        String supportingText
) {
}
