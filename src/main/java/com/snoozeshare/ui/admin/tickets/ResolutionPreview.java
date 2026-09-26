package com.snoozeshare.ui.admin.tickets;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.snoozeshare.domain.settlement.SettlementBreakdown;
import com.snoozeshare.domain.settlement.SettlementCalculator;

/**
 * Live preview text for the resolution dialog. Pure logic so it can be tested without JavaFX.
 */
public final class ResolutionPreview {

    /** valid=false carries an explanatory message; valid=true carries the summary and parsed refund. */
    public record Result(boolean valid, String message, String summary, BigDecimal refund) {
    }

    private ResolutionPreview() {
    }

    public static Result compute(BigDecimal escrow, String refundText) {
        if (refundText == null || refundText.isBlank()) {
            return invalid("Enter a guest refund amount (0 for a full payout to the host)");
        }
        BigDecimal refund;
        try {
            refund = new BigDecimal(refundText.trim());
        } catch (NumberFormatException exception) {
            return invalid("Refund must be a number");
        }
        try {
            SettlementBreakdown split = SettlementCalculator.split(escrow, refund);
            return new Result(true, "", "Guest refund " + money(split.guestRefund())
                    + " | Host payout " + money(split.hostNet()) + " (fee " + money(split.fee()) + ")",
                    split.guestRefund());
        } catch (IllegalArgumentException exception) {
            return invalid(exception.getMessage());
        }
    }

    private static Result invalid(String message) {
        return new Result(false, message, "", null);
    }

    private static String money(BigDecimal value) {
        return "SGD " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
