package com.snoozeshare.service;

import java.util.UUID;

import com.snoozeshare.domain.model.Review;

public interface ReviewService {
    Review submit(UUID bookingId, UUID guestId, int rating, String comment);

    boolean hasReview(UUID bookingId);
}
