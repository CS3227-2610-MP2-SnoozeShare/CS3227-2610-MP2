package com.snoozeshare.domain.statemachine;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.Role;

public final class BookingStateMachine {

    private BookingStateMachine() {
    }

    public static boolean canTransition(BookingStatus from, BookingStatus to, Role actingRole) {
        if (from == null || to == null || actingRole == null || isTerminal(from)) {
            return false;
        }
        return switch (from) {
            case PENDING -> switch (to) {
                case CONFIRMED, REJECTED -> actingRole == Role.HOST;
                case CANCELLED_BY_GUEST -> actingRole == Role.GUEST;
                case FORCE_CANCELLED -> actingRole == Role.AGENT;
                default -> false;
            };
            case CONFIRMED -> switch (to) {
                case CANCELLED_BY_GUEST -> actingRole == Role.GUEST;
                case CANCELLED_BY_HOST -> actingRole == Role.HOST;
                case COMPLETED -> actingRole == Role.HOST || actingRole == Role.AGENT;
                case FORCE_CANCELLED, FORCE_COMPLETED -> actingRole == Role.AGENT;
                default -> false;
            };
            default -> false;
        };
    }

    private static boolean isTerminal(BookingStatus status) {
        return switch (status) {
            case REJECTED, CANCELLED_BY_GUEST, CANCELLED_BY_HOST, COMPLETED,
                    FORCE_CANCELLED, FORCE_COMPLETED -> true;
            default -> false;
        };
    }
}
