package com.snoozeshare.service;

import java.math.BigDecimal;

/** The refund a guest cancellation would trigger: the amount and the share of the total it represents. */
public record CancellationRefund(BigDecimal amount, int percent) {
}
