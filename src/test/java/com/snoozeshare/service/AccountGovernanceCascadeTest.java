package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.events.BookingCancelledEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;

class AccountGovernanceCascadeTest {

    private static final LocalDate UPCOMING_START = AccountFixture.TODAY.plusDays(9);
    private static final LocalDate UPCOMING_END = AccountFixture.TODAY.plusDays(11);

    private static BookingStatus statusOf(AccountFixture fixture, Booking booking) {
        return fixture.bookings.findById(booking.bookingId()).orElseThrow().status();
    }

    private static List<AuditLogEntry> audit(AccountFixture fixture, AuditAction action) {
        return fixture.audit.search(new AuditFilter(null, Set.of(action), null, null), 50, 0);
    }

    @Test
    void suspendingAGuestForceCancelsPendingAndUpcomingConfirmedWithFullRefundAndSparesTheRest() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);
            Booking pending = fixture.booking(guest, loft, BookingStatus.PENDING, UPCOMING_START, UPCOMING_END);
            Booking upcoming = fixture.booking(guest, loft, BookingStatus.CONFIRMED,
                    UPCOMING_END.plusDays(1), UPCOMING_END.plusDays(4));
            Booking inProgress = fixture.booking(guest, loft, BookingStatus.CONFIRMED,
                    AccountFixture.TODAY.minusDays(1), AccountFixture.TODAY.plusDays(2));
            Booking ended = fixture.booking(guest, loft, BookingStatus.CONFIRMED,
                    AccountFixture.TODAY.minusDays(6), AccountFixture.TODAY.minusDays(3));
            Booking completed = fixture.booking(guest, loft, BookingStatus.COMPLETED,
                    AccountFixture.TODAY.minusDays(30), AccountFixture.TODAY.minusDays(27));
            BigDecimal balanceBefore = fixture.balance(guest);
            List<Object> events = new ArrayList<>();
            fixture.bus.subscribe(BookingCancelledEvent.class, events::add);
            fixture.bus.subscribe(WalletTransactionRecordedEvent.class, events::add);

            fixture.service().suspend(guest.userId(), fixture.agent.userId(), "policy breach");

            assertEquals(BookingStatus.FORCE_CANCELLED, statusOf(fixture, pending));
            assertEquals(BookingStatus.FORCE_CANCELLED, statusOf(fixture, upcoming));
            assertEquals(BookingStatus.CONFIRMED, statusOf(fixture, inProgress));
            assertEquals(BookingStatus.CONFIRMED, statusOf(fixture, ended));
            assertEquals(BookingStatus.COMPLETED, statusOf(fixture, completed));
            BigDecimal refunded = pending.totalAmount().add(upcoming.totalAmount());
            assertEquals(0, balanceBefore.add(refunded).compareTo(fixture.balance(guest)));
            assertTrue(fixture.blocks.findByPropertyId(loft.propertyId()).stream()
                    .noneMatch(b -> b.bookingId() != null && (b.bookingId().equals(pending.bookingId())
                            || b.bookingId().equals(upcoming.bookingId()))), "blocks of cancelled bookings freed");
            assertEquals(2, audit(fixture, AuditAction.BOOKING_FORCE_CANCELLED).size());
            AuditLogEntry cancelled = audit(fixture, AuditAction.BOOKING_FORCE_CANCELLED).stream()
                    .filter(row -> row.entityId().equals(pending.bookingId())).findFirst().orElseThrow();
            assertEquals("PENDING", cancelled.beforeState());
            assertEquals("FORCE_CANCELLED", cancelled.afterState());
            assertEquals("Account suspended — cascading cancellation", cancelled.reason());
            assertEquals(guest.userId(), cancelled.subjectUserId());
            assertEquals(pending.bookingId(), cancelled.bookingId());
            assertEquals("CONFIRMED", audit(fixture, AuditAction.BOOKING_FORCE_CANCELLED).stream()
                    .filter(row -> row.entityId().equals(upcoming.bookingId())).findFirst().orElseThrow()
                    .beforeState());
            List<AuditLogEntry> money = audit(fixture, AuditAction.ESCROW_REFUND);
            assertEquals(2, money.size());
            assertTrue(money.stream().allMatch(row -> row.subjectUserId().equals(guest.userId())
                    && row.walletAdjustment().signum() > 0));
            assertEquals(4, events.size(), "a cancellation and a wallet event per booking");
        }
    }

    @Test
    void suspendingAHostForceCancelsTheirPropertiesBookingsAndRefundsEachGuest() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            User guestOne = fixture.user(Role.GUEST, "One", "one@test.com", "2026-03-01T08:00:00Z");
            User guestTwo = fixture.user(Role.GUEST, "Two", "two@test.com", "2026-03-02T08:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);
            Property villa = fixture.property(host, ListingStatus.INACTIVE);
            Booking pending = fixture.booking(guestOne, loft, BookingStatus.PENDING, UPCOMING_START, UPCOMING_END);
            Booking upcoming = fixture.booking(guestTwo, villa, BookingStatus.CONFIRMED,
                    UPCOMING_START, UPCOMING_END);
            Booking started = fixture.booking(guestTwo, loft, BookingStatus.CONFIRMED,
                    AccountFixture.TODAY.minusDays(1), AccountFixture.TODAY.plusDays(1));
            BigDecimal one = fixture.balance(guestOne);
            BigDecimal two = fixture.balance(guestTwo);

            fixture.service().suspend(host.userId(), fixture.agent.userId(), "fraud");

            assertEquals(BookingStatus.FORCE_CANCELLED, statusOf(fixture, pending));
            assertEquals(BookingStatus.FORCE_CANCELLED, statusOf(fixture, upcoming));
            assertEquals(BookingStatus.CONFIRMED, statusOf(fixture, started));
            assertEquals(0, one.add(pending.totalAmount()).compareTo(fixture.balance(guestOne)));
            assertEquals(0, two.add(upcoming.totalAmount()).compareTo(fixture.balance(guestTwo)));
            for (AuditLogEntry row : audit(fixture, AuditAction.BOOKING_FORCE_CANCELLED)) {
                assertEquals(host.userId(), row.subjectUserId(), "the suspended host is the subject");
            }
        }
    }
}
