package com.snoozeshare.service;

import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.settlement.SettlementBreakdown;

/**
 * The committed outcome of a settlement. Either transaction is null when its amount was zero.
 */
public record Settlement(
        Ticket ticket,
        Booking booking,
        SettlementBreakdown breakdown,
        WalletTransaction guestTransaction,
        WalletTransaction hostTransaction
) {
}
