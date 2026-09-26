package com.snoozeshare.service.impl;

import java.time.Clock;
import java.util.UUID;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Review;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.ReviewRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.ReviewService;

public final class ReviewServiceImpl implements ReviewService {

    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final AuditService audit;
    private final Clock clock;

    public ReviewServiceImpl(BookingRepository bookings, ReviewRepository reviews,
                             AuditService audit, Clock clock) {
        this.bookings = bookings;
        this.reviews = reviews;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Review submit(UUID bookingId, UUID guestId, int rating, String comment) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));

        if (!booking.guestId().equals(guestId)) {
            throw new IllegalArgumentException("Only the booking guest may leave a review");
        }

        if (booking.status() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Reviews can only be submitted for completed stays");
        }

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        if (!reviews.findByBookingId(bookingId).isEmpty()) {
            throw new IllegalStateException("A review already exists for this booking");
        }

        Review review = new Review(UUID.randomUUID(), bookingId, guestId, rating, comment,
                clock.instant());
        Review saved = reviews.save(review);
        audit.record(AuditRecord.builder(guestId, AuditAction.REVIEW_SUBMITTED, "Review", saved.reviewId())
                .subject(guestId).booking(bookingId).at(saved.createdAt()).build());
        return saved;
    }

    @Override
    public boolean hasReview(UUID bookingId) {
        return !reviews.findByBookingId(bookingId).isEmpty();
    }
}
