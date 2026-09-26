package com.snoozeshare.ui.common.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.WalletTransaction;

/** Formats wallet transaction values for the shared Guest and Host wallet views. */
public final class WalletTransactionFormatter {

    private WalletTransactionFormatter() {
    }

    public static String typeLabel(WalletTransactionType type) {
        return type.name();
    }

    public static String relatedLabel(WalletTransaction transaction) {
        if (transaction.relatedBookingId() != null) {
            return "Booking #" + shortId(transaction.relatedBookingId());
        }
        if (transaction.relatedTicketId() != null) {
            return "Ticket #" + shortId(transaction.relatedTicketId());
        }
        if (transaction.initiatedBy() != null) {
            return "Agent #" + shortId(transaction.initiatedBy());
        }
        return "—";
    }

    public static String amountLabel(BigDecimal amount) {
        BigDecimal display = amount.setScale(2, RoundingMode.HALF_UP);
        if (display.signum() > 0) {
            return "+SGD " + display.toPlainString();
        }
        if (display.signum() < 0) {
            return "-SGD " + display.abs().toPlainString();
        }
        return "SGD 0.00";
    }

    public static String balanceLabel(BigDecimal balance) {
        return "SGD " + balance.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String feeLabel(BigDecimal feeAmount) {
        if (feeAmount == null || feeAmount.signum() == 0) {
            return "";
        }
        return "Fee: " + balanceLabel(feeAmount);
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }
}
