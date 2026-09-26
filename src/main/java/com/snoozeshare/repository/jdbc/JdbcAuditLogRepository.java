package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.repository.AuditCriteria;
import com.snoozeshare.repository.AuditLogRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcAuditLogRepository implements AuditLogRepository {

    private static final List<String> ID_COLUMNS = List.of("entityId", "bookingId", "ticketId",
            "actorUserId", "subjectUserId");

    private final Connection connection;

    public JdbcAuditLogRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, "
                        + "beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, "
                        + "bookingId, ticketId, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(entry.logId()));
            statement.setString(2, JdbcCodecs.uuid(entry.actorUserId()));
            statement.setString(3, entry.actorName());
            statement.setString(4, entry.actionType());
            statement.setString(5, entry.entityType());
            statement.setString(6, JdbcCodecs.uuid(entry.entityId()));
            statement.setString(7, entry.beforeState());
            statement.setString(8, entry.afterState());
            statement.setString(9, JdbcCodecs.decimal(entry.walletAdjustment()));
            statement.setString(10, entry.reason());
            statement.setString(11, JdbcCodecs.uuid(entry.subjectUserId()));
            statement.setString(12, entry.subjectName());
            statement.setString(13, JdbcCodecs.uuid(entry.bookingId()));
            statement.setString(14, JdbcCodecs.uuid(entry.ticketId()));
            statement.setString(15, JdbcCodecs.instant(entry.timestamp()));
            statement.executeUpdate();
            return entry;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save audit log entry", exception);
        }
    }

    @Override
    public List<AuditLogEntry> search(AuditCriteria criteria, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1 = 1");
        List<Object> values = new ArrayList<>();
        if (criteria.textGiven()) {
            List<String> terms = new ArrayList<>();
            if (!criteria.userIds().isEmpty()) {
                String marks = String.join(", ", Collections.nCopies(criteria.userIds().size(), "?"));
                terms.add("actorUserId IN (" + marks + ")");
                terms.add("subjectUserId IN (" + marks + ")");
                for (int pass = 0; pass < 2; pass++) {
                    criteria.userIds().forEach(id -> values.add(id.toString()));
                }
            }
            if (criteria.idFragment() != null) {
                for (String column : ID_COLUMNS) {
                    terms.add(column + " LIKE ?");
                    values.add("%" + criteria.idFragment() + "%");
                }
            }
            sql.append(terms.isEmpty() ? " AND 0" : " AND (" + String.join(" OR ", terms) + ")");
        }
        if (!criteria.actionTypes().isEmpty()) {
            String marks = String.join(", ", Collections.nCopies(criteria.actionTypes().size(), "?"));
            sql.append(" AND actionType IN (").append(marks).append(")");
            values.addAll(criteria.actionTypes());
        }
        if (criteria.from() != null) {
            sql.append(" AND substr(timestamp, 1, 19) >= ?");
            values.add(JdbcCodecs.instant(criteria.from()).substring(0, 19));
        }
        if (criteria.toExclusive() != null) {
            sql.append(" AND substr(timestamp, 1, 19) < ?");
            values.add(JdbcCodecs.instant(criteria.toExclusive()).substring(0, 19));
        }
        sql.append(" ORDER BY timestamp DESC, rowid ASC LIMIT ? OFFSET ?");
        values.add(limit);
        values.add(offset);
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setObject(index + 1, values.get(index));
            }
            try (var result = statement.executeQuery()) {
                List<AuditLogEntry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(RowMappers.auditLogEntry(result));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search audit log", exception);
        }
    }

    @Override
    public Set<UUID> findUserIdsByName(String fragment) {
        String like = "%" + fragment.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        String sql = "SELECT userId FROM users WHERE lower(displayName) LIKE ? ESCAPE '\\' "
                + "OR lower(email) LIKE ? ESCAPE '\\' "
                + "UNION SELECT actorUserId FROM audit_log WHERE lower(actorName) LIKE ? ESCAPE '\\' "
                + "UNION SELECT subjectUserId FROM audit_log "
                + "WHERE subjectUserId IS NOT NULL AND lower(subjectName) LIKE ? ESCAPE '\\'";
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 4; index++) {
                statement.setString(index, like);
            }
            try (var result = statement.executeQuery()) {
                Set<UUID> ids = new HashSet<>();
                while (result.next()) {
                    ids.add(UUID.fromString(result.getString(1)));
                }
                return ids;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to resolve users by name", exception);
        }
    }
}
