package com.snoozeshare.service;

import java.time.LocalDate;
import java.util.UUID;

import com.snoozeshare.domain.model.BookingMessage;

/**
 * One inbox row for a guest or host: a booking chat they are a party to. {@code lastMessage} is null until
 * someone posts; {@code open} is false once the chat window has closed (the thread is then read-only).
 */
public record BookingConversationSummary(
        UUID bookingId,
        String listingTitle,
        LocalDate startDate,
        LocalDate endDate,
        String counterpartName,
        BookingMessage lastMessage,
        int unreadCount,
        boolean open
) {
}
