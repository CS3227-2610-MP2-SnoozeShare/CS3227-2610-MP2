package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;

/**
 * The only code that changes a wallet balance. It runs on the caller's connection and never opens a
 * transaction: the caller wraps it (and any state change it belongs with) in one, so the balance, the money
 * row and the state change commit or roll back together. Callers publish events after their commit.
 */
public final class LedgerWriter {

    private final WalletRepository wallets;
    private final AuditService audit;

    public LedgerWriter(WalletRepository wallets, AuditService audit) {
        this.wallets = wallets;
        this.audit = audit;
    }

    /** Moves {@code amount} (signed) in one wallet and records the money row. */
    public WalletTransaction post(UUID walletId, WalletTransactionType type, BigDecimal amount, UUID actorId,
                                  UUID bookingId, UUID ticketId, String reason, Instant at) {
        if (walletId == null || type == null || actorId == null) {
            throw new IllegalArgumentException("Wallet, type and actor are required");
        }
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("Amount must not be zero");
        }
        Wallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet does not exist"));
        DomainValidation.requireSgd(wallet.currency());
        BigDecimal balanceAfter = wallet.balance().add(amount);
        if (balanceAfter.signum() < 0) {
            throw new IllegalArgumentException("Insufficient wallet funds");
        }
        wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter, wallet.currency(), at));
        UUID transactionId = UUID.randomUUID();
        audit.record(AuditRecord.builder(actorId, AuditAction.forWallet(type), "WalletTransaction", transactionId)
                .wallet(amount, balanceAfter).reason(reason).subject(wallet.userId())
                .booking(bookingId).ticket(ticketId).at(at).build());
        return new WalletTransaction(transactionId, walletId, type, amount, BigDecimal.ZERO, balanceAfter,
                bookingId, ticketId, actorId, at);
    }

    /**
     * Pays a host {@code net} and credits the platform {@code fee} to the System wallet (no fee row for a zero
     * fee). Returns the host's payout row.
     */
    public WalletTransaction postPayout(UUID hostWalletId, BigDecimal net, BigDecimal fee, UUID actorId,
                                        UUID bookingId, UUID ticketId, Instant at) {
        DomainValidation.requireNonNegative(fee, "fee");
        String reason = fee.signum() > 0
                ? "Payout net of 3% platform fee (" + fee.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")"
                : null;
        WalletTransaction payout = post(hostWalletId, WalletTransactionType.BOOKING_PAYOUT, net, actorId,
                bookingId, ticketId, reason, at);
        if (fee.signum() > 0) {
            Wallet system = wallets.findByUserId(AuditService.SYSTEM_ACTOR_ID)
                    .orElseThrow(() -> new IllegalStateException("The System wallet is missing"));
            post(system.walletId(), WalletTransactionType.PLATFORM_FEE, fee, actorId, bookingId, ticketId,
                    "3% platform fee on payout", at);
        }
        return payout;
    }
}
