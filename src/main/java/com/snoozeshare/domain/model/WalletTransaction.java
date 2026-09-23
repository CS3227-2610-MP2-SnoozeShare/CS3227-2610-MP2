package com.snoozeshare.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.WalletTransactionType;

public record WalletTransaction(
        UUID transactionId,
        UUID walletId,
        WalletTransactionType type,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal balanceAfter,
        UUID relatedBookingId,
        UUID relatedTicketId,
        UUID initiatedBy,
        Instant createdAt
) {
}
