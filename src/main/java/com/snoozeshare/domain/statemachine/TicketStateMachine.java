package com.snoozeshare.domain.statemachine;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;

public final class TicketStateMachine {

    private TicketStateMachine() {
    }

    public static boolean canTransition(TicketStatus from, TicketStatus to, Role actingRole) {
        if (from == null || to == null || actingRole != Role.AGENT) {
            return false;
        }
        return switch (from) {
            case OPEN -> to == TicketStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> to == TicketStatus.RESOLVED_APPROVED
                    || to == TicketStatus.RESOLVED_REJECTED
                    || to == TicketStatus.OPEN;
            case RESOLVED_APPROVED, RESOLVED_REJECTED -> false;
        };
    }
}
