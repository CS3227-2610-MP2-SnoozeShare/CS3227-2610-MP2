package com.snoozeshare.ui.guest.messaging;

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

/** Display projection for the Guest inbox's booking and ticket conversations. */
public record GuestConversationRow(
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

    public static List<GuestConversationRow> merge(List<BookingConversationSummary> bookings,
                                                    List<ConversationSummary> tickets) {
        List<GuestConversationRow> rows = new ArrayList<>();
        bookings.stream().map(GuestConversationRow::bookingRow).forEach(rows::add);
        tickets.stream().map(GuestConversationRow::ticketRow).forEach(rows::add);
        rows.sort(Comparator.comparing(GuestConversationRow::activityAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(row -> row.sourceId().toString()));
        return List.copyOf(rows);
    }

    private static GuestConversationRow bookingRow(BookingConversationSummary summary) {
        return new GuestConversationRow(Kind.BOOKING, summary.bookingId(),
                summary.listingTitle() + " \u00b7 " + dateRange(summary.startDate(), summary.endDate()),
                "Direct message \u00b7 " + summary.counterpartName() + " (Host)",
                summary.counterpartName(), summary.open(), summary.unreadCount(),
                summary.lastMessage() == null ? null : summary.lastMessage().sentAt(),
                summary.lastMessage());
    }

    private static GuestConversationRow ticketRow(ConversationSummary summary) {
        return new GuestConversationRow(Kind.TICKET, summary.ticketId(), summary.ticketTitle(),
                "Ticket " + summary.ticketLabel() + " \u00b7 " + summary.listingTitle()
                        + " \u00b7 Support Agent",
                summary.counterpartName(), summary.open(), summary.unreadCount(),
                summary.lastMessage() == null ? null : summary.lastMessage().sentAt(),
                summary.lastMessage());
    }

    private static String dateRange(LocalDate start, LocalDate end) {
        String endText = start.getMonth() == end.getMonth() && start.getYear() == end.getYear()
                ? Integer.toString(end.getDayOfMonth()) : DATE.format(end);
        return DATE.format(start) + "\u2013" + endText;
    }
}
