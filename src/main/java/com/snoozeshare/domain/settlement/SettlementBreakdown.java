package com.snoozeshare.domain.settlement;

import java.math.BigDecimal;

/**
 * How a booking's held escrow is divided: a fee-free guest refund and a host share net of the platform fee.
 */
public record SettlementBreakdown(
        BigDecimal escrow,
        BigDecimal guestRefund,
        BigDecimal hostGross,
        BigDecimal fee,
        BigDecimal hostNet
) {
}
