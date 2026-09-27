package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.events.AccountStatusChangedEvent;

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

    private static User guest(AccountFixture fixture) {
        return fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
    }

    private static List<AuditLogEntry> auditFor(AccountFixture fixture, AuditAction action) {
        return fixture.audit.search(new AuditFilter(null, Set.of(action), null, null), 50, 0);
    }

    @Test
    void suspendStoresTheTrimmedReasonAuditsItAndPublishesAfterCommit() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = guest(fixture);
            List<AccountStatusChangedEvent> events = new ArrayList<>();
            fixture.bus.subscribe(AccountStatusChangedEvent.class, events::add);

            User suspended = fixture.service().suspend(guest.userId(), fixture.agent.userId(),
                    "  repeated late cancellations  ");

            assertEquals(AccountStatus.SUSPENDED, suspended.accountStatus());
            User stored = fixture.users.findById(guest.userId()).orElseThrow();
            assertEquals(AccountStatus.SUSPENDED, stored.accountStatus());
            assertEquals("repeated late cancellations", stored.suspensionReason());
            AuditLogEntry row = auditFor(fixture, AuditAction.ACCOUNT_SUSPENDED).get(0);
            assertEquals("User", row.entityType());
            assertEquals(guest.userId(), row.entityId());
            assertEquals("ACTIVE", row.beforeState());
            assertEquals("SUSPENDED", row.afterState());
            assertEquals("repeated late cancellations", row.reason());
            assertEquals(fixture.agent.userId(), row.actorUserId());
            assertEquals(guest.userId(), row.subjectUserId());
            assertEquals(1, events.size());
            assertEquals(AccountStatus.SUSPENDED, events.get(0).newStatus());
        }
    }

    @Test
    void reactivateClearsTheReasonAndAudits() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = guest(fixture);
            fixture.service().suspend(guest.userId(), fixture.agent.userId(), "late cancellations");

            User reactivated = fixture.service().reactivate(guest.userId(), fixture.agent.userId(),
                    "appeal accepted");

            assertEquals(AccountStatus.ACTIVE, reactivated.accountStatus());
            assertNull(fixture.users.findById(guest.userId()).orElseThrow().suspensionReason());
            AuditLogEntry row = auditFor(fixture, AuditAction.ACCOUNT_REACTIVATED).get(0);
            assertEquals("SUSPENDED", row.beforeState());
            assertEquals("ACTIVE", row.afterState());
            assertEquals("appeal accepted", row.reason());
        }
    }

    @Test
    void validationRejectsBadRequestsAndChangesNothing() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = guest(fixture);
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            var service = fixture.service();
            var agentId = fixture.agent.userId();

            assertThrows(IllegalArgumentException.class, () -> service.suspend(guest.userId(), agentId, "  "));
            assertThrows(IllegalArgumentException.class, () -> service.suspend(guest.userId(), agentId, null));
            var hostId = host.userId();
            assertThrows(IllegalArgumentException.class, () -> service.suspend(guest.userId(), hostId, "not an agent"));
            assertThrows(IllegalArgumentException.class, () -> service.suspend(agentId, agentId, "self"),
                    "agents cannot be suspended, even by themselves");
            var unknown = UUID.randomUUID();
            var system = AuditService.SYSTEM_ACTOR_ID;
            assertThrows(IllegalArgumentException.class, () -> service.suspend(unknown, agentId, "unknown user"));
            assertThrows(IllegalArgumentException.class, () -> service.suspend(system, agentId, "system"));
            assertThrows(IllegalStateException.class, () -> service.reactivate(guest.userId(), agentId, "not on"));
            service.suspend(guest.userId(), agentId, "reason");
            assertThrows(IllegalStateException.class, () -> service.suspend(guest.userId(), agentId, "again"));

            assertEquals(1, auditFor(fixture, AuditAction.ACCOUNT_SUSPENDED).size(), "only the one valid suspension");
        }
    }

    @Test
    void aSuspendedAgentCannotGovern() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = guest(fixture);
            fixture.users.save(new User(fixture.agent.userId(), Role.AGENT, "Amy Tanaka", "amy@test.com",
                    AccountStatus.SUSPENDED, "CODE", fixture.agent.createdAt()));

            var service = fixture.service();
            var agentId = fixture.agent.userId();
            assertThrows(IllegalStateException.class, () -> service.suspend(guest.userId(), agentId, "reason"));
        }
    }
}
