package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.TransactionService;

public final class TransactionServiceImpl implements TransactionService {

    private final WalletLedgerWriter ledger;
    private final BookingRepository bookings;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final PropertyRepository properties;
    private final Connection connection;
    private final EventBus eventBus;

    public TransactionServiceImpl(Connection connection, BookingRepository bookings,
                                   WalletRepository wallets,
                                   WalletTransactionRepository transactions,
                                   EventBus eventBus) {
        this(connection, bookings, null, wallets, transactions, eventBus, null);
    }

    public TransactionServiceImpl(Connection connection, BookingRepository bookings,
                                   WalletRepository wallets,
                                   WalletTransactionRepository transactions,
                                   EventBus eventBus, AuditService audit) {
        this(connection, bookings, null, wallets, transactions, eventBus, audit);
    }

    public TransactionServiceImpl(Connection connection, BookingRepository bookings,
                                   PropertyRepository properties, WalletRepository wallets,
                                   WalletTransactionRepository transactions,
                                   EventBus eventBus) {
        this(connection, bookings, properties, wallets, transactions, eventBus, null);
    }

    public TransactionServiceImpl(Connection connection, BookingRepository bookings,
                                   PropertyRepository properties, WalletRepository wallets,
                                   WalletTransactionRepository transactions,
                                   EventBus eventBus, AuditService audit) {
        this.connection = connection;
        this.ledger = new WalletLedgerWriter(connection, wallets, transactions, eventBus,
                audit == null ? new NoOpAuditService() : audit);
        this.bookings = bookings;
        this.properties = properties;
        this.wallets = wallets;
        this.transactions = transactions;
        this.eventBus = eventBus;
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
        try {
            WalletTransaction payout = new TransactionManager(connection).inTransaction(current -> {
                Booking booking = bookings.findById(bookingId)
                        .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
                if (booking.status() == com.snoozeshare.domain.enums.BookingStatus.COMPLETED) {
                    return transactions.findByBookingId(bookingId).stream()
                            .filter(txn -> txn.type() == WalletTransactionType.BOOKING_PAYOUT)
                            .findFirst().orElseThrow(() -> new IllegalStateException(
                                    "Completed booking has no payout"));
                }
                if (booking.status() != com.snoozeshare.domain.enums.BookingStatus.CONFIRMED) {
                    throw new IllegalStateException("Only confirmed bookings can be completed");
                }
                if (properties == null) {
                    throw new IllegalStateException("Property repository is required for settlement");
                }
                var property = properties.findById(booking.listingId())
                        .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
                var existing = transactions.findByBookingId(bookingId).stream()
                        .filter(txn -> txn.type() == WalletTransactionType.BOOKING_PAYOUT)
                        .findFirst();
                if (existing.isPresent()) {
                    return existing.get();
                }
                Wallet hostWallet = wallets.findByUserId(property.hostId())
                        .orElseThrow(() -> new IllegalArgumentException("Host wallet does not exist"));
                BigDecimal gross = booking.totalAmount();
                BigDecimal net = gross.multiply(new BigDecimal("0.97"))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal fee = gross.subtract(net).setScale(2, java.math.RoundingMode.HALF_UP);
                Instant now = Instant.now();
                BigDecimal balanceAfter = hostWallet.balance().add(net);
                wallets.save(new Wallet(hostWallet.walletId(), hostWallet.userId(), balanceAfter,
                        hostWallet.currency(), now));
                WalletTransaction result = transactions.save(new WalletTransaction(
                        UUID.randomUUID(), hostWallet.walletId(), WalletTransactionType.BOOKING_PAYOUT,
                        net, fee, balanceAfter, bookingId, null, property.hostId(), now));
                bookings.save(new Booking(booking.bookingId(), booking.listingId(), booking.guestId(),
                        booking.startDate(), booking.endDate(),
                        com.snoozeshare.domain.enums.BookingStatus.COMPLETED,
                        booking.nightlyRateSnapshot(), booking.totalAmount(), booking.createdAt(),
                        booking.decidedAt(), now, booking.hostDecisionMessage()));
                return result;
            });
            if (eventBus != null) {
                eventBus.publish(new WalletTransactionRecordedEvent(payout.transactionId(),
                        payout.walletId(), payout.createdAt()));
            }
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
        return transactions.findByBookingId(bookingId);
    }
}
