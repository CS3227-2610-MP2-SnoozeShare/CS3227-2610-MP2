package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.model.WalletTransaction;

public interface TransactionService {
    WalletTransaction holdEscrow(UUID bookingId);

    WalletTransaction refundEscrow(UUID bookingId, BigDecimal refundAmount);

    WalletTransaction settleBookingCompletion(UUID bookingId);

    WalletTransaction applyTicketRemedy(UUID ticketId, RemedyType remedy, BigDecimal amount,
                                        UUID agentId);

    WalletTransaction manualOverride(UUID bookingId, BigDecimal amount, boolean creditGuest,
                                     UUID agentId, String reason);

    List<WalletTransaction> historyFor(UUID bookingId);
}
