package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.WalletService;

public final class WalletServiceImpl implements WalletService {

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final WalletLedgerWriter ledger;

    public WalletServiceImpl(Connection connection, WalletRepository wallets,
                             WalletTransactionRepository transactions) {
        this(connection, wallets, transactions, null);
    }

    public WalletServiceImpl(Connection connection, WalletRepository wallets,
                             WalletTransactionRepository transactions, EventBus eventBus) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.ledger = new WalletLedgerWriter(connection, wallets, transactions, eventBus);
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
        Wallet wallet = getWallet(userId);
        return ledger.record(wallet.walletId(), WalletTransactionType.TOP_UP, amount,
                BigDecimal.ZERO, null, null, userId);
    }

    @Override
    public WalletTransaction withdraw(UUID userId, BigDecimal amount) {
        DomainValidation.requirePositive(amount, "amount");
        Wallet wallet = getWallet(userId);
        return ledger.record(wallet.walletId(), WalletTransactionType.WITHDRAWAL, amount.negate(),
                BigDecimal.ZERO, null, null, userId);
    }

    @Override
    public List<WalletTransaction> statementFor(UUID userId) {
        return transactions.findByWalletId(getWallet(userId).walletId());
    }
}
