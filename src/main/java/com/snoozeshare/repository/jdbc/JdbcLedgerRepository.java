package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.repository.LedgerRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;

public final class JdbcLedgerRepository implements LedgerRepository {

    private static final String SELECT = "SELECT a.entityId AS transactionId, w.walletId AS walletId, "
            + "a.actionType AS type, a.walletAdjustment AS amount, a.balanceAfter AS balanceAfter, "
            + "a.bookingId AS relatedBookingId, a.ticketId AS relatedTicketId, a.actorUserId AS initiatedBy, "
            + "a.timestamp AS createdAt FROM audit_log a JOIN wallets w ON w.userId = a.subjectUserId "
            + "WHERE a.walletAdjustment IS NOT NULL AND ";

    private final Connection connection;

    public JdbcLedgerRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<WalletTransaction> entriesForWallet(UUID walletId) {
        return query("w.walletId = ?", walletId);
    }

    @Override
    public List<WalletTransaction> entriesForBooking(UUID bookingId) {
        return query("a.bookingId = ?", bookingId);
    }

    private List<WalletTransaction> query(String condition, UUID value) {
        try (var statement = connection.prepareStatement(SELECT + condition + " ORDER BY a.rowid")) {
            statement.setString(1, JdbcCodecs.uuid(value));
            try (var result = statement.executeQuery()) {
                List<WalletTransaction> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(new WalletTransaction(
                            JdbcCodecs.uuid(result.getString("transactionId")),
                            JdbcCodecs.uuid(result.getString("walletId")),
                            WalletTransactionType.valueOf(result.getString("type")),
                            JdbcCodecs.decimal(result.getString("amount")),
                            JdbcCodecs.decimal(result.getString("balanceAfter")),
                            JdbcCodecs.uuid(result.getString("relatedBookingId")),
                            JdbcCodecs.uuid(result.getString("relatedTicketId")),
                            JdbcCodecs.uuid(result.getString("initiatedBy")),
                            JdbcCodecs.instant(result.getString("createdAt"))));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query the ledger", exception);
        }
    }
}
