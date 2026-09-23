package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

class JdbcRepositoryIntegrationTest {

    @Test
    void userRepositoryRoundTripsByIdEmailAndRole() throws Exception {
        try (var connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            JdbcUserRepository repository = new JdbcUserRepository(connection);
            User user = new User(UUID.randomUUID(), Role.GUEST, "Guest One",
                    "guest@example.com", AccountStatus.ACTIVE, null,
                    Instant.parse("2026-09-23T00:00:00Z"));

            repository.save(user);

            assertEquals(user, repository.findById(user.userId()).orElseThrow());
            assertEquals(user, repository.findByEmail(user.email()).orElseThrow());
            assertEquals(List.of(user), repository.findByRole(Role.GUEST));
        }
    }

    @Test
    void walletAndTransactionRepositoriesRoundTripMoneyAndEnums() throws Exception {
        try (var connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            JdbcUserRepository users = new JdbcUserRepository(connection);
            JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
            JdbcWalletTransactionRepository transactions =
                    new JdbcWalletTransactionRepository(connection);
            UUID userId = UUID.randomUUID();
            UUID walletId = UUID.randomUUID();
            users.save(new User(userId, Role.HOST, "Host One", "host@example.com",
                    AccountStatus.ACTIVE, "HOST-DEMO", Instant.parse("2026-09-23T00:00:00Z")));
            Wallet wallet = new Wallet(walletId, userId, new BigDecimal("12.34"), "SGD",
                    Instant.parse("2026-09-23T00:00:00Z"));
            wallets.save(wallet);
            WalletTransaction transaction = new WalletTransaction(
                    UUID.randomUUID(), walletId, WalletTransactionType.TOP_UP,
                    new BigDecimal("12.34"), null, new BigDecimal("12.34"),
                    null, null, userId, Instant.parse("2026-09-23T00:00:00Z"));

            transactions.save(transaction);

            assertEquals(wallet, wallets.findByUserId(userId).orElseThrow());
            assertEquals(transaction,
                    transactions.findByWalletId(walletId).get(0));
            assertTrue(transactions.findByBookingId(UUID.randomUUID()).isEmpty());
        }
    }
}
