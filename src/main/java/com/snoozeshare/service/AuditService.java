package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.WalletTransaction;

public interface AuditService {

    /** The seeded, non-loginable user that owns system-initiated rows. */
    UUID SYSTEM_ACTOR_ID = UUID.fromString("a0000000-0000-0000-0000-0000000000ff");

    /** Writes one row on the caller's connection, so it commits or rolls back with the caller's change. */
    void record(AuditRecord record);

    /**
     * Writes the money row for one wallet transaction. {@code applied} is the amount that actually moved
     * in the owner's wallet (a payout net of fee, a signed debit for a hold).
     */
    default void recordWalletTransaction(UUID actorId, UUID ownerUserId, WalletTransaction transaction,
                                         BigDecimal applied, String reason) {
        record(AuditRecord.builder(actorId, AuditAction.forWallet(transaction.type()), "WalletTransaction",
                        transaction.transactionId())
                .wallet(applied).reason(reason).subject(ownerUserId)
                .booking(transaction.relatedBookingId()).ticket(transaction.relatedTicketId())
                .at(transaction.createdAt()).build());
    }

    /** Newest first. Text is a name/email/id fragment; a name is resolved to user ids before querying. */
    List<AuditLogEntry> search(AuditFilter filter, int limit, int offset);
}
