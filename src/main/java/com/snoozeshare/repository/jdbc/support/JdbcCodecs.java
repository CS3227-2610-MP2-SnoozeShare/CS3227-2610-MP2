package com.snoozeshare.repository.jdbc.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class JdbcCodecs {

    private JdbcCodecs() {
    }

    public static String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    public static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    public static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    public static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    public static String localDate(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static LocalDate localDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    public static String localTime(LocalTime value) {
        return value == null ? null : value.toString();
    }

    public static LocalTime localTime(String value) {
        return value == null ? null : LocalTime.parse(value);
    }

    public static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    public static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
