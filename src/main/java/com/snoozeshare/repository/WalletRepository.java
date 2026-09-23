package com.snoozeshare.repository;

import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.Wallet;

public interface WalletRepository {
    Optional<Wallet> findById(UUID walletId);

    Optional<Wallet> findByUserId(UUID userId);

    Wallet save(Wallet wallet);
}
