package com.snoozeshare.domain.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.snoozeshare.domain.validation.DomainValidation;

public final class SettlementCalculator {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");

    private SettlementCalculator() {
    }

    /**
     * Splits the held escrow. The guest refund carries no fee; the platform takes 3% of the host share only.
     */
    public static SettlementBreakdown split(BigDecimal escrow, BigDecimal guestRefund) {
        DomainValidation.requirePositive(escrow, "escrow");
        DomainValidation.requireNonNegative(guestRefund, "guestRefund");
        if (guestRefund.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Guest refund must have at most two decimal places");
        }
        if (guestRefund.compareTo(escrow) > 0) {
            throw new IllegalArgumentException("Guest refund cannot exceed the escrow held");
        }
        BigDecimal held = escrow.setScale(2, RoundingMode.HALF_UP);
        BigDecimal refund = guestRefund.setScale(2, RoundingMode.HALF_UP);
        BigDecimal hostGross = held.subtract(refund);
        BigDecimal fee = hostGross.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        return new SettlementBreakdown(held, refund, hostGross, fee, hostGross.subtract(fee));
    }
}
