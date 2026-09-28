package com.snoozeshare.repository;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.WalletTransaction;

/** Read side of the ledger: the money rows of the audit log, oldest first (insertion order). */
public interface LedgerRepository {
    List<WalletTransaction> entriesForWallet(UUID walletId);

    List<WalletTransaction> entriesForBooking(UUID bookingId);
}
