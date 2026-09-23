package com.snoozeshare.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Booking;

public interface BookingService {
    Booking submitRequest(UUID guestId, UUID propertyId, LocalDate start, LocalDate end);

    List<Booking> tripsFor(UUID guestId, TripFilter filter);

    List<Booking> pendingRequestsFor(UUID hostId);

    Booking decide(UUID bookingId, boolean approve, UUID hostId);

    Booking cancel(UUID bookingId, UUID actingGuestId);

    Booking complete(UUID bookingId);

    Booking forceTransition(UUID bookingId, BookingStatus target, UUID agentId, String reason);

    Money previewHostEarnings(UUID bookingId);
}
