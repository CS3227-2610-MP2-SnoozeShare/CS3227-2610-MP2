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
import com.snoozeshare.service.AuditService;

public final class WalletLedgerWriter {

    private final Connection connection;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final EventBus eventBus;
    private final AuditService audit;

    public WalletLedgerWriter(Connection connection, WalletRepository wallets,
                              WalletTransactionRepository transactions, EventBus eventBus,
                              AuditService audit) {
        this.connection = connection;
        this.wallets = wallets;
        this.transactions = transactions;
        this.eventBus = eventBus;
        this.audit = audit;
    }

    public WalletTransaction record(UUID walletId, WalletTransactionType type, BigDecimal amount,
                                    BigDecimal feeAmount, UUID relatedBookingId,
                                    UUID relatedTicketId, UUID initiatedBy) {
        if (walletId == null || type == null) {
            throw new IllegalArgumentException("WalletId and type are required");
        }
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("Amount must not be zero");
        }
        DomainValidation.requireNonNegative(feeAmount, "feeAmount");
        try {
            WalletTransaction transaction = new TransactionManager(connection).inTransaction(current -> {
                Wallet wallet = wallets.findById(walletId)
                        .orElseThrow(() -> new IllegalArgumentException("Wallet does not exist"));
                DomainValidation.requireSgd(wallet.currency());
                BigDecimal balanceAfter = wallet.balance().add(amount).subtract(feeAmount);
                if (balanceAfter.signum() < 0) {
                    throw new IllegalArgumentException("Insufficient wallet funds");
                }
                Instant now = Instant.now();
                wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter,
                        wallet.currency(), now));
                WalletTransaction entry = new WalletTransaction(UUID.randomUUID(), walletId,
                        type, amount, feeAmount, balanceAfter, relatedBookingId, relatedTicketId,
                        initiatedBy, now);
                WalletTransaction saved = transactions.save(entry);
                UUID actor = initiatedBy == null ? wallet.userId() : initiatedBy;
                String feeNote = feeAmount.signum() > 0 ? "Fee deducted: " + feeAmount.toPlainString() : null;
                audit.recordWalletTransaction(actor, wallet.userId(), saved, amount.subtract(feeAmount), feeNote);
                return saved;
            });
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
