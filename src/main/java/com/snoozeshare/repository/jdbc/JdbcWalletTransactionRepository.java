package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcWalletTransactionRepository implements WalletTransactionRepository {

    private final Connection connection;

    public JdbcWalletTransactionRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public WalletTransaction save(WalletTransaction transaction) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO wallet_transactions (transactionId, walletId, type, amount, "
                        + "feeAmount, balanceAfter, relatedBookingId, relatedTicketId, "
                        + "initiatedBy, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(transaction.transactionId()));
            statement.setString(2, JdbcCodecs.uuid(transaction.walletId()));
            statement.setString(3, transaction.type().name());
            statement.setString(4, JdbcCodecs.decimal(transaction.amount()));
            statement.setString(5, JdbcCodecs.decimal(transaction.feeAmount()));
            statement.setString(6, JdbcCodecs.decimal(transaction.balanceAfter()));
            statement.setString(7, JdbcCodecs.uuid(transaction.relatedBookingId()));
            statement.setString(8, JdbcCodecs.uuid(transaction.relatedTicketId()));
            statement.setString(9, JdbcCodecs.uuid(transaction.initiatedBy()));
            statement.setString(10, JdbcCodecs.instant(transaction.createdAt()));
            statement.executeUpdate();
            return transaction;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save wallet transaction", exception);
        }
    }

    @Override
    public List<WalletTransaction> findByWalletId(UUID walletId) {
        return findMany("walletId", walletId);
    }

    @Override
    public List<WalletTransaction> findByBookingId(UUID bookingId) {
        return findMany("relatedBookingId", bookingId);
    }

    private List<WalletTransaction> findMany(String column, UUID value) {
        try (var statement = connection.prepareStatement(
                "SELECT transactionId, walletId, type, amount, feeAmount, balanceAfter, "
                        + "relatedBookingId, relatedTicketId, initiatedBy, createdAt "
                        + "FROM wallet_transactions WHERE " + column + " = ? ORDER BY createdAt")) {
            statement.setString(1, JdbcCodecs.uuid(value));
            try (var result = statement.executeQuery()) {
                List<WalletTransaction> transactions = new ArrayList<>();
                while (result.next()) {
                    transactions.add(RowMappers.walletTransaction(result));
                }
                return transactions;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query wallet transactions", exception);
        }
    }
}
