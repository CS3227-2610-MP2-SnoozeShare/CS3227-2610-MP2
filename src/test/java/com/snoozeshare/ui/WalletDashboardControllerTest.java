package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.ui.common.wallet.WalletTransactionFormatter;

class WalletDashboardControllerTest {

    private static final UUID WALLET_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID BOOKING_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID AGENT_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Test
    void formatterLabelsEveryWalletTransactionType() {
        for (WalletTransactionType type : WalletTransactionType.values()) {
            assertEquals(type.name(), WalletTransactionFormatter.typeLabel(type));
        }
    }

    @Test
    void formatterRendersSignedAmountsBalancesAndPayoutFees() {
        assertEquals("+SGD 500.00",
                WalletTransactionFormatter.amountLabel(new BigDecimal("500")));
        assertEquals("-SGD 12.50",
                WalletTransactionFormatter.amountLabel(new BigDecimal("-12.5")));
        assertEquals("SGD 487.50",
                WalletTransactionFormatter.balanceLabel(new BigDecimal("487.5")));
        assertEquals("Fee: SGD 15.00",
                WalletTransactionFormatter.feeLabel(new BigDecimal("15")));
        assertEquals("", WalletTransactionFormatter.feeLabel(BigDecimal.ZERO));
    }

    @Test
    void formatterRendersRelatedBookingTicketAndAgentReferences() {
        WalletTransaction booking = transaction(WalletTransactionType.ESCROW_HOLD,
                BOOKING_ID, null, null);
        WalletTransaction ticket = transaction(WalletTransactionType.TICKET_REMEDY,
                null, TICKET_ID, null);
        WalletTransaction agent = transaction(WalletTransactionType.AGENT_OVERRIDE,
                null, null, AGENT_ID);

        assertEquals("Booking #20000000", WalletTransactionFormatter.relatedLabel(booking));
        assertEquals("Ticket #d0000000", WalletTransactionFormatter.relatedLabel(ticket));
        assertEquals("Agent #c0000000", WalletTransactionFormatter.relatedLabel(agent));
        assertEquals("—", WalletTransactionFormatter.relatedLabel(
                transaction(WalletTransactionType.TOP_UP, null, null, null)));
    }

    @Test
    void formatterSumsOnlyEscrowStillHeldForPendingBookings() {
        UUID releasedBooking = UUID.fromString("20000000-0000-0000-0000-000000000002");
        List<WalletTransaction> transactions = List.of(
                transaction(WalletTransactionType.ESCROW_HOLD, BOOKING_ID, null, null,
                        new BigDecimal("-480.00")),
                transaction(WalletTransactionType.ESCROW_HOLD, releasedBooking, null, null,
                        new BigDecimal("-210.00")),
                transaction(WalletTransactionType.ESCROW_REFUND, releasedBooking, null, null,
                        new BigDecimal("210.00")));

        assertEquals(new BigDecimal("480.00"),
                WalletTransactionFormatter.escrowHeldAmount(transactions));
    }

    @Test
    void commonDashboardControllerOwnsWalletLifecycle() throws Exception {
        Path source = Path.of(
                "src/main/java/com/snoozeshare/ui/common/wallet/WalletDashboardController.java");
        assertTrue(Files.exists(source));
        String controller = Files.readString(source);
        assertTrue(controller.contains("void setContext(AppContext"));
        assertTrue(controller.contains("void cleanup()"));
        assertTrue(controller.contains("WalletTransactionRecordedEvent"));
        assertTrue(controller.contains("Platform.runLater(this::loadData)"));
        assertTrue(controller.contains("subscription.unsubscribe()"));
    }

    private static WalletTransaction transaction(WalletTransactionType type, UUID bookingId,
                                                 UUID ticketId, UUID initiatedBy) {
        return transaction(type, bookingId, ticketId, initiatedBy, new BigDecimal("10.00"));
    }

    private static WalletTransaction transaction(WalletTransactionType type, UUID bookingId,
                                                 UUID ticketId, UUID initiatedBy,
                                                 BigDecimal amount) {
        return new WalletTransaction(UUID.randomUUID(), WALLET_ID, type,
                amount, BigDecimal.ZERO, new BigDecimal("10.00"),
                bookingId, ticketId, initiatedBy, Instant.parse("2026-09-27T00:00:00Z"));
    }
}
