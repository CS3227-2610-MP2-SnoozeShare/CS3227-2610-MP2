package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SearchCriteriaTest {

    @Test
    void allFieldsNullableByDefault() {
        var criteria = new SearchCriteria(null, null, null, null);

        assertNull(criteria.city());
        assertNull(criteria.guests());
        assertNull(criteria.startDate());
        assertNull(criteria.endDate());
    }

    @Test
    void allFieldsPreserved() {
        var criteria = new SearchCriteria("Singapore", 2,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));

        assertEquals("Singapore", criteria.city());
        assertEquals(2, criteria.guests());
        assertEquals(LocalDate.of(2026, 10, 1), criteria.startDate());
        assertEquals(LocalDate.of(2026, 10, 5), criteria.endDate());
    }
}
