CREATE TABLE messages (
    messageId  TEXT PRIMARY KEY,
    ticketId   TEXT NOT NULL REFERENCES tickets(ticketId),
    channel    TEXT NOT NULL CHECK (channel IN ('GUEST','HOST')),
    authorId   TEXT NOT NULL REFERENCES users(userId),
    authorRole TEXT NOT NULL CHECK (authorRole IN ('GUEST','HOST','AGENT')),
    body       TEXT NOT NULL,
    sentAt     TEXT NOT NULL
);
CREATE INDEX idx_messages_thread ON messages(ticketId, channel);
CREATE TABLE message_reads (
    ticketId          TEXT NOT NULL REFERENCES tickets(ticketId),
    channel           TEXT NOT NULL CHECK (channel IN ('GUEST','HOST')),
    userId            TEXT NOT NULL REFERENCES users(userId),
    lastReadMessageId TEXT NOT NULL REFERENCES messages(messageId),
    PRIMARY KEY (ticketId, channel, userId)
)
