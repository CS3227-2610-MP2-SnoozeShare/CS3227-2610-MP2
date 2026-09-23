package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.WalletServiceImpl;

class WalletLedgerTest {

    @Test
    void creditsAndDebitsProduceSignedAppendOnlyRows() throws Exception {
        try (Connection connection = migratedConnection()) {
            UUID userId = seedUser(connection);
            WalletService service = new WalletServiceImpl(connection,
                    new JdbcWalletRepository(connection),
                    new JdbcWalletTransactionRepository(connection));

            service.topUp(userId, new BigDecimal("50.00"));
            service.withdraw(userId, new BigDecimal("12.50"));

            assertEquals(0, new BigDecimal("37.50").compareTo(service.balanceOf(userId)));
            var statement = service.statementFor(userId);
            assertEquals(2, statement.size());
            assertEquals(0, new BigDecimal("50.00").compareTo(statement.get(0).amount()));
            assertEquals(0, new BigDecimal("-12.50").compareTo(statement.get(1).amount()));
            assertEquals(0, new BigDecimal("37.50")
                    .compareTo(statement.get(1).balanceAfter()));
        }
    }

    @Test
    void invalidAmountsAndInsufficientFundsAreRejected() throws Exception {
        try (Connection connection = migratedConnection()) {
            UUID userId = seedUser(connection);
            WalletService service = new WalletServiceImpl(connection,
                    new JdbcWalletRepository(connection),
                    new JdbcWalletTransactionRepository(connection));

            assertThrows(IllegalArgumentException.class, () ->
                    service.topUp(userId, BigDecimal.ZERO));
            assertThrows(IllegalArgumentException.class, () ->
                    service.withdraw(userId, new BigDecimal("1.00")));
            assertThrows(IllegalArgumentException.class, () ->
                    service.topUp(userId, new BigDecimal("-1.00")));
        }
    }

    private static UUID seedUser(Connection connection) throws Exception {
        MigrationRunner.migrate(connection);
        User user = new User(UUID.randomUUID(), com.snoozeshare.domain.enums.Role.GUEST,
                "Guest", "guest-" + UUID.randomUUID() + "@example.com",
                com.snoozeshare.domain.enums.AccountStatus.ACTIVE, null, Instant.now());
        new JdbcUserRepository(connection).save(user);
        new JdbcWalletRepository(connection).save(new Wallet(UUID.randomUUID(), user.userId(),
                BigDecimal.ZERO, "SGD", Instant.now()));
        return user.userId();
    }

    private static Connection migratedConnection() throws Exception {
        return ConnectionFactory.open("jdbc:sqlite::memory:");
    }
}
