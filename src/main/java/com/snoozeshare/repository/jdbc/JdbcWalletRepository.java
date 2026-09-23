package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcWalletRepository implements WalletRepository {

    private final Connection connection;

    public JdbcWalletRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Wallet> findById(UUID walletId) {
        return findOne("walletId", walletId.toString());
    }

    @Override
    public Optional<Wallet> findByUserId(UUID userId) {
        return findOne("userId", userId.toString());
    }

    @Override
    public Wallet save(Wallet wallet) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO wallets (walletId, userId, balance, currency, updatedAt) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT(walletId) DO UPDATE SET "
                        + "balance = excluded.balance, currency = excluded.currency, "
                        + "updatedAt = excluded.updatedAt")) {
            statement.setString(1, JdbcCodecs.uuid(wallet.walletId()));
            statement.setString(2, JdbcCodecs.uuid(wallet.userId()));
            statement.setString(3, JdbcCodecs.decimal(wallet.balance()));
            statement.setString(4, wallet.currency());
            statement.setString(5, JdbcCodecs.instant(wallet.updatedAt()));
            statement.executeUpdate();
            return wallet;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save wallet", exception);
        }
    }

    private Optional<Wallet> findOne(String column, String value) {
        try (var statement = connection.prepareStatement(
                "SELECT walletId, userId, balance, currency, updatedAt FROM wallets WHERE "
                        + column + " = ?")) {
            statement.setString(1, value);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(RowMappers.wallet(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query wallet", exception);
        }
    }
}
