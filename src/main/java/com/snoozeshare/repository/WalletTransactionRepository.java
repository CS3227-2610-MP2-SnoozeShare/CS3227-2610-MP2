package com.snoozeshare.repository;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.WalletTransaction;

public interface WalletTransactionRepository {
    WalletTransaction save(WalletTransaction transaction);

    List<WalletTransaction> findByWalletId(UUID walletId);

    List<WalletTransaction> findByBookingId(UUID bookingId);
}
