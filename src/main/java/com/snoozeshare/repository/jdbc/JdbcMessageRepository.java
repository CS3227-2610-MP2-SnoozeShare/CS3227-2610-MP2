package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.repository.MessageRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcMessageRepository implements MessageRepository {

    private final Connection connection;

    public JdbcMessageRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<Message> findThread(UUID ticketId, ThreadChannel channel) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM messages WHERE ticketId = ? AND channel = ? ORDER BY rowid")) {
            statement.setString(1, JdbcCodecs.uuid(ticketId));
            statement.setString(2, channel.name());
            try (var result = statement.executeQuery()) {
                List<Message> messages = new ArrayList<>();
                while (result.next()) {
                    messages.add(RowMappers.message(result));
                }
                return messages;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query messages", exception);
        }
    }

    @Override
    public Optional<Message> findLast(UUID ticketId, ThreadChannel channel) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM messages WHERE ticketId = ? AND channel = ? ORDER BY rowid DESC LIMIT 1")) {
            statement.setString(1, JdbcCodecs.uuid(ticketId));
            statement.setString(2, channel.name());
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(RowMappers.message(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query last message", exception);
        }
    }

    @Override
    public Message save(Message message) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO messages (messageId, ticketId, channel, authorId, authorRole, body, sentAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(message.messageId()));
            statement.setString(2, JdbcCodecs.uuid(message.ticketId()));
            statement.setString(3, message.channel().name());
            statement.setString(4, JdbcCodecs.uuid(message.authorId()));
            statement.setString(5, message.authorRole().name());
            statement.setString(6, message.body());
            statement.setString(7, JdbcCodecs.instant(message.sentAt()));
            statement.executeUpdate();
            return message;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save message", exception);
        }
    }

    @Override
    public void markRead(UUID ticketId, ThreadChannel channel, UUID userId, UUID lastReadMessageId) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO message_reads (ticketId, channel, userId, lastReadMessageId) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(ticketId, channel, userId) DO UPDATE SET "
                        + "lastReadMessageId = excluded.lastReadMessageId")) {
            statement.setString(1, JdbcCodecs.uuid(ticketId));
            statement.setString(2, channel.name());
            statement.setString(3, JdbcCodecs.uuid(userId));
            statement.setString(4, JdbcCodecs.uuid(lastReadMessageId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to mark messages read", exception);
        }
    }

    @Override
    public int countUnread(UUID ticketId, ThreadChannel channel, UUID userId) {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM messages m WHERE m.ticketId = ? AND m.channel = ? AND m.authorId <> ? "
                        + "AND m.rowid > COALESCE((SELECT last.rowid FROM message_reads r "
                        + "JOIN messages last ON last.messageId = r.lastReadMessageId "
                        + "WHERE r.ticketId = ? AND r.channel = ? AND r.userId = ?), 0)")) {
            String ticket = JdbcCodecs.uuid(ticketId);
            String user = JdbcCodecs.uuid(userId);
            statement.setString(1, ticket);
            statement.setString(2, channel.name());
            statement.setString(3, user);
            statement.setString(4, ticket);
            statement.setString(5, channel.name());
            statement.setString(6, user);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to count unread messages", exception);
        }
    }
}
