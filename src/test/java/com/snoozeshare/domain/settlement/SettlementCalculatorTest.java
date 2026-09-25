package com.snoozeshare.domain.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SettlementCalculatorTest {

    @ParameterizedTest
    @CsvSource({
        "480.00, 100.00, 100.00, 380.00, 11.40, 368.60",
        "330.00, 165.00, 165.00, 165.00, 4.95, 160.05",
        "875.00, 0, 0.00, 875.00, 26.25, 848.75",
        "210.00, 210.00, 210.00, 0.00, 0.00, 0.00",
        "100.01, 0, 0.00, 100.01, 3.00, 97.01",
        "0.50, 0, 0.00, 0.50, 0.02, 0.48"
    })
    void splitsEscrowIntoGuestRefundAndHostPayoutNetOfThreePercent(
            String escrow, String refund, String expectedRefund, String expectedGross,
            String expectedFee, String expectedNet) {
        SettlementBreakdown split = SettlementCalculator.split(
                new BigDecimal(escrow), new BigDecimal(refund));

        assertEquals(0, new BigDecimal(expectedRefund).compareTo(split.guestRefund()));
        assertEquals(0, new BigDecimal(expectedGross).compareTo(split.hostGross()));
        assertEquals(0, new BigDecimal(expectedFee).compareTo(split.fee()));
        assertEquals(0, new BigDecimal(expectedNet).compareTo(split.hostNet()));
        assertEquals(0, split.escrow().compareTo(split.guestRefund().add(split.hostGross())));
    }

    @Test
    void rejectsRefundAboveEscrow() {
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                new BigDecimal("210.00"), new BigDecimal("210.01")));
    }

    @Test
    void rejectsNegativeRefundAndNonPositiveEscrow() {
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                new BigDecimal("210.00"), new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                new BigDecimal("210.00"), null));
    }

    @Test
    void rejectsRefundWithMoreThanTwoDecimalPlaces() {
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                new BigDecimal("210.00"), new BigDecimal("10.005")));
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimalPlaces() {
        SettlementBreakdown split = SettlementCalculator.split(
                new BigDecimal("210.00"), new BigDecimal("10.500"));

        assertEquals(0, new BigDecimal("10.50").compareTo(split.guestRefund()));
    }
}
