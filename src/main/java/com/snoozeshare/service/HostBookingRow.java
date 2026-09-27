package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.OptionalDouble;

import com.snoozeshare.domain.model.Booking;

public record HostBookingRow(
        Booking booking,
        String guestDisplayName,
        String listingTitle,
        long nights,
        BigDecimal grossAmount,
        BigDecimal projectedNetAmount,
        OptionalDouble guestAverageRating
) {
    public HostBookingRow {
        if (booking == null || guestDisplayName == null || listingTitle == null
                || nights < 0 || grossAmount == null || projectedNetAmount == null
                || guestAverageRating == null) {
            throw new IllegalArgumentException("Host booking projection values are required");
        }
    }
}
