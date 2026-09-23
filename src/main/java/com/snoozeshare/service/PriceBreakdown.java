package com.snoozeshare.service;

import java.math.BigDecimal;

public record PriceBreakdown(
        BigDecimal nightlyRate,
        int nights,
        BigDecimal totalAmount
) {
}
