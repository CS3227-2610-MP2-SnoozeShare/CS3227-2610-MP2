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

    List<Booking> findByHostPending(UUID hostId);

    Booking save(Booking booking);
}
