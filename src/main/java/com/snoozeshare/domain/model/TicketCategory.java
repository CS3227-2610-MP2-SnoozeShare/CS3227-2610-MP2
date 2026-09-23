package com.snoozeshare.domain.model;

import java.util.UUID;

public record TicketCategory(
        UUID categoryId,
        String label,
        boolean active
) {
}
