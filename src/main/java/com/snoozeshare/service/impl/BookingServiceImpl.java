package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.statemachine.BookingStateMachine;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.BookingCancelledEvent;
import com.snoozeshare.infra.events.events.BookingConfirmedEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.ReviewRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.BookingService;
import com.snoozeshare.service.HostBookingRow;
import com.snoozeshare.service.Money;
import com.snoozeshare.service.TripFilter;

public final class BookingServiceImpl implements BookingService {

    private final Connection connection;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final AvailabilityBlockRepository blocks;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final UserRepository users;
    private final ReviewRepository reviews;
    private final com.snoozeshare.service.TransactionService transactionService;
    private final TicketRepository tickets;
    private final EventBus eventBus;

    public BookingServiceImpl(Connection connection, BookingRepository bookings,
                               PropertyRepository properties,
                               AvailabilityBlockRepository blocks,
                               WalletRepository wallets,
                               WalletTransactionRepository transactions,
                               UserRepository users,
                               ReviewRepository reviews,
                               EventBus eventBus,
                               com.snoozeshare.service.TransactionService transactionService,
                               TicketRepository tickets) {
        this.connection = connection;
        this.bookings = bookings;
        this.properties = properties;
        this.blocks = blocks;
        this.wallets = wallets;
        this.transactions = transactions;
        this.users = users;
        this.reviews = reviews;
        this.eventBus = eventBus;
        this.transactionService = transactionService;
        this.tickets = tickets;
    }

    @Override
    public Booking submitRequest(UUID guestId, UUID propertyId,
                                  LocalDate start, LocalDate end) {
        DomainValidation.requireDateRange(start, end);
        try {
            Booking booking = new TransactionManager(connection).inTransaction(conn -> {
                Property property = properties.findById(propertyId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Property does not exist"));

                if (!bookings.findOverlapping(propertyId, start, end).isEmpty()
                        || !blocks.findOverlapping(propertyId, start, end).isEmpty()) {
                    throw new IllegalArgumentException(
                            "Dates are not available for this property");
                }

                long nights = ChronoUnit.DAYS.between(start, end);
                BigDecimal nightlyRate = property.baseNightlyRate();
                BigDecimal totalAmount = nightlyRate.multiply(BigDecimal.valueOf(nights));

                Instant now = Instant.now();
                UUID bookingId = UUID.randomUUID();

                Booking newBooking = new Booking(bookingId, propertyId, guestId,
                        start, end, BookingStatus.PENDING, nightlyRate, totalAmount,
                        now, null, null);
                bookings.save(newBooking);

                blocks.save(new AvailabilityBlock(UUID.randomUUID(), propertyId,
                        start, end, "BOOKING", bookingId, null));

                // Inline wallet write — cannot use WalletLedgerWriter here because
                // it opens its own transaction which would cause premature commit
                // on SQLite's single shared connection.
                Wallet wallet = wallets.findByUserId(guestId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Guest wallet does not exist"));
                DomainValidation.requireSgd(wallet.currency());
                BigDecimal balanceAfter = wallet.balance().subtract(totalAmount);
                if (balanceAfter.signum() < 0) {
                    throw new IllegalArgumentException("Insufficient wallet funds");
                }
                wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter,
                        wallet.currency(), now));
                transactions.save(new WalletTransaction(UUID.randomUUID(), wallet.walletId(),
                        WalletTransactionType.ESCROW_HOLD, totalAmount.negate(),
                        BigDecimal.ZERO, balanceAfter, bookingId, null, guestId, now));

                return newBooking;
            });
            publishWalletTransactionEvent(booking);
            return booking;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to submit booking request", exception);
        }
    }

    private void publishWalletTransactionEvent(Booking booking) {
        if (eventBus == null) {
            return;
        }
        WalletTransaction transaction = transactions.findByBookingId(booking.bookingId()).stream()
                .filter(entry -> entry.type() == WalletTransactionType.ESCROW_HOLD)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Escrow transaction was not recorded"));
        eventBus.publish(new WalletTransactionRecordedEvent(transaction.transactionId(),
                transaction.walletId(), transaction.createdAt()));
    }

