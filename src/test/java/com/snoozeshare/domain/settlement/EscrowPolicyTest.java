package com.snoozeshare.domain.settlement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.WalletTransaction;

class EscrowPolicyTest {

    private static WalletTransaction tx(WalletTransactionType type) {
        return new WalletTransaction(UUID.randomUUID(), UUID.randomUUID(), type,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, UUID.randomUUID(), null, null,
                Instant.parse("2026-09-25T00:00:00Z"));
    }

    @Test
    void escrowIsHeldWhenOnlyAHoldExists() {
        assertTrue(EscrowPolicy.isHeld(List.of(tx(WalletTransactionType.ESCROW_HOLD))));
    }

    @Test
    void escrowIsNotHeldWithNoHold() {
        assertFalse(EscrowPolicy.isHeld(List.of()));
        assertFalse(EscrowPolicy.isHeld(List.of(tx(WalletTransactionType.TOP_UP))));
    }

    @Test
    void anyReleasingRowMeansEscrowIsNoLongerHeld() {
        for (WalletTransactionType releasing : List.of(WalletTransactionType.ESCROW_REFUND,
                WalletTransactionType.BOOKING_PAYOUT, WalletTransactionType.TICKET_REMEDY,
                WalletTransactionType.AGENT_OVERRIDE)) {
            assertFalse(EscrowPolicy.isHeld(List.of(tx(WalletTransactionType.ESCROW_HOLD), tx(releasing))),
                    releasing.name());
        }
    }
}
