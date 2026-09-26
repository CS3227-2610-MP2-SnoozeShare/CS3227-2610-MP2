package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.NoOpAuditService;
import com.snoozeshare.service.impl.WalletLedgerWriter;

class WalletTransactionAtomicityTest {

    @Test
    void failedLedgerWriteLeavesBalanceAndStatementUnchanged() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            UUID userId = UUID.randomUUID();
            new JdbcUserRepository(connection).save(new User(userId, Role.GUEST, "Guest",
                    "atomic@example.com", AccountStatus.ACTIVE, null, Instant.now()));
            JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
            UUID walletId = UUID.randomUUID();
            wallets.save(new Wallet(walletId, userId, BigDecimal.ZERO, "SGD", Instant.now()));
            JdbcWalletTransactionRepository transactions = new JdbcWalletTransactionRepository(
                    connection);
            WalletLedgerWriter writer = new WalletLedgerWriter(connection, wallets, transactions,
                    null, new NoOpAuditService());

            assertThrows(IllegalArgumentException.class, () -> writer.record(walletId,
                    WalletTransactionType.WITHDRAWAL, new BigDecimal("-1.00"), BigDecimal.ZERO,
                    null, null, userId));

            assertEquals(0, BigDecimal.ZERO.compareTo(
                    wallets.findById(walletId).orElseThrow().balance()));
            assertEquals(0, transactions.findByWalletId(walletId).size());
        }
    }
}
