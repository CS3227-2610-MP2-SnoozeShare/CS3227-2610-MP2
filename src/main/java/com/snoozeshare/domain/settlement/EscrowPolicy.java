package com.snoozeshare.domain.settlement;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.WalletTransaction;

public final class EscrowPolicy {

    private static final Set<WalletTransactionType> RELEASING = EnumSet.of(
            WalletTransactionType.ESCROW_REFUND, WalletTransactionType.BOOKING_PAYOUT,
            WalletTransactionType.TICKET_REMEDY, WalletTransactionType.AGENT_OVERRIDE);

    private EscrowPolicy() {
    }

    /**
     * Escrow is held when a booking has an ESCROW_HOLD row and nothing has released it yet.
     */
    public static boolean isHeld(List<WalletTransaction> bookingTransactions) {
        boolean held = false;
        for (WalletTransaction transaction : bookingTransactions) {
            if (RELEASING.contains(transaction.type())) {
                return false;
            }
            if (transaction.type() == WalletTransactionType.ESCROW_HOLD) {
                held = true;
            }
        }
        return held;
    }
}
