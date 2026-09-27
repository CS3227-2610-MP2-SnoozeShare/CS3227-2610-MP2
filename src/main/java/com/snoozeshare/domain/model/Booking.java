package com.snoozeshare.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.snoozeshare.domain.enums.BookingStatus;

public record Booking(
        UUID bookingId,
        UUID listingId,
        UUID guestId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        BigDecimal nightlyRateSnapshot,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant decidedAt,
        Instant completedAt,
        String hostDecisionMessage
) {
    public Booking(UUID bookingId, UUID listingId, UUID guestId, LocalDate startDate,
                   LocalDate endDate, BookingStatus status, BigDecimal nightlyRateSnapshot,
                   BigDecimal totalAmount, Instant createdAt, Instant decidedAt,
                   Instant completedAt) {
        this(bookingId, listingId, guestId, startDate, endDate, status, nightlyRateSnapshot,
                totalAmount, createdAt, decidedAt, completedAt, null);
    }
}
