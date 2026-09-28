package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.service.ListingMetrics;
import com.snoozeshare.service.ListingMetricsService;
import com.snoozeshare.service.ListingReview;

public final class ListingMetricsServiceImpl implements ListingMetricsService {

    private final Connection connection;
    private final Clock clock;

    public ListingMetricsServiceImpl(Connection connection) {
        this(connection, Clock.systemUTC());
    }

    public ListingMetricsServiceImpl(Connection connection, Clock clock) {
        this.connection = connection;
        this.clock = clock;
    }

    @Override
    public ListingMetrics metricsFor(UUID propertyId) {
        Instant now = clock.instant();
        LocalDate windowEnd = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate windowStart = windowEnd.minusDays(30);
        try (var bookingStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM bookings WHERE listingId = ?");
             var bookingDatesStatement = connection.prepareStatement(
                     "SELECT startDate, endDate FROM bookings WHERE listingId = ? "
                             + "AND status IN ('CONFIRMED', 'COMPLETED', 'FORCE_COMPLETED')");
             var reviewStatement = connection.prepareStatement(
                     "SELECT u.displayName, r.rating, r.comment FROM reviews r "
                             + "JOIN bookings b ON b.bookingId = r.bookingId "
                             + "JOIN users u ON u.userId = r.guestId "
                             + "WHERE b.listingId = ? ORDER BY r.createdAt DESC");
             var payoutStatement = connection.prepareStatement(
                     "SELECT COALESCE(SUM(a.walletAdjustment), 0) FROM audit_log a "
                             + "JOIN bookings b ON b.bookingId = a.bookingId "
                             + "WHERE b.listingId = ? AND a.actionType = 'BOOKING_PAYOUT' "
                             + "AND a.walletAdjustment IS NOT NULL "
                             + "AND a.timestamp >= ? AND a.timestamp <= ?")) {
            String listingId = JdbcCodecs.uuid(propertyId);
            bookingStatement.setString(1, listingId);
            int bookingCount;
            try (var bookingResult = bookingStatement.executeQuery()) {
                bookingResult.next();
                bookingCount = bookingResult.getInt(1);
            }

            bookingDatesStatement.setString(1, listingId);
            long bookedNights = 0;
            try (var dates = bookingDatesStatement.executeQuery()) {
                while (dates.next()) {
                    LocalDate start = LocalDate.parse(dates.getString("startDate"));
                    LocalDate end = LocalDate.parse(dates.getString("endDate"));
                    LocalDate overlapStart = start.isAfter(windowStart) ? start : windowStart;
                    LocalDate overlapEnd = end.isBefore(windowEnd) ? end : windowEnd;
                    if (overlapStart.isBefore(overlapEnd)) {
                        bookedNights += ChronoUnit.DAYS.between(overlapStart, overlapEnd);
                    }
                }
            }

            reviewStatement.setString(1, listingId);
            List<ListingReview> reviews = new ArrayList<>();
            int ratingTotal = 0;
            try (var reviewResult = reviewStatement.executeQuery()) {
                while (reviewResult.next()) {
                    int rating = reviewResult.getInt("rating");
                    ratingTotal += rating;
                    reviews.add(new ListingReview(reviewResult.getString("displayName"), rating,
                            reviewResult.getString("comment")));
                }
            }
            double averageRating = reviews.isEmpty() ? 0.0 : (double) ratingTotal / reviews.size();

            payoutStatement.setString(1, listingId);
            payoutStatement.setString(2, JdbcCodecs.instant(windowStart.atStartOfDay().toInstant(ZoneOffset.UTC)));
            payoutStatement.setString(3, JdbcCodecs.instant(now));
            BigDecimal earnings;
            try (var payoutResult = payoutStatement.executeQuery()) {
                payoutResult.next();
                earnings = JdbcCodecs.decimal(payoutResult.getString(1));
            }

            double occupancyPercentage = Math.min(100.0, bookedNights / 30.0 * 100.0);
            return new ListingMetrics(bookingCount, averageRating, reviews, occupancyPercentage, earnings);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query listing metrics", exception);
        }
    }
}
