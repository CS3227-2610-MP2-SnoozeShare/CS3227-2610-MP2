package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;

import org.junit.jupiter.api.Test;

import com.snoozeshare.config.RegistrationCodes;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.UserServiceImpl;

class UserServiceTest {

    @Test
    void guestRegistrationNeedsNoCodeAndCreatesOneZeroBalanceWallet() throws Exception {
        try (Connection connection = migratedConnection()) {
            JdbcUserRepository users = new JdbcUserRepository(connection);
            JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
            UserService service = new UserServiceImpl(connection, users, wallets);

            var user = service.register("Guest", "guest@example.com", Role.GUEST, null);

            assertEquals(Role.GUEST, user.role());
            assertEquals(0, wallets.findByUserId(user.userId()).orElseThrow()
                    .balance().signum());
        }
    }

    @Test
    void hostRegistrationRequiresTheHostCodeAndCreatesOneWallet() throws Exception {
        try (Connection connection = migratedConnection()) {
            JdbcUserRepository users = new JdbcUserRepository(connection);
            JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
            UserService service = new UserServiceImpl(connection, users, wallets);

            var user = service.register("Host", "host@example.com", Role.HOST,
                    RegistrationCodes.HOST_CODE);

            assertEquals(Role.HOST, user.role());
            assertTrue(wallets.findByUserId(user.userId()).isPresent());
        }
    }

    @Test
    void agentRegistrationRequiresTheAgentCodeButCreatesNoWallet() throws Exception {
        try (Connection connection = migratedConnection()) {
            JdbcUserRepository users = new JdbcUserRepository(connection);
            JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
            UserService service = new UserServiceImpl(connection, users, wallets);

            var user = service.register("Agent", "agent@example.com", Role.AGENT,
                    RegistrationCodes.AGENT_CODE);

            assertEquals(Role.AGENT, user.role());
            assertTrue(wallets.findByUserId(user.userId()).isEmpty());
        }
    }

    @Test
    void wrongRegistrationCodeAndDuplicateEmailAreRejected() throws Exception {
        try (Connection connection = migratedConnection()) {
            JdbcUserRepository users = new JdbcUserRepository(connection);
            JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
            UserService service = new UserServiceImpl(connection, users, wallets);

            assertThrows(IllegalArgumentException.class, () ->
                    service.register("Host", "host@example.com", Role.HOST, "wrong"));
            service.register("Guest", "same@example.com", Role.GUEST, null);
            assertThrows(IllegalArgumentException.class, () ->
                    service.register("Other", "same@example.com", Role.GUEST, null));
        }
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
