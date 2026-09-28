package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.UserServiceImpl;

class SystemUserGuardTest {

    private static final String SYSTEM_EMAIL = "system@snoozeshare.invalid";

    @Test
    void theSystemUserCannotAuthenticate() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            UserService users = new UserServiceImpl(connection, new JdbcUserRepository(connection),
                    new JdbcWalletRepository(connection));
            assertThrows(IllegalArgumentException.class, () -> users.authenticate(SYSTEM_EMAIL));
        }
    }

    @Test
    void nobodyCanRegisterAsSystem() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            UserService users = new UserServiceImpl(connection, new JdbcUserRepository(connection),
                    new JdbcWalletRepository(connection));
            assertThrows(IllegalArgumentException.class, () ->
                    users.register("Mallory", "m@x.test", Role.SYSTEM, "AGENT-2026-01"));
        }
    }

    @Test
    void theSystemUserIsHiddenFromAccountsAndCannotBeSuspended() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            var service = fixture.service();
            assertFalse(service.listAccounts().stream()
                    .anyMatch(account -> account.userId().equals(AuditService.SYSTEM_ACTOR_ID)));
            assertThrows(IllegalArgumentException.class, () ->
                    service.suspend(AuditService.SYSTEM_ACTOR_ID, fixture.agent.userId(), "no"));
        }
    }
}
