package com.snoozeshare.service;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
}
