package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;

public final class WalletLedgerWriter {

    private final Connection connection;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final EventBus eventBus;

    public WalletLedgerWriter(Connection connection, WalletRepository wallets,
                               WalletTransactionRepository transactions) {
        this.connection = connection;
        this.wallets = wallets;
        this.transactions = transactions;
        this.eventBus = null;
    }

    public WalletLedgerWriter(Connection connection, WalletRepository wallets,
                              WalletTransactionRepository transactions, EventBus eventBus) {
        this.connection = connection;
        this.wallets = wallets;
        this.transactions = transactions;
        this.eventBus = eventBus;
    }

    public WalletTransaction record(UUID walletId, WalletTransactionType type, BigDecimal amount,
                                    BigDecimal feeAmount, UUID relatedBookingId,
                                    UUID relatedTicketId, UUID initiatedBy) {
        if (walletId == null || type == null) {
            throw new IllegalArgumentException("walletId and type are required");
        }
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("amount must not be zero");
        }
        DomainValidation.requireNonNegative(feeAmount, "feeAmount");
        try {
            WalletTransaction transaction = new TransactionManager(connection).inTransaction(current -> {
                Wallet wallet = wallets.findById(walletId)
                        .orElseThrow(() -> new IllegalArgumentException("wallet does not exist"));
                DomainValidation.requireSgd(wallet.currency());
                BigDecimal balanceAfter = wallet.balance().add(amount).subtract(feeAmount);
                if (balanceAfter.signum() < 0) {
                    throw new IllegalArgumentException("insufficient wallet funds");
                }
                Instant now = Instant.now();
                wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter,
                        wallet.currency(), now));
                WalletTransaction entry = new WalletTransaction(UUID.randomUUID(), walletId,
                        type, amount, feeAmount, balanceAfter, relatedBookingId, relatedTicketId,
                        initiatedBy, now);
                return transactions.save(entry);
            });
            if (eventBus != null) {
                eventBus.publish(new WalletTransactionRecordedEvent(transaction.transactionId(),
                        transaction.walletId(), transaction.createdAt()));
            }
            return transaction;
        } catch (SQLException exception) {
            throw new IllegalStateException("unable to write wallet ledger", exception);
        }
    }
}
