package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcAvailabilityBlockRepository implements AvailabilityBlockRepository {

    private final Connection connection;

    public JdbcAvailabilityBlockRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<AvailabilityBlock> findById(UUID blockId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM availability_blocks WHERE blockId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(blockId));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(RowMappers.availabilityBlock(result))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query availability block", exception);
        }
    }

    @Override
    public List<AvailabilityBlock> findByPropertyId(UUID propertyId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM availability_blocks WHERE propertyId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            try (var result = statement.executeQuery()) {
                List<AvailabilityBlock> blocks = new ArrayList<>();
                while (result.next()) {
                    blocks.add(RowMappers.availabilityBlock(result));
                }
                return blocks;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to query availability blocks by property", exception);
        }
    }

    @Override
    public List<AvailabilityBlock> findOverlapping(UUID propertyId,
                                                    LocalDate start, LocalDate end) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM availability_blocks WHERE propertyId = ? "
                        + "AND startDate < ? AND endDate > ?")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            statement.setString(2, JdbcCodecs.localDate(end));
            statement.setString(3, JdbcCodecs.localDate(start));
            try (var result = statement.executeQuery()) {
                List<AvailabilityBlock> blocks = new ArrayList<>();
                while (result.next()) {
                    blocks.add(RowMappers.availabilityBlock(result));
                }
                return blocks;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to query overlapping availability blocks", exception);
        }
    }

    @Override
    public void deleteByBookingId(UUID bookingId) {
        try (var statement = connection.prepareStatement(
                "DELETE FROM availability_blocks WHERE bookingId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(bookingId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to delete availability block by booking", exception);
        }
    }

    @Override
    public void deleteById(UUID blockId) {
        try (var statement = connection.prepareStatement(
                "DELETE FROM availability_blocks WHERE blockId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(blockId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete availability block", exception);
        }
    }

    @Override
    public AvailabilityBlock save(AvailabilityBlock block) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO availability_blocks (blockId, propertyId, startDate, endDate, "
                        + "source, bookingId, reason) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(block.blockId()));
            statement.setString(2, JdbcCodecs.uuid(block.propertyId()));
            statement.setString(3, JdbcCodecs.localDate(block.startDate()));
            statement.setString(4, JdbcCodecs.localDate(block.endDate()));
            statement.setString(5, block.source());
            statement.setString(6, JdbcCodecs.uuid(block.bookingId()));
            statement.setString(7, block.reason());
            statement.executeUpdate();
            return block;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save availability block", exception);
        }
    }
}
