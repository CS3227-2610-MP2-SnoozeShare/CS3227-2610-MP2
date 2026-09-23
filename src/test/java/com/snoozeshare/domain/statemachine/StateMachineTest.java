package com.snoozeshare.domain.statemachine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
class StateMachineTest {

    @Test
    void hostCanApprovePendingBooking() {
        assertTrue(BookingStateMachine.canTransition(
                BookingStatus.PENDING, BookingStatus.CONFIRMED, Role.HOST));
    }

    @Test
    void guestCanCancelPendingBooking() {
        assertTrue(BookingStateMachine.canTransition(
                BookingStatus.PENDING, BookingStatus.CANCELLED_BY_GUEST, Role.GUEST));
    }

    @Test
    void agentCanForceCancelNonTerminalBooking() {
        assertTrue(BookingStateMachine.canTransition(
                BookingStatus.CONFIRMED, BookingStatus.FORCE_CANCELLED, Role.AGENT));
    }

    @Test
    void guestCannotApproveOrForceCompleteBooking() {
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.PENDING, BookingStatus.CONFIRMED, Role.GUEST));
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.CONFIRMED, BookingStatus.FORCE_COMPLETED, Role.GUEST));
    }

    @Test
    void terminalBookingCannotTransitionAgain() {
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.COMPLETED, BookingStatus.FORCE_CANCELLED, Role.AGENT));
    }

    @Test
    void agentMovesOpenTicketToUnderReview() {
        assertTrue(TicketStateMachine.canTransition(
                TicketStatus.OPEN, TicketStatus.UNDER_REVIEW, Role.AGENT));
    }

    @Test
    void agentResolvesTicketFromUnderReview() {
        assertTrue(TicketStateMachine.canTransition(
                TicketStatus.UNDER_REVIEW, TicketStatus.RESOLVED_APPROVED, Role.AGENT));
        assertTrue(TicketStateMachine.canTransition(
                TicketStatus.UNDER_REVIEW, TicketStatus.RESOLVED_REJECTED, Role.AGENT));
    }

    @Test
    void resolvedTicketCannotTransitionAgain() {
        assertFalse(TicketStateMachine.canTransition(
                TicketStatus.RESOLVED_APPROVED, TicketStatus.UNDER_REVIEW, Role.AGENT));
    }
}
