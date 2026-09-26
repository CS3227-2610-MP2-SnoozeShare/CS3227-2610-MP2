package com.snoozeshare.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.Booking;

public interface BookingRepository {
    Optional<Booking> findById(UUID bookingId);

    List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end);

    List<Booking> findByGuest(UUID guestId);

    /** Every booking of one property, any status, oldest first. */
    List<Booking> findByListing(UUID propertyId);

    List<Booking> findByHostPending(UUID hostId);

    Booking save(Booking booking);
}
