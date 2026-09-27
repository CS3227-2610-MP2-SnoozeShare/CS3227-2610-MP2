package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.BookingMessage;
import com.snoozeshare.repository.BookingMessageRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcBookingMessageRepository implements BookingMessageRepository {

    private final Connection connection;

    public JdbcBookingMessageRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<BookingMessage> findThread(UUID bookingId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM booking_messages WHERE bookingId = ? ORDER BY rowid")) {
            statement.setString(1, JdbcCodecs.uuid(bookingId));
            try (var result = statement.executeQuery()) {
                List<BookingMessage> messages = new ArrayList<>();
                while (result.next()) {
                    messages.add(RowMappers.bookingMessage(result));
                }
                return messages;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query booking messages", exception);
        }
    }

    @Override
    public Optional<BookingMessage> findLast(UUID bookingId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM booking_messages WHERE bookingId = ? ORDER BY rowid DESC LIMIT 1")) {
            statement.setString(1, JdbcCodecs.uuid(bookingId));
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(RowMappers.bookingMessage(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query last booking message", exception);
        }
    }

    @Override
    public BookingMessage save(BookingMessage message) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO booking_messages (messageId, bookingId, authorId, authorRole, body, sentAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(message.messageId()));
            statement.setString(2, JdbcCodecs.uuid(message.bookingId()));
            statement.setString(3, JdbcCodecs.uuid(message.authorId()));
            statement.setString(4, message.authorRole().name());
            statement.setString(5, message.body());
            statement.setString(6, JdbcCodecs.instant(message.sentAt()));
            statement.executeUpdate();
            return message;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save booking message", exception);
        }
    }

    @Override
    public void markRead(UUID bookingId, UUID userId, UUID lastReadMessageId) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO booking_message_reads (bookingId, userId, lastReadMessageId) VALUES (?, ?, ?) "
                        + "ON CONFLICT(bookingId, userId) DO UPDATE SET "
                        + "lastReadMessageId = excluded.lastReadMessageId")) {
            statement.setString(1, JdbcCodecs.uuid(bookingId));
            statement.setString(2, JdbcCodecs.uuid(userId));
            statement.setString(3, JdbcCodecs.uuid(lastReadMessageId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to mark booking messages read", exception);
        }
    }

    @Override
    public int countUnread(UUID bookingId, UUID userId) {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM booking_messages m WHERE m.bookingId = ? AND m.authorId <> ? "
                        + "AND m.rowid > COALESCE((SELECT last.rowid FROM booking_message_reads r "
                        + "JOIN booking_messages last ON last.messageId = r.lastReadMessageId "
                        + "WHERE r.bookingId = ? AND r.userId = ?), 0)")) {
            String booking = JdbcCodecs.uuid(bookingId);
            String user = JdbcCodecs.uuid(userId);
            statement.setString(1, booking);
            statement.setString(2, user);
            statement.setString(3, booking);
            statement.setString(4, user);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to count unread booking messages", exception);
        }
    }
}
