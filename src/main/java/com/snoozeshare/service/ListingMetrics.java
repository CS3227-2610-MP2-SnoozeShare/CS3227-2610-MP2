package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.List;

public record ListingMetrics(int bookingCount, double averageRating, List<ListingReview> reviews,
                             double occupancyPercentage, BigDecimal earnings) {
    public ListingMetrics {
        reviews = List.copyOf(reviews);
        earnings = earnings == null ? BigDecimal.ZERO : earnings;
    }

    public ListingMetrics(int bookingCount, double averageRating) {
        this(bookingCount, averageRating, List.of(), 0.0, BigDecimal.ZERO);
    }
}
