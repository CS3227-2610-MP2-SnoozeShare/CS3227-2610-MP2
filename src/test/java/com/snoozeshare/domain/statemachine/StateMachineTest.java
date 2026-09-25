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
    void agentMovesOpenTicketToInReview() {
        assertTrue(TicketStateMachine.canTransition(
                TicketStatus.OPEN, TicketStatus.IN_REVIEW, Role.AGENT));
    }

    @Test
    void agentResolvesTicketFromInReview() {
        assertTrue(TicketStateMachine.canTransition(
                TicketStatus.IN_REVIEW, TicketStatus.RESOLVED_APPROVED, Role.AGENT));
        assertTrue(TicketStateMachine.canTransition(
                TicketStatus.IN_REVIEW, TicketStatus.RESOLVED_REJECTED, Role.AGENT));
    }

    @Test
    void agentMayReturnAnInReviewTicketToOpenButNobodyElse() {
        assertTrue(TicketStateMachine.canTransition(
                TicketStatus.IN_REVIEW, TicketStatus.OPEN, Role.AGENT));
        assertFalse(TicketStateMachine.canTransition(
                TicketStatus.IN_REVIEW, TicketStatus.OPEN, Role.HOST));
        assertFalse(TicketStateMachine.canTransition(
                TicketStatus.RESOLVED_APPROVED, TicketStatus.OPEN, Role.AGENT));
    }

    @Test
    void resolvedTicketCannotTransitionAgain() {
        assertFalse(TicketStateMachine.canTransition(
                TicketStatus.RESOLVED_APPROVED, TicketStatus.IN_REVIEW, Role.AGENT));
    }
}
