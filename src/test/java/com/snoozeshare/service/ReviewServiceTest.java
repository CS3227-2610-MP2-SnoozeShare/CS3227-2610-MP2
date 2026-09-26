package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Review;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.ReviewRepository;
import com.snoozeshare.service.impl.ReviewServiceImpl;
import com.snoozeshare.testsupport.Fakes;

class ReviewServiceTest {

    private final Fakes.RecordingAudit audit = new Fakes.RecordingAudit();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC);

    private final UUID guestId = UUID.randomUUID();
    private final UUID listingId = UUID.randomUUID();
    private Booking completedBooking;
    private InMemoryReviews reviews;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        completedBooking = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.COMPLETED, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z"));

        reviews = new InMemoryReviews();
        BookingRepository bookings = stubBookings(completedBooking);
        service = new ReviewServiceImpl(bookings, reviews, audit, clock);
    }

    @Test
    void successfullySubmitsReview() {
        Review review = service.submit(completedBooking.bookingId(), guestId, 4, "Great stay!");

        assertNotNull(review.reviewId());
        assertEquals(completedBooking.bookingId(), review.bookingId());
        assertEquals(guestId, review.guestId());
        assertEquals(4, review.rating());
        assertEquals("Great stay!", review.comment());
        assertNotNull(review.createdAt());
        assertTrue(audit.actions().contains("REVIEW_SUBMITTED"));
    }

    @Test
    void allowsNullComment() {
        Review review = service.submit(completedBooking.bookingId(), guestId, 5, null);
        assertEquals(5, review.rating());
    }

    @Test
    void rejectsBookingNotFound() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submit(UUID.randomUUID(), guestId, 4, "Nice"));
    }

    @Test
    void rejectsWhenGuestDoesNotOwnBooking() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submit(completedBooking.bookingId(), UUID.randomUUID(), 4, "Nice"));
    }

    @Test
    void rejectsNonCompletedBooking() {
        Booking confirmed = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.CONFIRMED, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"), null);
        service = new ReviewServiceImpl(stubBookings(confirmed), reviews, audit, clock);
        assertThrows(IllegalStateException.class, () ->
                service.submit(confirmed.bookingId(), guestId, 4, "Nice"));
    }

    @Test
    void rejectsForceCompletedBooking() {
        Booking forceCompleted = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.FORCE_COMPLETED, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z"));
        service = new ReviewServiceImpl(stubBookings(forceCompleted), reviews, audit, clock);
        assertThrows(IllegalStateException.class, () ->
                service.submit(forceCompleted.bookingId(), guestId, 4, "Nice"));
    }

    @Test
    void rejectsRatingBelow1() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submit(completedBooking.bookingId(), guestId, 0, "Bad"));
    }

    @Test
    void rejectsRatingAbove5() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submit(completedBooking.bookingId(), guestId, 6, "Good"));
    }

    @Test
    void rejectsDuplicateReview() {
        service.submit(completedBooking.bookingId(), guestId, 4, "First review");
        assertThrows(IllegalStateException.class, () ->
                service.submit(completedBooking.bookingId(), guestId, 5, "Second review"));
    }

    private static BookingRepository stubBookings(Booking booking) {
        return new BookingRepository() {
            @Override
            public Optional<Booking> findById(UUID id) {
                return id.equals(booking.bookingId()) ? Optional.of(booking) : Optional.empty();
            }

            @Override
            public List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end) {
                return List.of();
            }

            @Override
            public List<Booking> findByGuest(UUID guestId) {
                return List.of(booking);
            }

            @Override
            public List<Booking> findByHostPending(UUID hostId) {
                return List.of();
            }

            @Override
            public Booking save(Booking b) {
                return b;
            }
        };
    }

    private static final class InMemoryReviews implements ReviewRepository {
        private final Map<UUID, Review> store = new HashMap<>();

        @Override
        public List<Review> findByBookingId(UUID bookingId) {
            return store.values().stream()
                    .filter(r -> r.bookingId().equals(bookingId)).toList();
        }

        @Override
        public Review save(Review review) {
            store.put(review.reviewId(), review);
            return review;
        }
    }
}
