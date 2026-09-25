package com.snoozeshare.domain.statemachine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.Role;

class AgentCompletionTest {

    @Test
    void agentCanCompleteAConfirmedBooking() {
        assertTrue(BookingStateMachine.canTransition(
                BookingStatus.CONFIRMED, BookingStatus.COMPLETED, Role.AGENT));
    }

    @Test
    void agentCannotCompleteAPendingBooking() {
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.PENDING, BookingStatus.COMPLETED, Role.AGENT));
    }

    @Test
    void hostStillCompletesAndGuestStillCannot() {
        assertTrue(BookingStateMachine.canTransition(
                BookingStatus.CONFIRMED, BookingStatus.COMPLETED, Role.HOST));
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.CONFIRMED, BookingStatus.COMPLETED, Role.GUEST));
    }

    @Test
    void completedIsTerminalEvenForAnAgent() {
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.COMPLETED, BookingStatus.FORCE_CANCELLED, Role.AGENT));
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.COMPLETED, BookingStatus.COMPLETED, Role.AGENT));
    }
}