    @Override
    public List<Booking> tripsFor(UUID guestId, TripFilter filter) {
        List<Booking> all = bookings.findByGuest(guestId);
        if (filter == null || filter.status() == null) {
            return all;
        }
        return all.stream()
                .filter(b -> b.status() == filter.status())
                .toList();
    }

    @Override
    public List<Booking> pendingRequestsFor(UUID hostId) {
        return bookings.findByHostPending(hostId);
    }

    @Override
    public List<HostBookingRow> pendingRequestRowsFor(UUID hostId) {
        return toHostRows(bookings.findByHostPending(hostId));
    }

    @Override
    public List<HostBookingRow> historyRowsFor(UUID hostId) {
        return toHostRows(bookings.findByHost(hostId));
    }

    private List<HostBookingRow> toHostRows(List<Booking> source) {
        return source.stream().map(booking -> {
            Property property = properties.findById(booking.listingId())
                    .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
            String guestName = users.findById(booking.guestId())
                    .orElseThrow(() -> new IllegalArgumentException("Guest does not exist"))
                    .displayName();
            List<com.snoozeshare.domain.model.Review> guestReviews =
                    reviews.findByGuestId(booking.guestId());
            OptionalDouble rating = guestReviews.isEmpty() ? OptionalDouble.empty()
                    : OptionalDouble.of(guestReviews.stream().mapToInt(
                            com.snoozeshare.domain.model.Review::rating).average().orElse(0));
            long nights = ChronoUnit.DAYS.between(booking.startDate(), booking.endDate());
            BigDecimal net = booking.totalAmount().multiply(new BigDecimal("0.97"))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            return new HostBookingRow(booking, guestName, property.title(), nights,
                    booking.totalAmount(), net, rating);
        }).toList();
    }

    @Override
    public Booking decide(UUID bookingId, boolean approve, UUID hostId) {
        return decide(bookingId, approve, hostId, null);
    }

