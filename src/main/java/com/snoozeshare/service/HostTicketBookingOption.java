package com.snoozeshare.service;

import com.snoozeshare.domain.model.Booking;

/** A host-owned booking that can be selected when filing a new ticket. */
public record HostTicketBookingOption(Booking booking) {
    public HostTicketBookingOption {
        if (booking == null) {
            throw new IllegalArgumentException("booking is required");
        }
    }
}
