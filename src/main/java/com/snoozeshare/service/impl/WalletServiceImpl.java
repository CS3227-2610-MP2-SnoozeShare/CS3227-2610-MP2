package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.LedgerRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.service.WalletService;

public final class WalletServiceImpl implements WalletService {

    private final Connection connection;
    private final WalletRepository wallets;
    private final LedgerWriter ledger;
    private final LedgerRepository ledgerEntries;
    private final EventBus eventBus;

    public WalletServiceImpl(Connection connection, WalletRepository wallets, LedgerWriter ledger,
                             LedgerRepository ledgerEntries, EventBus eventBus) {
        this.connection = connection;
        this.wallets = wallets;
        this.ledger = ledger;
        this.ledgerEntries = ledgerEntries;
        this.eventBus = eventBus;
    }

    @Override
    public Wallet getWallet(UUID userId) {
        return wallets.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet does not exist"));
    }

    @Override
    public BigDecimal balanceOf(UUID userId) {
        return getWallet(userId).balance();
    }

    @Override
    public WalletTransaction topUp(UUID userId, BigDecimal amount) {
        DomainValidation.requirePositive(amount, "amount");
        return move(userId, WalletTransactionType.TOP_UP, amount);
    }

    @Override
    public WalletTransaction withdraw(UUID userId, BigDecimal amount) {
        DomainValidation.requirePositive(amount, "amount");
        return move(userId, WalletTransactionType.WITHDRAWAL, amount.negate());
    }

    @Override
    public List<WalletTransaction> statementFor(UUID userId) {
        return ledgerEntries.entriesForWallet(getWallet(userId).walletId());
    }

    private WalletTransaction move(UUID userId, WalletTransactionType type, BigDecimal amount) {
        Wallet wallet = getWallet(userId);
        try {
            WalletTransaction transaction = new TransactionManager(connection).inTransaction(current ->
                    ledger.post(wallet.walletId(), type, amount, userId, null, null, null, Instant.now()));
            if (eventBus != null) {
                eventBus.publish(new WalletTransactionRecordedEvent(transaction.transactionId(),
                        transaction.walletId(), transaction.createdAt()));
            }
            return transaction;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to write wallet ledger", exception);
        }
    }
}
