package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.TransactionService;

public final class TransactionServiceImpl implements TransactionService {

    private final WalletLedgerWriter ledger;
    private final BookingRepository bookings;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;

    public TransactionServiceImpl(Connection connection, BookingRepository bookings,
                                   WalletRepository wallets,
                                   WalletTransactionRepository transactions,
                                   EventBus eventBus, AuditService audit) {
        this.ledger = new WalletLedgerWriter(connection, wallets, transactions, eventBus, audit);
        this.bookings = bookings;
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Override
    public WalletTransaction holdEscrow(UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        Wallet guestWallet = wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
        return ledger.record(guestWallet.walletId(), WalletTransactionType.ESCROW_HOLD,
                booking.totalAmount().negate(), BigDecimal.ZERO, bookingId, null,
                booking.guestId());
    }

    @Override
    public WalletTransaction refundEscrow(UUID bookingId, BigDecimal refundAmount) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        Wallet guestWallet = wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
        return ledger.record(guestWallet.walletId(), WalletTransactionType.ESCROW_REFUND,
                refundAmount, BigDecimal.ZERO, bookingId, null, booking.guestId());
    }

    @Override
    public WalletTransaction settleBookingCompletion(UUID bookingId) {
        throw new UnsupportedOperationException("Owned by W8");
    }

    @Override
    public WalletTransaction applyTicketRemedy(UUID ticketId, RemedyType remedy,
                                                BigDecimal amount, UUID agentId) {
        throw new UnsupportedOperationException("Owned by W10");
    }

    @Override
    public WalletTransaction manualOverride(UUID bookingId, BigDecimal amount,
                                             boolean creditGuest, UUID agentId, String reason) {
        throw new UnsupportedOperationException("Owned by W10");
    }

    @Override
    public List<WalletTransaction> historyFor(UUID bookingId) {
        return transactions.findByBookingId(bookingId);
    }
}
