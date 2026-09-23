package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;

public interface WalletService {
    Wallet getWallet(UUID userId);

    BigDecimal balanceOf(UUID userId);

    WalletTransaction topUp(UUID userId, BigDecimal amount);

    WalletTransaction withdraw(UUID userId, BigDecimal amount);

    List<WalletTransaction> statementFor(UUID userId);
}
