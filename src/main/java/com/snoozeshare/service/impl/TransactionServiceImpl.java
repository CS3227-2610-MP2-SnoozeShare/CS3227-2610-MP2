package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.LedgerRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.service.TransactionService;

public final class TransactionServiceImpl implements TransactionService {

    private final Connection connection;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final WalletRepository wallets;
    private final LedgerWriter ledger;
    private final LedgerRepository ledgerEntries;
    private final EventBus eventBus;

    public TransactionServiceImpl(Connection connection, BookingRepository bookings,
                                  PropertyRepository properties, WalletRepository wallets,
                                  LedgerWriter ledger, LedgerRepository ledgerEntries, EventBus eventBus) {
        this.connection = connection;
        this.bookings = bookings;
        this.properties = properties;
        this.wallets = wallets;
        this.ledger = ledger;
        this.ledgerEntries = ledgerEntries;
        this.eventBus = eventBus;
    }

    @Override
    public WalletTransaction holdEscrow(UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        return inOwnTransaction(() -> ledger.post(guestWallet(booking).walletId(),
                WalletTransactionType.ESCROW_HOLD, booking.totalAmount().negate(), booking.guestId(),
                bookingId, null, null, Instant.now()));
    }

    @Override
    public WalletTransaction refundEscrow(UUID bookingId, BigDecimal refundAmount) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        return inOwnTransaction(() -> ledger.post(guestWallet(booking).walletId(),
                WalletTransactionType.ESCROW_REFUND, refundAmount, booking.guestId(), bookingId, null, null,
                Instant.now()));
    }

    @Override
    public WalletTransaction settleBookingCompletion(UUID bookingId) {
        try {
            WalletTransaction payout = new TransactionManager(connection).inTransaction(current -> {
                Booking booking = bookings.findById(bookingId)
                        .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
                var existing = ledgerEntries.entriesForBooking(bookingId).stream()
                        .filter(txn -> txn.type() == WalletTransactionType.BOOKING_PAYOUT).findFirst();
                if (booking.status() == BookingStatus.COMPLETED) {
                    return existing.orElseThrow(() -> new IllegalStateException("Completed booking has no payout"));
                }
                if (booking.status() != BookingStatus.CONFIRMED) {
                    throw new IllegalStateException("Only confirmed bookings can be completed");
                }
                if (properties == null) {
                    throw new IllegalStateException("Property repository is required for settlement");
                }
                if (existing.isPresent()) {
                    return existing.get();
                }
                var property = properties.findById(booking.listingId())
                        .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
                Wallet hostWallet = wallets.findByUserId(property.hostId())
                        .orElseThrow(() -> new IllegalArgumentException("Host wallet does not exist"));
                BigDecimal gross = booking.totalAmount();
                BigDecimal net = gross.multiply(new BigDecimal("0.97")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal fee = gross.subtract(net).setScale(2, RoundingMode.HALF_UP);
                Instant now = Instant.now();
                WalletTransaction result = ledger.postPayout(hostWallet.walletId(), net, fee, property.hostId(),
                        bookingId, null, now);
                bookings.save(new Booking(booking.bookingId(), booking.listingId(), booking.guestId(),
                        booking.startDate(), booking.endDate(), BookingStatus.COMPLETED,
                        booking.nightlyRateSnapshot(), booking.totalAmount(), booking.createdAt(),
                        booking.decidedAt(), now, booking.hostDecisionMessage()));
                return result;
            });
            publish(payout);
            return payout;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to settle booking completion", exception);
        }
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
        return ledgerEntries.entriesForBooking(bookingId);
    }

    private Wallet guestWallet(Booking booking) {
        return wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
    }

    private WalletTransaction inOwnTransaction(Supplier<WalletTransaction> work) {
        try {
            WalletTransaction transaction = new TransactionManager(connection).inTransaction(current -> work.get());
            publish(transaction);
            return transaction;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to write wallet ledger", exception);
        }
    }

    private void publish(WalletTransaction transaction) {
        if (eventBus != null) {
            eventBus.publish(new WalletTransactionRecordedEvent(transaction.transactionId(),
                    transaction.walletId(), transaction.createdAt()));
        }
    }
}
