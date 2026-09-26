package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcBookingRepository implements BookingRepository {

    private final Connection connection;

    public JdbcBookingRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Booking> findById(UUID bookingId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM bookings WHERE bookingId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(bookingId));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(RowMappers.booking(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query booking", exception);
        }
    }

    @Override
    public List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM bookings WHERE listingId = ? "
                        + "AND startDate < ? AND endDate > ? "
                        + "AND status IN ('PENDING', 'CONFIRMED')")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            statement.setString(2, JdbcCodecs.localDate(end));
            statement.setString(3, JdbcCodecs.localDate(start));
            try (var result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(RowMappers.booking(result));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query overlapping bookings", exception);
        }
    }

    @Override
    public List<Booking> findByGuest(UUID guestId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM bookings WHERE guestId = ? ORDER BY createdAt DESC")) {
            statement.setString(1, JdbcCodecs.uuid(guestId));
            try (var result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(RowMappers.booking(result));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query bookings by guest", exception);
        }
    }

    @Override
    public List<Booking> findByHostPending(UUID hostId) {
        try (var statement = connection.prepareStatement(
                "SELECT b.* FROM bookings b JOIN properties p ON b.listingId = p.propertyId "
                        + "WHERE p.hostId = ? AND b.status = 'PENDING' "
                        + "ORDER BY b.createdAt")) {
            statement.setString(1, JdbcCodecs.uuid(hostId));
            try (var result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(RowMappers.booking(result));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query pending bookings by host", exception);
        }
    }

    @Override
    public List<Booking> findByHost(UUID hostId) {
        try (var statement = connection.prepareStatement(
                "SELECT b.* FROM bookings b JOIN properties p ON b.listingId = p.propertyId "
                        + "WHERE p.hostId = ? ORDER BY b.createdAt DESC, b.bookingId")) {
            statement.setString(1, JdbcCodecs.uuid(hostId));
            try (var result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(RowMappers.booking(result));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query bookings by host", exception);
        }
    }

    @Override
    public Booking save(Booking booking) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO bookings (bookingId, listingId, guestId, startDate, endDate, "
                        + "status, nightlyRateSnapshot, totalAmount, createdAt, decidedAt, "
                        + "completedAt, hostDecisionMessage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(bookingId) DO UPDATE SET status = excluded.status, "
                        + "decidedAt = excluded.decidedAt, completedAt = excluded.completedAt, "
                        + "hostDecisionMessage = excluded.hostDecisionMessage")) {
            statement.setString(1, JdbcCodecs.uuid(booking.bookingId()));
            statement.setString(2, JdbcCodecs.uuid(booking.listingId()));
            statement.setString(3, JdbcCodecs.uuid(booking.guestId()));
            statement.setString(4, JdbcCodecs.localDate(booking.startDate()));
            statement.setString(5, JdbcCodecs.localDate(booking.endDate()));
            statement.setString(6, booking.status().name());
            statement.setString(7, JdbcCodecs.decimal(booking.nightlyRateSnapshot()));
            statement.setString(8, JdbcCodecs.decimal(booking.totalAmount()));
            statement.setString(9, JdbcCodecs.instant(booking.createdAt()));
            statement.setString(10, JdbcCodecs.instant(booking.decidedAt()));
            statement.setString(11, JdbcCodecs.instant(booking.completedAt()));
            statement.setString(12, booking.hostDecisionMessage());
            statement.executeUpdate();
            return booking;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save booking", exception);
        }
    }
}
