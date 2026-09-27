package com.snoozeshare.ui.admin.accounts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.service.AccountSummary;

class AccountSearchTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final AccountSummary PRIYA = new AccountSummary(UUID.randomUUID(), "Priya Nair",
            "priya.nair@snoozeshare.test", Role.HOST, Instant.parse("2026-03-05T08:00:00Z"),
            AccountStatus.SUSPENDED, "repeated late cancellations");

    private static boolean matches(String query) {
        return AccountSearch.matches(PRIYA, query, UTC);
    }

    @Test
    void blankShowsEverything() {
        assertTrue(matches(null));
        assertTrue(matches(""));
        assertTrue(matches("   "));
    }

    @Test
    void matchesEachDisplayedFieldCaseInsensitively() {
        assertTrue(matches("PRIYA"), "display name");
        assertTrue(matches("nair"), "display name, later word");
        assertTrue(matches("snoozeshare.test"), "email");
        assertTrue(matches("host"), "role");
        assertTrue(matches("mar"), "joined month name");
        assertTrue(matches("2026"), "joined year");
        assertTrue(matches("05 mar"), "joined as displayed");
        assertTrue(matches("suspend"), "status");
    }

    @Test
    void doesNotSearchTheReasonOrUnrelatedText() {
        assertFalse(matches("cancellations"), "the reason is not searched");
        assertFalse(matches("guest"), "the role is Host");
        assertFalse(matches("active"), "the status is Suspended");
        assertFalse(matches("zzz"));
    }

    @Test
    void agentsAreFoundBySupportAgent() {
        AccountSummary agent = new AccountSummary(UUID.randomUUID(), "Amy Tanaka", "amy@test.com", Role.AGENT,
                Instant.parse("2026-01-10T09:00:00Z"), AccountStatus.ACTIVE, null);
        assertTrue(AccountSearch.matches(agent, "support agent", UTC));
    }
}
