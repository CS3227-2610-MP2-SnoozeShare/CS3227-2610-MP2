package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

class AccountGovernanceServiceTest {

    @Test
    void listAccountsIsOldestFirstIncludesAgentsAndHidesTheSystemUser() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            fixture.user(Role.GUEST, "Guest Later", "later@test.com", "2026-03-05T08:00:00Z");
            fixture.user(Role.HOST, "Host Earlier", "earlier@test.com", "2026-02-01T10:00:00Z");

            List<AccountSummary> accounts = fixture.service().listAccounts();

            assertEquals(List.of("Amy Tanaka", "Host Earlier", "Guest Later"),
                    accounts.stream().map(AccountSummary::displayName).toList());
            assertFalse(accounts.stream().anyMatch(a -> a.displayName().equals("SnoozeShare System")));
            assertTrue(accounts.get(1).governable());
            assertFalse(accounts.get(0).governable(), "agents are listed but not governable");
            assertEquals(AccountStatus.ACTIVE, accounts.get(1).status());
        }
    }

    @Test
    void aSuspendedAccountShowsItsReason() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            fixture.users.save(new User(guest.userId(), guest.role(), guest.displayName(), guest.email(),
                    AccountStatus.SUSPENDED, null, guest.createdAt(), "late cancellations"));

            AccountSummary summary = fixture.service().listAccounts().stream()
                    .filter(a -> a.displayName().equals("Priya")).findFirst().orElseThrow();

            assertEquals(AccountStatus.SUSPENDED, summary.status());
            assertEquals("late cancellations", summary.suspensionReason());
        }
    }
}
