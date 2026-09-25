package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.snoozeshare.ui.admin.tickets.ResolutionPreview;

class ResolutionPreviewTest {

    private static final BigDecimal ESCROW = new BigDecimal("480.00");

    @Test
    void validAmountShowsRefundHostPayoutAndFee() {
        ResolutionPreview.Result result = ResolutionPreview.compute(ESCROW, "100");

        assertTrue(result.valid());
        assertEquals("Guest refund SGD 100.00 | Host payout SGD 368.60 (fee SGD 11.40)", result.summary());
        assertEquals(0, new BigDecimal("100").compareTo(result.refund()));
    }

    @Test
    void zeroIsAFullPayoutAndTheEscrowIsAFullRefund() {
        assertEquals("Guest refund SGD 0.00 | Host payout SGD 465.60 (fee SGD 14.40)",
                ResolutionPreview.compute(ESCROW, "0").summary());
        assertEquals("Guest refund SGD 480.00 | Host payout SGD 0.00 (fee SGD 0.00)",
                ResolutionPreview.compute(ESCROW, "480.00").summary());
    }

    @Test
    void blankNonNumericNegativeAndTooLargeAmountsAreInvalidWithAMessage() {
        for (String text : new String[] {null, "", "  ", "abc", "-5", "480.01", "1.234"}) {
            ResolutionPreview.Result result = ResolutionPreview.compute(ESCROW, text);
            assertFalse(result.valid(), String.valueOf(text));
            assertFalse(result.message().isBlank(), String.valueOf(text));
        }
    }
}
