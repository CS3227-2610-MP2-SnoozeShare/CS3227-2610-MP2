package com.snoozeshare.ui.guest.trips;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Booking;

class TripDashboardGroupingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    private static Booking booking(BookingStatus status, LocalDate start, LocalDate end) {
        return new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start, end, status,
                new BigDecimal("100.00"), new BigDecimal("300.00"), Instant.EPOCH, null, null);
    }

    @Test
    void pendingRequestsAreUpcomingWhateverTheirDates() {
        assertEquals(TripDashboardController.Group.UPCOMING, TripDashboardController.groupOf(
                booking(BookingStatus.PENDING, TODAY.plusDays(10), TODAY.plusDays(12)), TODAY));
    }

    @Test
    void confirmedStaysSplitByToday() {
        assertEquals(TripDashboardController.Group.UPCOMING, TripDashboardController.groupOf(
                booking(BookingStatus.CONFIRMED, TODAY.plusDays(1), TODAY.plusDays(3)), TODAY));
        assertEquals(TripDashboardController.Group.ACTIVE, TripDashboardController.groupOf(
                booking(BookingStatus.CONFIRMED, TODAY, TODAY.plusDays(2)), TODAY));
        assertEquals(TripDashboardController.Group.ACTIVE, TripDashboardController.groupOf(
                booking(BookingStatus.CONFIRMED, TODAY.minusDays(2), TODAY), TODAY));
    }

    @Test
    void aConfirmedStayThatHasEndedIsPastSoItStaysVisibleForDisputes() {
        assertEquals(TripDashboardController.Group.PAST, TripDashboardController.groupOf(
                booking(BookingStatus.CONFIRMED, TODAY.minusDays(5), TODAY.minusDays(1)), TODAY));
    }

    @Test
    void completedAndCancelledBookingsArePast() {
        for (BookingStatus status : new BookingStatus[] {BookingStatus.COMPLETED,
            BookingStatus.FORCE_COMPLETED, BookingStatus.CANCELLED_BY_GUEST,
            BookingStatus.CANCELLED_BY_HOST, BookingStatus.REJECTED, BookingStatus.FORCE_CANCELLED}) {
            assertEquals(TripDashboardController.Group.PAST, TripDashboardController.groupOf(
                    booking(status, TODAY.plusDays(3), TODAY.plusDays(5)), TODAY), status.name());
        }
    }

    @Test
    void dateRangesAreCompactAndAddTheYearOnlyOutsideThisYear() {
        assertEquals("Sep 24 – 28", TripDashboardController.dateRange(
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 28), TODAY));
        assertEquals("Sep 28 – Oct 2", TripDashboardController.dateRange(
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2), TODAY));
        assertEquals("Mar 1 – 5, 2027", TripDashboardController.dateRange(
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 5), TODAY));
        assertEquals("Dec 30, 2026 – Jan 2, 2027", TripDashboardController.dateRange(
                LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2), TODAY));
    }
}
