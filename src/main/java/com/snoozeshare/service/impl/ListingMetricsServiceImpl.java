package com.snoozeshare.service.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.service.ListingMetrics;
import com.snoozeshare.service.ListingMetricsService;

public final class ListingMetricsServiceImpl implements ListingMetricsService {

    private final Connection connection;

    public ListingMetricsServiceImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public ListingMetrics metricsFor(UUID propertyId) {
        try (var bookingStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM bookings WHERE listingId = ?");
             var ratingStatement = connection.prepareStatement(
                     "SELECT AVG(r.rating) FROM reviews r "
                             + "JOIN bookings b ON b.bookingId = r.bookingId "
                             + "WHERE b.listingId = ?")) {
            String listingId = JdbcCodecs.uuid(propertyId);
            bookingStatement.setString(1, listingId);
            ratingStatement.setString(1, listingId);
            int bookingCount;
            double averageRating;
            try (var bookingResult = bookingStatement.executeQuery()) {
                bookingResult.next();
                bookingCount = bookingResult.getInt(1);
            }
            try (var ratingResult = ratingStatement.executeQuery()) {
                ratingResult.next();
                double value = ratingResult.getDouble(1);
                averageRating = ratingResult.wasNull() ? 0.0 : value;
            }
            return new ListingMetrics(bookingCount, averageRating);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query listing metrics", exception);
        }
    }
}
