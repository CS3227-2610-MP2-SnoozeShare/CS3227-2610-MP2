package com.snoozeshare.repository;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.Review;

public interface ReviewRepository {
    List<Review> findByBookingId(UUID bookingId);

    List<Review> findByGuestId(UUID guestId);

    Review save(Review review);
}
