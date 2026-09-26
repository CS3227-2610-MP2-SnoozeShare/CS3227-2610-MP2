-- SnoozeShare — SQLite schema
--
-- Implements the tables specified in docs/SnoozeShare-Architecture-Proposal.md §4.
-- `properties` columns are the operator-specified House fields (confirmed 2026-09-22),
-- not inferred.
--
-- This file is a dev/reference artifact (mock shared DB), not wired into app startup yet —
-- infra.db.migration / a real migration runner is still (planned) per PROJECT_STATE.md § 4.5.

PRAGMA foreign_keys = ON;

CREATE TABLE users (
    userId              TEXT PRIMARY KEY,
    role                TEXT NOT NULL CHECK (role IN ('GUEST','HOST','AGENT')),
    displayName         TEXT NOT NULL,
    email               TEXT NOT NULL UNIQUE,
    accountStatus       TEXT NOT NULL CHECK (accountStatus IN ('ACTIVE','SUSPENDED')),
    registrationCode    TEXT,
    createdAt           TEXT NOT NULL
);

CREATE TABLE properties (
    propertyId          TEXT PRIMARY KEY,
    hostId              TEXT NOT NULL REFERENCES users(userId),
    status              TEXT NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    propertyType        TEXT NOT NULL CHECK (propertyType IN ('APARTMENT','HOUSE','CONDO','PRIVATE_ROOM')),
    streetAddress       TEXT NOT NULL,
    city                TEXT NOT NULL,
    region              TEXT NOT NULL,
    postalCode          TEXT NOT NULL,
    maxGuests           INTEGER NOT NULL CHECK (maxGuests > 0),
    bedrooms            INTEGER NOT NULL CHECK (bedrooms >= 0),
    bathrooms           REAL NOT NULL CHECK (bathrooms >= 0),
    baseNightlyRate     REAL NOT NULL CHECK (baseNightlyRate >= 0),
    checkInTime         TEXT NOT NULL,   -- LocalTime as "HH:MM:SS"
    checkOutTime        TEXT NOT NULL,   -- LocalTime as "HH:MM:SS"
    amenities           TEXT NOT NULL,   -- comma-separated Set<AmenityType> — SQLite has no set/array type, mock-only representation
    createdAt           TEXT NOT NULL
);

CREATE TABLE availability_blocks (
    blockId             TEXT PRIMARY KEY,
    propertyId          TEXT NOT NULL REFERENCES properties(propertyId),
    startDate           TEXT NOT NULL,
    endDate             TEXT NOT NULL,
    source              TEXT NOT NULL CHECK (source IN ('HOST_BLOCK','BOOKING')),
    bookingId           TEXT REFERENCES bookings(bookingId),
    reason              TEXT
);

CREATE TABLE bookings (
    bookingId            TEXT PRIMARY KEY,
    listingId             TEXT NOT NULL REFERENCES properties(propertyId),
    guestId               TEXT NOT NULL REFERENCES users(userId),
    startDate             TEXT NOT NULL,
    endDate               TEXT NOT NULL,
    status                TEXT NOT NULL CHECK (status IN
        ('PENDING','CONFIRMED','REJECTED','CANCELLED_BY_GUEST','CANCELLED_BY_HOST',
         'COMPLETED','FORCE_CANCELLED','FORCE_COMPLETED')),
    nightlyRateSnapshot   REAL NOT NULL,
    totalAmount           REAL NOT NULL,   -- nightlyRateSnapshot * nights, no guest-side fee (confirmed 2026-09-22 — only fee is the 3% deducted from host BOOKING_PAYOUT)
    createdAt             TEXT NOT NULL,
    decidedAt             TEXT,
    completedAt           TEXT
);

CREATE TABLE wallets (
    walletId             TEXT PRIMARY KEY,
    userId                TEXT NOT NULL UNIQUE REFERENCES users(userId),
    balance               REAL NOT NULL,
    currency              TEXT NOT NULL DEFAULT 'SGD',
    updatedAt             TEXT NOT NULL
);

CREATE TABLE wallet_transactions (
    transactionId         TEXT PRIMARY KEY,
    walletId              TEXT NOT NULL REFERENCES wallets(walletId),
    type                  TEXT NOT NULL CHECK (type IN
        ('TOP_UP','WITHDRAWAL','ESCROW_HOLD','ESCROW_REFUND','BOOKING_PAYOUT',
         'TICKET_REMEDY','AGENT_OVERRIDE')),
    amount                REAL NOT NULL,        -- signed: positive = credit, negative = debit
    feeAmount             REAL,
    balanceAfter          REAL NOT NULL,
    relatedBookingId      TEXT REFERENCES bookings(bookingId),
    relatedTicketId       TEXT REFERENCES tickets(ticketId),
    initiatedBy           TEXT REFERENCES users(userId),
    createdAt             TEXT NOT NULL
);

CREATE TABLE ticket_categories (
    categoryId            TEXT PRIMARY KEY,
    label                 TEXT NOT NULL,
    active                INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE tickets (
    ticketId              TEXT PRIMARY KEY,
    bookingId             TEXT NOT NULL REFERENCES bookings(bookingId),
    raisedByUserId        TEXT NOT NULL REFERENCES users(userId),
    raisedByRole          TEXT NOT NULL CHECK (raisedByRole IN ('GUEST','HOST')),
    category              TEXT NOT NULL,
    title                 TEXT NOT NULL,
    description            TEXT NOT NULL,
    requestedRemedy       TEXT NOT NULL CHECK (requestedRemedy IN
        ('FULL_REFUND','PARTIAL_REFUND','HOST_PAYOUT','OTHER')),
    supportingText        TEXT,
    status                TEXT NOT NULL CHECK (status IN
        ('OPEN','IN_REVIEW','RESOLVED_APPROVED','RESOLVED_REJECTED')),
    assignedAgentId       TEXT REFERENCES users(userId),
    agentNotes            TEXT,
    resolutionReason      TEXT,
    createdAt             TEXT NOT NULL,
    resolvedAt            TEXT
);

CREATE TABLE reviews (
    reviewId              TEXT PRIMARY KEY,
    bookingId             TEXT NOT NULL REFERENCES bookings(bookingId),
    guestId               TEXT NOT NULL REFERENCES users(userId),
    rating                INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment               TEXT,
    createdAt             TEXT NOT NULL
);

CREATE TABLE audit_log (
    logId                 TEXT PRIMARY KEY,
    actorUserId           TEXT NOT NULL REFERENCES users(userId),
    actionType            TEXT NOT NULL,
    entityType            TEXT NOT NULL,
    entityId              TEXT NOT NULL,
    beforeState           TEXT,
    afterState            TEXT,
    timestamp             TEXT NOT NULL
);
