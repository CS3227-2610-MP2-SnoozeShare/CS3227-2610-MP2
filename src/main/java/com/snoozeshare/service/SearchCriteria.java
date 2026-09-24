package com.snoozeshare.service;

import java.time.LocalDate;

public record SearchCriteria(
        String city,
        Integer guests,
        LocalDate startDate,
        LocalDate endDate
) {
}
