package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.repository.AuditLogRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcAuditLogRepository implements AuditLogRepository {

    private final Connection connection;

    public JdbcAuditLogRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO audit_log (logId, actorUserId, actionType, entityType, entityId, "
                        + "beforeState, afterState, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(entry.logId()));
            statement.setString(2, JdbcCodecs.uuid(entry.actorUserId()));
            statement.setString(3, entry.actionType());
            statement.setString(4, entry.entityType());
            statement.setString(5, JdbcCodecs.uuid(entry.entityId()));
            statement.setString(6, entry.beforeState());
            statement.setString(7, entry.afterState());
            statement.setString(8, JdbcCodecs.instant(entry.timestamp()));
            statement.executeUpdate();
            return entry;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save audit log entry", exception);
        }
    }

    @Override
    public List<AuditLogEntry> query(UUID userId, UUID bookingId, String actionType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1 = 1");
        List<String> values = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND actorUserId = ?");
            values.add(JdbcCodecs.uuid(userId));
        }
        if (bookingId != null) {
            sql.append(" AND entityId = ?");
            values.add(JdbcCodecs.uuid(bookingId));
        }
        if (actionType != null) {
            sql.append(" AND actionType = ?");
            values.add(actionType);
        }
        sql.append(" ORDER BY timestamp");
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
            try (var result = statement.executeQuery()) {
                List<AuditLogEntry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(RowMappers.auditLogEntry(result));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query audit log", exception);
        }
    }
}
