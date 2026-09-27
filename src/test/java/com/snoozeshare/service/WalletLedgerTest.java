package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcLedgerRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.WalletServiceImpl;
import com.snoozeshare.testsupport.LedgerTestSupport;

class WalletLedgerTest {

    @Test
    void creditsAndDebitsProduceSignedAppendOnlyRows() throws Exception {
        try (Connection connection = migratedConnection()) {
            UUID userId = seedUser(connection);
            WalletService service = new WalletServiceImpl(connection,
                    new JdbcWalletRepository(connection),
                    LedgerTestSupport.writer(connection), new JdbcLedgerRepository(connection), null);

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
                    LedgerTestSupport.writer(connection), new JdbcLedgerRepository(connection), null);

            assertThrows(IllegalArgumentException.class, () ->
                    service.topUp(userId, BigDecimal.ZERO));
            assertThrows(IllegalArgumentException.class, () ->
                    service.withdraw(userId, new BigDecimal("1.00")));
            assertThrows(IllegalArgumentException.class, () ->
                    service.topUp(userId, new BigDecimal("-1.00")));
        }
    }

    @Test
    void hostWalletSupportsTopUpWithdrawalAndChronologicalStatement() throws Exception {
        try (Connection connection = migratedConnection()) {
            UUID userId = seedUser(connection, Role.HOST);
            WalletService service = new WalletServiceImpl(connection,
                    new JdbcWalletRepository(connection),
                    LedgerTestSupport.writer(connection), new JdbcLedgerRepository(connection), null);

            service.topUp(userId, new BigDecimal("500.00"));
            service.withdraw(userId, new BigDecimal("125.00"));

            assertEquals(0, new BigDecimal("375.00").compareTo(service.balanceOf(userId)));
            assertEquals(2, service.statementFor(userId).size());
            assertEquals(WalletTransactionType.TOP_UP, service.statementFor(userId).get(0).type());
            assertEquals(WalletTransactionType.WITHDRAWAL,
                    service.statementFor(userId).get(1).type());
            BigDecimal excessiveAmount = new BigDecimal("375.01");
            assertThrows(IllegalArgumentException.class, () ->
                    service.withdraw(userId, excessiveAmount));
        }
    }

    @Test
    void hostStatementCanContainEveryWalletTransactionType() throws Exception {
        try (Connection connection = migratedConnection()) {
            UUID userId = seedUser(connection, Role.HOST);
            JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
            UUID walletId = wallets.findByUserId(userId).orElseThrow().walletId();
            var ledger = LedgerTestSupport.writer(connection);

            // Every type is a plain post() here (including PLATFORM_FEE, which in production only ever
            // lands on the System wallet via postPayout) — this is a mechanical round-trip check that the
            // ledger can carry and read back any WalletTransactionType, not a business-rules assertion.
            ledger.post(walletId, WalletTransactionType.TOP_UP, new BigDecimal("100.00"),
                    userId, null, null, null, Instant.now());
            ledger.post(walletId, WalletTransactionType.ESCROW_HOLD, new BigDecimal("-10.00"),
                    userId, null, null, null, Instant.now());
            ledger.post(walletId, WalletTransactionType.ESCROW_REFUND, new BigDecimal("10.00"),
                    userId, null, null, null, Instant.now());
            ledger.post(walletId, WalletTransactionType.BOOKING_PAYOUT, new BigDecimal("20.00"),
                    userId, null, null, null, Instant.now());
            ledger.post(walletId, WalletTransactionType.TICKET_REMEDY, new BigDecimal("5.00"),
                    userId, null, null, null, Instant.now());
            ledger.post(walletId, WalletTransactionType.AGENT_OVERRIDE, new BigDecimal("2.00"),
                    userId, null, null, null, Instant.now());
            ledger.post(walletId, WalletTransactionType.PLATFORM_FEE, new BigDecimal("1.50"),
                    userId, null, null, null, Instant.now());
            ledger.post(walletId, WalletTransactionType.WITHDRAWAL, new BigDecimal("-1.00"),
                    userId, null, null, null, Instant.now());

            assertEquals(java.util.Set.of(WalletTransactionType.values()),
                    serviceTypes(new JdbcLedgerRepository(connection).entriesForWallet(walletId)));
        }
    }

    private static UUID seedUser(Connection connection) throws Exception {
        return seedUser(connection, Role.GUEST);
    }

    private static UUID seedUser(Connection connection, Role role) throws Exception {
        MigrationRunner.migrate(connection);
        User user = new User(UUID.randomUUID(), role,
                "Guest", "guest-" + UUID.randomUUID() + "@example.com",
                com.snoozeshare.domain.enums.AccountStatus.ACTIVE, null, Instant.now());
        new JdbcUserRepository(connection).save(user);
        new JdbcWalletRepository(connection).save(new Wallet(UUID.randomUUID(), user.userId(),
                BigDecimal.ZERO, "SGD", Instant.now()));
        return user.userId();
    }

    private static java.util.Set<WalletTransactionType> serviceTypes(
            java.util.List<com.snoozeshare.domain.model.WalletTransaction> transactions) {
        return transactions.stream().map(com.snoozeshare.domain.model.WalletTransaction::type)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Connection migratedConnection() throws Exception {
        return ConnectionFactory.open("jdbc:sqlite::memory:");
    }
}
