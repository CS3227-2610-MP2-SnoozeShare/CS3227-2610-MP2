package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcLedgerRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.LedgerWriter;
import com.snoozeshare.testsupport.LedgerTestSupport;

class LedgerWriterTest {

    private static final Instant AT = Instant.parse("2026-09-27T01:00:00Z");

    private static Wallet wallet(Connection connection, Role role, String email, String balance) {
        User user = new JdbcUserRepository(connection).save(new User(UUID.randomUUID(), role, email, email,
                AccountStatus.ACTIVE, null, AT));
        return new JdbcWalletRepository(connection).save(new Wallet(UUID.randomUUID(), user.userId(),
                new BigDecimal(balance), "SGD", AT));
    }

    @Test
    void postingMovesTheBalanceAndWritesOneMoneyRowWithBalanceAfter() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            Wallet guest = wallet(connection, Role.GUEST, "g@x.test", "100.00");
            LedgerWriter ledger = LedgerTestSupport.writer(connection);

            WalletTransaction hold = ledger.post(guest.walletId(), WalletTransactionType.ESCROW_HOLD,
                    new BigDecimal("-40.00"), guest.userId(), null, null, null, AT);

            assertEquals(0, new BigDecimal("60.00").compareTo(hold.balanceAfter()));
            assertEquals(0, new BigDecimal("60.00").compareTo(
                    new JdbcWalletRepository(connection).findById(guest.walletId()).orElseThrow().balance()));
            List<WalletTransaction> rows = new JdbcLedgerRepository(connection).entriesForWallet(guest.walletId());
            assertEquals(1, rows.size());
            assertEquals(hold.transactionId(), rows.get(0).transactionId());
        }
    }

    @Test
    void anOverdraftAndAZeroAmountAreRejectedWithoutSideEffects() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            Wallet guest = wallet(connection, Role.GUEST, "g@x.test", "10.00");
            LedgerWriter ledger = LedgerTestSupport.writer(connection);

            assertThrows(IllegalArgumentException.class, () -> ledger.post(guest.walletId(),
                    WalletTransactionType.WITHDRAWAL, new BigDecimal("-10.01"), guest.userId(), null, null, null, AT));
            assertThrows(IllegalArgumentException.class, () -> ledger.post(guest.walletId(),
                    WalletTransactionType.TOP_UP, BigDecimal.ZERO, guest.userId(), null, null, null, AT));
            assertEquals(0, new JdbcLedgerRepository(connection).entriesForWallet(guest.walletId()).size());
        }
    }

    @Test
    void aPayoutWritesTheNetRowAndAFeeRowToTheSystemWallet() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            Wallet host = wallet(connection, Role.HOST, "h@x.test", "0.00");
            LedgerWriter ledger = LedgerTestSupport.writer(connection);
            UUID booking = null;

            WalletTransaction payout = ledger.postPayout(host.walletId(), new BigDecimal("97.00"),
                    new BigDecimal("3.00"), host.userId(), booking, null, AT);

            assertEquals(WalletTransactionType.BOOKING_PAYOUT, payout.type());
            assertEquals(0, new BigDecimal("97.00").compareTo(payout.amount()));
            var system = new JdbcWalletRepository(connection).findByUserId(AuditService.SYSTEM_ACTOR_ID)
                    .orElseThrow();
            assertEquals(0, new BigDecimal("3.00").compareTo(system.balance()));
            List<WalletTransaction> fee = new JdbcLedgerRepository(connection).entriesForWallet(system.walletId());
            assertEquals(1, fee.size());
            assertEquals(WalletTransactionType.PLATFORM_FEE, fee.get(0).type());
        }
    }

    @Test
    void aZeroFeeWritesNoFeeRow() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            Wallet host = wallet(connection, Role.HOST, "h@x.test", "0.00");
            LedgerTestSupport.writer(connection).postPayout(host.walletId(), new BigDecimal("50.00"),
                    BigDecimal.ZERO, host.userId(), null, null, AT);
            var system = new JdbcWalletRepository(connection).findByUserId(AuditService.SYSTEM_ACTOR_ID)
                    .orElseThrow();
            assertEquals(0, new JdbcLedgerRepository(connection).entriesForWallet(system.walletId()).size());
        }
    }
}
