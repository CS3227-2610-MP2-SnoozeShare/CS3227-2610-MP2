CREATE TABLE booking_messages (
    messageId  TEXT PRIMARY KEY,
    bookingId  TEXT NOT NULL REFERENCES bookings(bookingId),
    authorId   TEXT NOT NULL REFERENCES users(userId),
    authorRole TEXT NOT NULL CHECK (authorRole IN ('GUEST','HOST')),
    body       TEXT NOT NULL,
    sentAt     TEXT NOT NULL
);
CREATE INDEX idx_booking_messages_booking ON booking_messages(bookingId);
CREATE TABLE booking_message_reads (
    bookingId         TEXT NOT NULL REFERENCES bookings(bookingId),
    userId            TEXT NOT NULL REFERENCES users(userId),
    lastReadMessageId TEXT NOT NULL REFERENCES booking_messages(messageId),
    PRIMARY KEY (bookingId, userId)
)
