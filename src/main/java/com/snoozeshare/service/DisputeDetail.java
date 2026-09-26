package com.snoozeshare.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Ticket;

public record DisputeDetail(
        Ticket ticket,
        String ticketLabel,
        String listingTitle,
        LocalDate startDate,
        LocalDate endDate,
        String guestName,
        String hostName,
        String raisedByName,
        String assignedAgentName,
        BookingStatus bookingStatus,
        BigDecimal escrowAmount,
        boolean escrowHeld,
        String phaseLabel
) {
}
