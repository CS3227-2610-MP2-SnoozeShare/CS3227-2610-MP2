package com.snoozeshare.ui.host;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.BookingMessage;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.service.BookingConversationSummary;
import com.snoozeshare.service.ConversationSummary;
import com.snoozeshare.ui.host.messaging.HostConversationRow;

class HostConversationRowTest {

    @Test
    void mergesBookingAndTicketRowsNewestFirstWithMockupLabels() {
        UUID bookingId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        BookingMessage bookingMessage = new BookingMessage(UUID.randomUUID(), bookingId,
                UUID.randomUUID(), Role.GUEST, "Hi", Instant.parse("2026-09-24T10:00:00Z"));
        Message ticketMessage = new Message(UUID.randomUUID(), ticketId, ThreadChannel.HOST,
                UUID.randomUUID(), Role.AGENT, "Ticket update", Instant.parse("2026-09-25T10:00:00Z"));

        List<HostConversationRow> rows = HostConversationRow.merge(
                List.of(new BookingConversationSummary(bookingId, "Lakeside Cabin",
                        LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 28),
                        "Sophia G.", bookingMessage, 1, true)),
                List.of(new ConversationSummary(ticketId, ThreadChannel.HOST, "#1042",
                        "Noisy neighbors", "Lakeside Cabin", "Support Agent", ticketMessage, 0, true)));

        assertEquals(List.of(HostConversationRow.Kind.TICKET, HostConversationRow.Kind.BOOKING),
                rows.stream().map(HostConversationRow::kind).toList());
        assertEquals("Lakeside Cabin · Sep 24–28", rows.get(1).title());
        assertEquals("Direct message · Sophia G. (Guest)", rows.get(1).subtitle());
        assertEquals("Ticket #1042 · Lakeside Cabin · Support Agent", rows.get(0).subtitle());
        assertEquals("OPEN", rows.get(0).statusLabel());
    }
}
