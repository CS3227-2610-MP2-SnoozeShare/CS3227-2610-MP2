package com.snoozeshare.service.requests;

import java.time.LocalDate;
import java.util.UUID;

public record NewBookingRequest(
        UUID guestId,
        UUID propertyId,
        LocalDate startDate,
        LocalDate endDate
) {
}
