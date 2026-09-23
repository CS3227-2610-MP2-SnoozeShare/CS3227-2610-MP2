package com.snoozeshare.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityBlock(
        UUID blockId,
        UUID propertyId,
        LocalDate startDate,
        LocalDate endDate,
        String source,
        UUID bookingId
) {
}
