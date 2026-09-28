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
import com.snoozeshare.repository.jdbc.JdbcLedgerRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.LedgerWriter;
import com.snoozeshare.testsupport.LedgerTestSupport;

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
            LedgerWriter writer = LedgerTestSupport.writer(connection);

            assertThrows(IllegalArgumentException.class, () -> writer.post(walletId,
                    WalletTransactionType.WITHDRAWAL, new BigDecimal("-1.00"), userId, null, null, null,
                    Instant.now()));

            assertEquals(0, BigDecimal.ZERO.compareTo(
                    wallets.findById(walletId).orElseThrow().balance()));
            assertEquals(0, new JdbcLedgerRepository(connection).entriesForWallet(walletId).size());
        }
    }
}
