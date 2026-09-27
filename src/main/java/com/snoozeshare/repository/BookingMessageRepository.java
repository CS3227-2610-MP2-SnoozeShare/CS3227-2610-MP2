package com.snoozeshare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.BookingMessage;

public interface BookingMessageRepository {
    /** Oldest first, by insertion order rather than the timestamp text. */
    List<BookingMessage> findThread(UUID bookingId);

    Optional<BookingMessage> findLast(UUID bookingId);

    BookingMessage save(BookingMessage message);

    void markRead(UUID bookingId, UUID userId, UUID lastReadMessageId);

    /** Messages after the user's last-read one, excluding the user's own. */
    int countUnread(UUID bookingId, UUID userId);
}
