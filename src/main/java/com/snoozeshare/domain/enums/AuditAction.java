package com.snoozeshare.domain.enums;

/** Every kind of change the audit log records; the log column stores the constant's name. */
public enum AuditAction {
    BOOKING_REQUESTED,
    BOOKING_CONFIRMED,
    BOOKING_REJECTED,
    BOOKING_CANCELLED_BY_GUEST,
    BOOKING_CANCELLED_BY_HOST,
    BOOKING_COMPLETED,
    BOOKING_FORCE_CANCELLED,
    TICKET_OPENED,
    TICKET_ASSIGNED,
    TICKET_UNASSIGNED,
    TICKET_RESOLVED,
    TICKET_NOTE_SAVED,
    TOP_UP,
    WITHDRAWAL,
    ESCROW_HOLD,
    ESCROW_REFUND,
    BOOKING_PAYOUT,
    TICKET_REMEDY,
    AGENT_OVERRIDE,
    LISTING_CREATED,
    LISTING_UPDATED,
    LISTING_STATUS_CHANGED,
    LISTING_STATUS_CASCADE,
    TICKET_CATEGORY_CREATED,
    TICKET_CATEGORY_RENAMED,
    TICKET_CATEGORY_TOGGLED,
    TICKET_CATEGORY_DELETED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_REACTIVATED;

    /** Money rows reuse the wallet transaction type's name so the two ledgers line up. */
    public static AuditAction forWallet(WalletTransactionType type) {
        return valueOf(type.name());
    }
}
