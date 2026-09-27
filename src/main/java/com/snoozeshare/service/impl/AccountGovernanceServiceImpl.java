package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.statemachine.BookingStateMachine;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.infra.events.DomainEvent;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.AccountStatusChangedEvent;
import com.snoozeshare.infra.events.events.BookingCancelledEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.AccountGovernanceService;
import com.snoozeshare.service.AccountSummary;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;

public final class AccountGovernanceServiceImpl implements AccountGovernanceService {

    static final String CASCADE_BOOKING_REASON = "Account suspended — cascading cancellation";
    static final String CASCADE_LISTING_REASON = "Host suspended";

    private final Connection connection;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final AvailabilityBlockRepository blocks;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final EventBus eventBus;
    private final AuditService audit;
    private final Clock clock;

    public AccountGovernanceServiceImpl(Connection connection, UserRepository users, BookingRepository bookings,
                                        PropertyRepository properties, AvailabilityBlockRepository blocks,
                                        WalletRepository wallets, WalletTransactionRepository transactions,
                                        EventBus eventBus, AuditService audit, Clock clock) {
        this.connection = connection;
        this.users = users;
        this.bookings = bookings;
        this.properties = properties;
        this.blocks = blocks;
        this.wallets = wallets;
        this.transactions = transactions;
        this.eventBus = eventBus;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<AccountSummary> listAccounts() {
        return users.findAll().stream()
                .filter(user -> !user.userId().equals(AuditService.SYSTEM_ACTOR_ID))
                .map(AccountSummary::from)
                .toList();
    }

    @Override
    public User suspend(UUID userId, UUID agentId, String reason) {
        String text = requireReason(reason);
        requireActiveAgent(agentId);
        User target = requireGovernable(userId);
        if (target.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is already suspended");
        }
        List<DomainEvent> events = new ArrayList<>();
        User suspended = inTransaction("suspend account", () -> {
            Instant now = clock.instant();
            User saved = users.save(withStatus(target, AccountStatus.SUSPENDED, text));
            audit.record(AuditRecord.builder(agentId, AuditAction.ACCOUNT_SUSPENDED, "User", userId)
                    .status(AccountStatus.ACTIVE, AccountStatus.SUSPENDED).reason(text).subject(userId)
                    .at(now).build());
            cascade(target, agentId, now, events);
            return saved;
        });
        events.add(new AccountStatusChangedEvent(userId, AccountStatus.SUSPENDED, agentId, clock.instant()));
        events.forEach(this::publish);
        return suspended;
    }

    @Override
    public User reactivate(UUID userId, UUID agentId, String reason) {
        String text = requireReason(reason);
        requireActiveAgent(agentId);
        User target = requireGovernable(userId);
        if (target.accountStatus() != AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Account is not suspended");
        }
        User reactivated = inTransaction("reactivate account", () -> {
            Instant now = clock.instant();
            User saved = users.save(withStatus(target, AccountStatus.ACTIVE, null));
            audit.record(AuditRecord.builder(agentId, AuditAction.ACCOUNT_REACTIVATED, "User", userId)
                    .status(AccountStatus.SUSPENDED, AccountStatus.ACTIVE).reason(text).subject(userId)
                    .at(now).build());
            return saved;
        });
        publish(new AccountStatusChangedEvent(userId, AccountStatus.ACTIVE, agentId, clock.instant()));
        return reactivated;
    }

    /** The suspended user's PENDING and not-yet-started CONFIRMED bookings are force-cancelled with a refund. */
    private void cascade(User target, UUID agentId, Instant now, List<DomainEvent> events) {
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        List<Booking> affected = new ArrayList<>();
        if (target.role() == Role.GUEST) {
            affected.addAll(bookings.findByGuest(target.userId()));
        } else {
            for (Property property : properties.findByHostId(target.userId())) {
                affected.addAll(bookings.findByListing(property.propertyId()));
                if (property.status() == ListingStatus.ACTIVE) {
                    deactivate(property, target, agentId, now);
                }
            }
        }
        for (Booking booking : affected) {
            if (isCancellable(booking, today)) {
                forceCancel(booking, target, agentId, now, events);
            }
        }
    }

    private void deactivate(Property property, User host, UUID agentId, Instant now) {
        properties.save(new Property(property.propertyId(), property.hostId(), ListingStatus.INACTIVE,
                property.title(), property.description(), property.propertyType(), property.streetAddress(),
                property.city(), property.region(), property.postalCode(), property.maxGuests(),
                property.bedrooms(), property.bathrooms(), property.baseNightlyRate(), property.checkInTime(),
                property.checkOutTime(), property.amenities(), property.createdAt()));
        audit.record(AuditRecord.builder(agentId, AuditAction.LISTING_STATUS_CASCADE, "Property",
                        property.propertyId())
                .status(ListingStatus.ACTIVE, ListingStatus.INACTIVE).reason(CASCADE_LISTING_REASON)
                .subject(host.userId()).at(now).build());
    }

    /** PENDING, or CONFIRMED with a check-in date after today. Started and ended stays belong to W10 (C36). */
    static boolean isCancellable(Booking booking, LocalDate today) {
        return booking.status() == BookingStatus.PENDING
                || (booking.status() == BookingStatus.CONFIRMED && booking.startDate().isAfter(today));
    }

    private void forceCancel(Booking booking, User suspended, UUID agentId, Instant now, List<DomainEvent> events) {
        if (!BookingStateMachine.canTransition(booking.status(), BookingStatus.FORCE_CANCELLED, Role.AGENT)) {
            throw new IllegalStateException("Cannot force-cancel a booking in status " + booking.status());
        }
        bookings.save(new Booking(booking.bookingId(), booking.listingId(), booking.guestId(),
                booking.startDate(), booking.endDate(), BookingStatus.FORCE_CANCELLED,
                booking.nightlyRateSnapshot(), booking.totalAmount(), booking.createdAt(), now, null));
        blocks.deleteByBookingId(booking.bookingId());
        audit.record(AuditRecord.builder(agentId, AuditAction.BOOKING_FORCE_CANCELLED, "Booking",
                        booking.bookingId())
                .status(booking.status(), BookingStatus.FORCE_CANCELLED).reason(CASCADE_BOOKING_REASON)
                .subject(suspended.userId()).booking(booking.bookingId()).at(now).build());
        // Inline wallet write: WalletLedgerWriter opens its own transaction, which would commit early here.
        Wallet wallet = wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
        BigDecimal balanceAfter = wallet.balance().add(booking.totalAmount());
        wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter, wallet.currency(), now));
        WalletTransaction refund = transactions.save(new WalletTransaction(UUID.randomUUID(), wallet.walletId(),
                WalletTransactionType.ESCROW_REFUND, booking.totalAmount(), BigDecimal.ZERO, balanceAfter,
                booking.bookingId(), null, agentId, now));
        audit.recordWalletTransaction(agentId, booking.guestId(), refund, refund.amount(), null);
        events.add(new WalletTransactionRecordedEvent(refund.transactionId(), refund.walletId(), now));
        events.add(new BookingCancelledEvent(booking.bookingId(), agentId, now));
    }

    private <T> T inTransaction(String what, java.util.concurrent.Callable<T> work) {
        try {
            return new TransactionManager(connection).inTransaction(conn -> {
                try {
                    return work.call();
                } catch (SQLException | RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to " + what, exception);
        }
    }

    private void publish(DomainEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required");
        }
        return reason.trim();
    }

    private void requireActiveAgent(UUID agentId) {
        User agent = users.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent does not exist"));
        if (agent.role() != Role.AGENT) {
            throw new IllegalArgumentException("Only a support agent can manage accounts");
        }
        if (agent.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }

    private User requireGovernable(UUID userId) {
        User target = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        if (target.role() == Role.AGENT || target.userId().equals(AuditService.SYSTEM_ACTOR_ID)) {
            throw new IllegalArgumentException("Support agent accounts cannot be suspended or reactivated");
        }
        return target;
    }

    private static User withStatus(User user, AccountStatus status, String reason) {
        return new User(user.userId(), user.role(), user.displayName(), user.email(), status,
                user.registrationCode(), user.createdAt(), reason);
    }
}
