package com.snoozeshare.ui.admin.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.model.AuditLogEntry;

class AuditLogFormattingTest {

    private static AuditLogEntry entry(String action, String before, String after, BigDecimal amount,
                                       UUID booking, UUID ticket) {
        return new AuditLogEntry(UUID.randomUUID(), UUID.randomUUID(), "Amy", action, "Ticket", UUID.randomUUID(),
                before, after, amount, null, null, null, booking, ticket, Instant.parse("2026-09-25T04:00:00Z"), null);
    }

    @Test
    void statusShowsBeforeToAfterWithReadableLabels() {
        assertEquals("In review \u2192 Resolved approved",
                AuditLogController.statusText(entry("TICKET_RESOLVED", "IN_REVIEW", "RESOLVED_APPROVED", null,
                        null, null)));
        assertEquals("Pending", AuditLogController.statusText(entry("BOOKING_REQUESTED", null, "PENDING", null,
                null, null)));
        assertEquals("\u2014", AuditLogController.statusText(entry("TOP_UP", null, null, BigDecimal.TEN, null, null)));
    }

    @Test
    void amountIsSignedSgdOrADash() {
        assertEquals("+SGD 465.60", AuditLogController.amountText(entry("BOOKING_PAYOUT", null, null,
                new BigDecimal("465.6"), null, null)));
        assertEquals("-SGD 120.00", AuditLogController.amountText(entry("ESCROW_HOLD", null, null,
                new BigDecimal("-120"), null, null)));
        assertEquals("\u2014", AuditLogController.amountText(entry("TICKET_RESOLVED", "A", "B", null, null, null)));
    }

    private static AuditLogEntry target(String entityType, UUID entityId, UUID subjectId, String subjectName) {
        return new AuditLogEntry(UUID.randomUUID(), UUID.randomUUID(), "Amy", "X", entityType, entityId,
                null, null, null, null, subjectId, subjectName, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-09-25T04:00:00Z"), null);
    }

    @Test
    void targetIsOnlyTheDirectEntityNotTheRelatedRecords() {
        UUID booking = UUID.fromString("20000000-0000-0000-0000-000000000009");
        UUID ticket = UUID.fromString("d0000000-0000-0000-0000-000000000004");
        UUID property = UUID.fromString("10000000-0000-0000-0000-000000000010");
        // A ticket resolution names the ticket only, although the row also carries a booking id.
        assertEquals("Ticket #0004", AuditLogController.targetText(target("Ticket", ticket, null, "Wei")));
        assertEquals("Booking #0009", AuditLogController.targetText(target("Booking", booking, null, "Wei")));
        assertEquals("Property #0010", AuditLogController.targetText(target("Property", property, null, null)));
    }

    @Test
    void targetOfAUserActionOrWalletMovementIsThatUser() {
        UUID user = UUID.fromString("c0000000-0000-0000-0000-000000000006");
        assertEquals("Kai Nakamura", AuditLogController.targetText(target("User", user, user, "Kai Nakamura")));
        assertEquals("SnoozeShare System", AuditLogController.targetText(
                target("WalletTransaction", UUID.randomUUID(), user, "SnoozeShare System")));
        assertEquals("User #0006", AuditLogController.targetText(target("User", user, null, null)));
        assertEquals("User #0006", AuditLogController.targetText(
                target("WalletTransaction", UUID.randomUUID(), user, null)));
    }

    @Test
    void timestampIsDayMonthYearWithA24HourClock() {
        assertEquals("25/09/2026 04:00", AuditLogController.timeText(entry("X", null, null, null, null, null),
                java.time.ZoneOffset.UTC));
    }

    @Test
    void pillToneGroupsActions() {
        assertEquals("agent-pill-success", AuditLogController.pillClass("BOOKING_PAYOUT"));
        assertEquals("agent-pill-accent", AuditLogController.pillClass("AGENT_OVERRIDE"));
        assertEquals("agent-pill-warning", AuditLogController.pillClass("ACCOUNT_SUSPENDED"));
        assertEquals("agent-pill-danger", AuditLogController.pillClass("BOOKING_FORCE_CANCELLED"));
        assertEquals("agent-pill-neutral", AuditLogController.pillClass("LISTING_UPDATED"));
    }
}
