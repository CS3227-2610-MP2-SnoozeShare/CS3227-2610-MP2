package com.snoozeshare.ui.common.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        return switch (transaction.type()) {
            case TOP_UP, WITHDRAWAL -> "—";
            case ESCROW_HOLD, ESCROW_REFUND, BOOKING_PAYOUT -> referenceLabel(
                    "Booking", transaction.relatedBookingId());
            case TICKET_REMEDY -> referenceLabel("Ticket", transaction.relatedTicketId());
            case AGENT_OVERRIDE -> referenceLabel("Agent", transaction.initiatedBy());
        };
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

    public static String dollarAmountLabel(BigDecimal amount) {
        BigDecimal display = amount.setScale(2, RoundingMode.HALF_UP);
        if (display.signum() > 0) {
            return "+$" + display.toPlainString();
        }
        if (display.signum() < 0) {
            return "-$" + display.abs().toPlainString();
        }
        return "$0.00";
    }

    public static String dollarBalanceLabel(BigDecimal balance) {
        return "$" + balance.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String feeLabel(BigDecimal feeAmount) {
        if (feeAmount == null || feeAmount.signum() == 0) {
            return "";
        }
        return "Fee: " + balanceLabel(feeAmount);
    }

    public static String dollarFeeLabel(BigDecimal feeAmount) {
        if (feeAmount == null || feeAmount.signum() == 0) {
            return "";
        }
        return "Fee: " + dollarBalanceLabel(feeAmount);
    }

    public static BigDecimal escrowHeldAmount(List<WalletTransaction> transactions) {
        Map<UUID, List<WalletTransaction>> byBooking = transactions.stream()
                .filter(transaction -> transaction.relatedBookingId() != null)
                .collect(Collectors.groupingBy(WalletTransaction::relatedBookingId));
        BigDecimal total = BigDecimal.ZERO;
        for (List<WalletTransaction> bookingTransactions : byBooking.values()) {
            BigDecimal held = BigDecimal.ZERO;
            List<WalletTransaction> chronological = new ArrayList<>(bookingTransactions);
            chronological.sort(Comparator.comparing(WalletTransaction::createdAt));
            for (WalletTransaction transaction : chronological) {
                if (transaction.type() == WalletTransactionType.ESCROW_HOLD) {
                    held = held.add(transaction.amount().abs());
                } else if (releasesEscrow(transaction.type())) {
                    held = held.subtract(transaction.amount().abs()).max(BigDecimal.ZERO);
                }
            }
            total = total.add(held);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static String escrowHeldLabel(List<WalletTransaction> transactions) {
        return "Includes $" + escrowHeldAmount(transactions).toPlainString()
                + " currently held in escrow for pending bookings";
    }

    private static boolean releasesEscrow(WalletTransactionType type) {
        return type == WalletTransactionType.ESCROW_REFUND
                || type == WalletTransactionType.BOOKING_PAYOUT
                || type == WalletTransactionType.TICKET_REMEDY
                || type == WalletTransactionType.AGENT_OVERRIDE;
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String referenceLabel(String kind, UUID id) {
        return id == null ? "—" : kind + " #" + shortId(id);
    }
}
