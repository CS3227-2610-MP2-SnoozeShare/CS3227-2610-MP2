package com.snoozeshare.service.impl;

import java.sql.Connection;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.AccountGovernanceService;
import com.snoozeshare.service.AccountSummary;
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
        throw new UnsupportedOperationException("Task 5");
    }

    @Override
    public User reactivate(UUID userId, UUID agentId, String reason) {
        throw new UnsupportedOperationException("Task 5");
    }
}
