package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.Review;
import com.snoozeshare.repository.ReviewRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcReviewRepository implements ReviewRepository {

    private final Connection connection;

    public JdbcReviewRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<Review> findByBookingId(UUID bookingId) {
        return findBy("bookingId", bookingId, "Unable to query reviews by booking");
    }

    @Override
    public List<Review> findByGuestId(UUID guestId) {
        return findBy("guestId", guestId, "Unable to query reviews by guest");
    }

    @Override
    public Review save(Review review) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO reviews (reviewId, bookingId, guestId, rating, comment, createdAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(reviewId) DO UPDATE SET "
                        + "bookingId = excluded.bookingId, guestId = excluded.guestId, "
                        + "rating = excluded.rating, comment = excluded.comment")) {
            statement.setString(1, JdbcCodecs.uuid(review.reviewId()));
            statement.setString(2, JdbcCodecs.uuid(review.bookingId()));
            statement.setString(3, JdbcCodecs.uuid(review.guestId()));
            statement.setInt(4, review.rating());
            statement.setString(5, review.comment());
            statement.setString(6, JdbcCodecs.instant(review.createdAt()));
            statement.executeUpdate();
            return review;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save review", exception);
        }
    }

    private List<Review> findBy(String column, UUID value, String message) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM reviews WHERE " + column + " = ? ORDER BY createdAt DESC")) {
            statement.setString(1, JdbcCodecs.uuid(value));
            try (var result = statement.executeQuery()) {
                List<Review> reviews = new ArrayList<>();
                while (result.next()) {
                    reviews.add(RowMappers.review(result));
                }
                return reviews;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(message, exception);
        }
    }
}
