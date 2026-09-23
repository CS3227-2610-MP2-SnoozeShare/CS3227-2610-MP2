package com.snoozeshare.domain.validation;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DomainValidation {

    private DomainValidation() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(capitalize(fieldName) + " must not be blank");
        }
        return value;
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(capitalize(fieldName) + " must be non-negative");
        }
        return value;
    }

    public static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(capitalize(fieldName) + " must be positive");
        }
        return value;
    }

    public static void requireDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("EndDate must be after startDate");
        }
    }

    public static int requireRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        return rating;
    }

    public static String requireSgd(String currency) {
        if (!"SGD".equals(currency)) {
            throw new IllegalArgumentException("Currency must be SGD");
        }
        return currency;
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
