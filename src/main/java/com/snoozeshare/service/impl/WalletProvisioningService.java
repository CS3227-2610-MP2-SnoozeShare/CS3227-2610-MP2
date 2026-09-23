package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.repository.WalletRepository;

public final class WalletProvisioningService {

    private final WalletRepository wallets;

    public WalletProvisioningService(WalletRepository wallets) {
        this.wallets = wallets;
    }

    public Wallet provisionIfRequired(User user) {
        if (user.role() == Role.AGENT) {
            return null;
        }
        if (wallets.findByUserId(user.userId()).isPresent()) {
            throw new IllegalStateException("User already has a wallet");
        }
        return wallets.save(new Wallet(
                UUID.randomUUID(),
                user.userId(),
                BigDecimal.ZERO,
                "SGD",
                Instant.now()));
    }
}
