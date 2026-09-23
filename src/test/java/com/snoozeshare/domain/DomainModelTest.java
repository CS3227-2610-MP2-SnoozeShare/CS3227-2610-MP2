package com.snoozeshare.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.validation.DomainValidation;

class DomainModelTest {

    @Test
    void userRecordPreservesTheSharedIdentityAndRoleFields() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-23T00:00:00Z");

        User user = new User(
                userId,
                Role.GUEST,
                "Guest One",
                "guest@example.com",
                AccountStatus.ACTIVE,
                null,
                createdAt
        );

        assertEquals(userId, user.userId());
        assertEquals(Role.GUEST, user.role());
        assertEquals(AccountStatus.ACTIVE, user.accountStatus());
        assertEquals(createdAt, user.createdAt());
    }

    @Test
    void roleAndAmenityEnumsContainTheSharedVocabulary() {
        assertEquals(3, Role.values().length);
        assertEquals(6, AmenityType.values().length);
    }

    @Test
    void requiredTextRejectsNullAndBlankValues() {
        assertThrows(IllegalArgumentException.class, () -> DomainValidation.requireText(
                null, "displayName"));
        assertThrows(IllegalArgumentException.class, () -> DomainValidation.requireText(
                "  ", "displayName"));
    }

    @Test
    void monetaryValidationRejectsNegativeAndAcceptsZeroWhenAllowed() {
        assertEquals(BigDecimal.ZERO, DomainValidation.requireNonNegative(
                BigDecimal.ZERO, "balance"));
        assertThrows(IllegalArgumentException.class, () -> DomainValidation.requireNonNegative(
                new BigDecimal("-0.01"), "balance"));
    }

    @Test
    void dateRangeValidationRequiresAnEndAfterTheStart() {
        assertThrows(IllegalArgumentException.class, () -> DomainValidation.requireDateRange(
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 24)));
        assertThrows(IllegalArgumentException.class, () -> DomainValidation.requireDateRange(
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 24)));
    }

    @Test
    void ratingValidationAcceptsOnlyOneThroughFive() {
        assertEquals(5, DomainValidation.requireRating(5));
        assertThrows(IllegalArgumentException.class, () -> DomainValidation.requireRating(0));
        assertThrows(IllegalArgumentException.class, () -> DomainValidation.requireRating(6));
    }
}