    @Override
    public Booking decide(UUID bookingId, boolean approve, UUID hostId,
                          String hostDecisionMessage) {
        try {
            Booking decided = new TransactionManager(connection).inTransaction(conn -> {
                Booking booking = bookings.findById(bookingId)
                        .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
                Property property = properties.findById(booking.listingId())
                        .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
                if (!property.hostId().equals(hostId)) {
                    throw new IllegalArgumentException("Only the property host can decide");
                }
                BookingStatus target = approve ? BookingStatus.CONFIRMED : BookingStatus.REJECTED;
                if (!BookingStateMachine.canTransition(booking.status(), target, Role.HOST)) {
                    throw new IllegalStateException("Cannot transition from " + booking.status()
                            + " to " + target);
                }
                Instant now = Instant.now();
                String message = approve || hostDecisionMessage == null
                        ? null : hostDecisionMessage.trim();
                if (message != null && message.isEmpty()) {
                    message = null;
                }
                Booking updated = new Booking(booking.bookingId(), booking.listingId(),
                        booking.guestId(), booking.startDate(), booking.endDate(),
                        target, booking.nightlyRateSnapshot(), booking.totalAmount(),
                        booking.createdAt(), now, null, message);
                bookings.save(updated);

                if (!approve) {
                    // Reject: 100% refund (decision C8) + remove block
                    blocks.deleteByBookingId(bookingId);
                    Wallet wallet = wallets.findByUserId(booking.guestId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Guest wallet does not exist"));
                    BigDecimal balanceAfter = wallet.balance().add(booking.totalAmount());
                    wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter,
                            wallet.currency(), now));
                    transactions.save(new WalletTransaction(UUID.randomUUID(),
                            wallet.walletId(), WalletTransactionType.ESCROW_REFUND,
                            booking.totalAmount(), BigDecimal.ZERO, balanceAfter,
                            bookingId, null, hostId, now));
                }

                return updated;
            });
            if (eventBus != null) {
                if (approve) {
                    eventBus.publish(new BookingConfirmedEvent(bookingId, hostId,
                            Instant.now()));
                } else {
                    eventBus.publish(new BookingCancelledEvent(bookingId, hostId,
                            Instant.now()));
                }
            }
            return decided;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to decide on booking", exception);
        }
    }

    @Override
    public Booking cancel(UUID bookingId, UUID actingGuestId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        if (!booking.guestId().equals(actingGuestId)) {
            throw new IllegalArgumentException("Only the booking guest can cancel");
        }
        if (!BookingStateMachine.canTransition(booking.status(),
                BookingStatus.CANCELLED_BY_GUEST, Role.GUEST)) {
            throw new IllegalStateException("Cannot cancel booking in status "
                    + booking.status());
        }
        BigDecimal refundAmount = calculateRefundAmount(booking);
        try {
            Booking cancelled = new TransactionManager(connection).inTransaction(conn -> {
                Instant now = Instant.now();
                Booking updated = new Booking(booking.bookingId(), booking.listingId(),
                        booking.guestId(), booking.startDate(), booking.endDate(),
                        BookingStatus.CANCELLED_BY_GUEST, booking.nightlyRateSnapshot(),
                        booking.totalAmount(), booking.createdAt(), now, null);
                bookings.save(updated);

                blocks.deleteByBookingId(bookingId);

                // Inline wallet refund
                Wallet wallet = wallets.findByUserId(actingGuestId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Guest wallet does not exist"));
                BigDecimal balanceAfter = wallet.balance().add(refundAmount);
                wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter,
                        wallet.currency(), now));
                transactions.save(new WalletTransaction(UUID.randomUUID(), wallet.walletId(),
                        WalletTransactionType.ESCROW_REFUND, refundAmount,
                        BigDecimal.ZERO, balanceAfter, bookingId, null, actingGuestId, now));

                return updated;
            });
            if (eventBus != null) {
                eventBus.publish(new BookingCancelledEvent(bookingId, actingGuestId,
                        Instant.now()));
            }
            return cancelled;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to cancel booking", exception);
        }
    }

    static BigDecimal calculateRefundAmount(Booking booking) {
        if (booking.status() == BookingStatus.PENDING) {
            return booking.totalAmount();
        }
        long hoursUntilCheckIn = ChronoUnit.HOURS.between(
                Instant.now(), booking.startDate().atStartOfDay()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant());
        if (hoursUntilCheckIn > 48) {
            return booking.totalAmount();
        }
        return booking.totalAmount().divide(BigDecimal.valueOf(2));
    }

    @Override
    public Booking complete(UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        if (booking.status() == BookingStatus.COMPLETED) {
            return booking;
        }
        if (booking.status() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be completed");
        }
        if (booking.endDate().isAfter(LocalDate.now().minusDays(7))) {
            throw new IllegalStateException("Booking is not eligible for completion");
        }
        boolean blocked = tickets.findByBookingId(bookingId).stream()
                .anyMatch(ticket -> ticket.status() == com.snoozeshare.domain.enums.TicketStatus.OPEN
                        || ticket.status() == com.snoozeshare.domain.enums.TicketStatus.IN_REVIEW);
        if (blocked) {
            throw new IllegalStateException("Open dispute blocks booking completion");
        }
        transactionService.settleBookingCompletion(bookingId);
        return bookings.findById(bookingId).orElseThrow();
    }

    @Override
    public int completeEligibleBookings() {
        int completed = 0;
        LocalDate cutoff = LocalDate.now().minusDays(7);
        for (Booking booking : bookings.findConfirmedEndingOnOrBefore(cutoff)) {
            try {
                complete(booking.bookingId());
                completed++;
            } catch (IllegalStateException ignored) {
                // Open disputes and transiently ineligible rows remain held for a later sweep.
            }
        }
        return completed;
    }

    @Override
    public Booking forceTransition(UUID bookingId, BookingStatus target,
                                    UUID agentId, String reason) {
        throw new UnsupportedOperationException("Owned by W10");
    }

    @Override
    public Money previewHostEarnings(UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        return new Money(booking.totalAmount().multiply(new BigDecimal("0.97"))
                .setScale(2, java.math.RoundingMode.HALF_UP), "SGD");
    }
}
