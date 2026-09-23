package com.snoozeshare.service.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.config.RegistrationCodes;
import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.service.UserService;

public final class UserServiceImpl implements UserService {

    private final Connection connection;
    private final UserRepository users;
    private final WalletProvisioningService walletProvisioning;

    public UserServiceImpl(Connection connection, UserRepository users,
                           WalletRepository wallets) {
        this.connection = connection;
        this.users = users;
        this.walletProvisioning = new WalletProvisioningService(wallets);
    }

    @Override
    public User authenticate(String email) {
        DomainValidation.requireText(email, "email");
        User user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email"));
        if (user.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
        return user;
    }

    @Override
    public User register(String displayName, String email, Role role, String registrationCode) {
        DomainValidation.requireText(displayName, "displayName");
        DomainValidation.requireText(email, "email");
        if (role == null) {
            throw new IllegalArgumentException("Role must not be null");
        }
        if (users.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email is already registered");
        }
        validateRegistrationCode(role, registrationCode);
        User user = new User(UUID.randomUUID(), role, displayName, email,
                AccountStatus.ACTIVE, role == Role.GUEST ? null : registrationCode,
                Instant.now());
        try {
            return new TransactionManager(connection).inTransaction(current -> {
                users.save(user);
                walletProvisioning.provisionIfRequired(user);
                return user;
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to register user", exception);
        }
    }

    @Override
    public List<User> listByRole(Role role) {
        return users.findByRole(role);
    }

    @Override
    public User suspend(UUID userId, UUID agentId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        User suspended = new User(user.userId(), user.role(), user.displayName(), user.email(),
                AccountStatus.SUSPENDED, user.registrationCode(), user.createdAt());
        return users.save(suspended);
    }

    private static void validateRegistrationCode(Role role, String registrationCode) {
        if (role == Role.GUEST) {
            return;
        }
        String expected = role == Role.HOST
                ? RegistrationCodes.HOST_CODE : RegistrationCodes.AGENT_CODE;
        if (!expected.equals(registrationCode)) {
            throw new IllegalArgumentException("Invalid registration code");
        }
    }
}
