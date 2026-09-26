package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.TicketStatus;

class AuditRecordTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID ENTITY = UUID.randomUUID();

    @Test
    void aRowCannotCarryBothAStatusChangeAndAWalletAdjustment() {
        var builder = AuditRecord.builder(ACTOR, AuditAction.TICKET_RESOLVED, "Ticket", ENTITY)
                .status(TicketStatus.IN_REVIEW, TicketStatus.RESOLVED_APPROVED)
                .wallet(new BigDecimal("5.00"));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void statusEnumsAreStoredAsPlainNamesAndNullMeansNone() {
        AuditRecord record = AuditRecord.builder(ACTOR, AuditAction.BOOKING_REQUESTED, "Booking", ENTITY)
                .status(null, "PENDING").build();

        assertNull(record.beforeState());
        assertEquals("PENDING", record.afterState());
        AuditRecord fromEnum = AuditRecord.builder(ACTOR, AuditAction.TICKET_ASSIGNED, "Ticket", ENTITY)
                .status(TicketStatus.OPEN, TicketStatus.IN_REVIEW).build();
        assertEquals("OPEN", fromEnum.beforeState());
        assertEquals("IN_REVIEW", fromEnum.afterState());
    }

    @Test
    void requiresActorActionAndEntity() {
        assertThrows(IllegalArgumentException.class, () ->
                AuditRecord.builder(null, AuditAction.TOP_UP, "WalletTransaction", ENTITY).build());
        assertThrows(IllegalArgumentException.class, () ->
                AuditRecord.builder(ACTOR, null, "WalletTransaction", ENTITY).build());
        assertThrows(IllegalArgumentException.class, () ->
                AuditRecord.builder(ACTOR, AuditAction.TOP_UP, "WalletTransaction", null).build());
    }

    @Test
    void blankReasonBecomesNull() {
        AuditRecord record = AuditRecord.builder(ACTOR, AuditAction.TICKET_NOTE_SAVED, "Ticket", ENTITY)
                .reason("   ").build();

        assertNull(record.reason());
    }
}
