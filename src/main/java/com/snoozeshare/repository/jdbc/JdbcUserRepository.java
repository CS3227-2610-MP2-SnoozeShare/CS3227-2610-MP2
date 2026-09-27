package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS = "userId, role, displayName, email, accountStatus, registrationCode, "
            + "createdAt, suspensionReason";

    private final Connection connection;

    public JdbcUserRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE userId = ?", userId.toString());
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE email = ?", email);
    }

    @Override
    public List<User> findByRole(Role role) {
        return findMany("SELECT " + COLUMNS + " FROM users WHERE role = ?", role.name());
    }

    @Override
    public List<User> findAll() {
        return findMany("SELECT " + COLUMNS + " FROM users ORDER BY createdAt, userId");
    }

    @Override
    public User save(User user) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO users (userId, role, displayName, email, accountStatus, "
                        + "registrationCode, createdAt, suspensionReason) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(userId) DO UPDATE SET role = excluded.role, "
                        + "displayName = excluded.displayName, email = excluded.email, "
                        + "accountStatus = excluded.accountStatus, "
                        + "registrationCode = excluded.registrationCode, "
                        + "suspensionReason = excluded.suspensionReason")) {
            statement.setString(1, JdbcCodecs.uuid(user.userId()));
            statement.setString(2, user.role().name());
            statement.setString(3, user.displayName());
            statement.setString(4, user.email());
            statement.setString(5, user.accountStatus().name());
            statement.setString(6, user.registrationCode());
            statement.setString(7, JdbcCodecs.instant(user.createdAt()));
            statement.setString(8, user.suspensionReason());
            statement.executeUpdate();
            return user;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save user", exception);
        }
    }

    private List<User> findMany(String sql, String... values) {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            try (var result = statement.executeQuery()) {
                var users = new java.util.ArrayList<User>();
                while (result.next()) {
                    users.add(RowMappers.user(result));
                }
                return users;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query users", exception);
        }
    }

    private Optional<User> findOne(String sql, String value) {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(RowMappers.user(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query user", exception);
        }
    }
}
