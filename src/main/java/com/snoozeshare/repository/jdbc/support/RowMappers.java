package com.snoozeshare.repository.jdbc.support;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;

public final class RowMappers {

    private RowMappers() {
    }

    public static User user(ResultSet result) throws SQLException {
        return new User(
                JdbcCodecs.uuid(result.getString("userId")),
                Role.valueOf(result.getString("role")),
                result.getString("displayName"),
                result.getString("email"),
                AccountStatus.valueOf(result.getString("accountStatus")),
                result.getString("registrationCode"),
                JdbcCodecs.instant(result.getString("createdAt")));
    }

    public static Wallet wallet(ResultSet result) throws SQLException {
        return new Wallet(
                JdbcCodecs.uuid(result.getString("walletId")),
                JdbcCodecs.uuid(result.getString("userId")),
                JdbcCodecs.decimal(result.getString("balance")),
                result.getString("currency"),
                JdbcCodecs.instant(result.getString("updatedAt")));
    }

    public static WalletTransaction walletTransaction(ResultSet result) throws SQLException {
        return new WalletTransaction(
                JdbcCodecs.uuid(result.getString("transactionId")),
                JdbcCodecs.uuid(result.getString("walletId")),
                WalletTransactionType.valueOf(result.getString("type")),
                JdbcCodecs.decimal(result.getString("amount")),
                JdbcCodecs.decimal(result.getString("feeAmount")),
                JdbcCodecs.decimal(result.getString("balanceAfter")),
                JdbcCodecs.uuid(result.getString("relatedBookingId")),
                JdbcCodecs.uuid(result.getString("relatedTicketId")),
                JdbcCodecs.uuid(result.getString("initiatedBy")),
                JdbcCodecs.instant(result.getString("createdAt")));
    }
}
