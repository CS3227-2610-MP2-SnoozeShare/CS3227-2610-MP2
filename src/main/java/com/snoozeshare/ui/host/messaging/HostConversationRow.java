package com.snoozeshare.ui.host.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.snoozeshare.service.BookingConversationSummary;
import com.snoozeshare.service.ConversationSummary;

/** Display projection for the Host inbox's booking and ticket conversations. */
public record HostConversationRow(
        Kind kind,
        UUID sourceId,
        String title,
        String subtitle,
        String counterpartName,
        boolean open,
        int unreadCount,
        Instant activityAt,
        Object lastMessage
) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    public enum Kind { BOOKING, TICKET }

    public String statusLabel() {
        return kind == Kind.TICKET ? (open ? "OPEN" : "RESOLVED") : null;
    }

    public static List<HostConversationRow> merge(List<BookingConversationSummary> bookings,
                                                   List<ConversationSummary> tickets) {
        List<HostConversationRow> rows = new ArrayList<>();
        bookings.stream().map(HostConversationRow::bookingRow).forEach(rows::add);
        tickets.stream().map(HostConversationRow::ticketRow).forEach(rows::add);
        rows.sort(Comparator.comparing(HostConversationRow::activityAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(row -> row.sourceId().toString()));
        return List.copyOf(rows);
    }

    private static HostConversationRow bookingRow(BookingConversationSummary summary) {
        return new HostConversationRow(Kind.BOOKING, summary.bookingId(),
                summary.listingTitle() + " · " + dateRange(summary.startDate(), summary.endDate()),
                "Direct message · " + summary.counterpartName() + " (Guest)",
                summary.counterpartName(), summary.open(), summary.unreadCount(),
                summary.lastMessage() == null ? null : summary.lastMessage().sentAt(),
                summary.lastMessage());
    }

    private static HostConversationRow ticketRow(ConversationSummary summary) {
        return new HostConversationRow(Kind.TICKET, summary.ticketId(), summary.ticketTitle(),
                "Ticket " + summary.ticketLabel() + " · " + summary.listingTitle() + " · Support Agent",
                summary.counterpartName(), summary.open(), summary.unreadCount(),
                summary.lastMessage() == null ? null : summary.lastMessage().sentAt(),
                summary.lastMessage());
    }

    private static String dateRange(LocalDate start, LocalDate end) {
        String endText = start.getMonth() == end.getMonth() && start.getYear() == end.getYear()
                ? Integer.toString(end.getDayOfMonth()) : DATE.format(end);
        return DATE.format(start) + "–" + endText;
    }
}
