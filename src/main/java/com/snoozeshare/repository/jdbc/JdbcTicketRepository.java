package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcTicketRepository implements TicketRepository {

    private final Connection connection;

    public JdbcTicketRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Ticket> findById(UUID ticketId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE ticketId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(ticketId));
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(RowMappers.ticket(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query ticket", exception);
        }
    }

    @Override
    public boolean existsByCategory(String categoryLabel) {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM tickets WHERE LOWER(category) = LOWER(?) LIMIT 1")) {
            statement.setString(1, categoryLabel);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check tickets for category", exception);
        }
    }

    @Override
    public List<Ticket> findByStatus(TicketStatus status) {
        return findQueue(status, AssigneeFilter.ALL, null);
    }

    @Override
    public List<Ticket> findByParty(UUID userId, Role role) {
        String sql = switch (role) {
            case GUEST -> "SELECT t.* FROM tickets t JOIN bookings b ON b.bookingId = t.bookingId "
                    + "WHERE b.guestId = ? ORDER BY t.createdAt DESC, t.ticketId";
            case HOST -> "SELECT t.* FROM tickets t JOIN bookings b ON b.bookingId = t.bookingId "
                    + "JOIN properties p ON p.propertyId = b.listingId "
                    + "WHERE p.hostId = ? ORDER BY t.createdAt DESC, t.ticketId";
            default -> throw new IllegalArgumentException("Only guests and hosts are ticket parties");
        };
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, JdbcCodecs.uuid(userId));
            try (var result = statement.executeQuery()) {
                List<Ticket> tickets = new ArrayList<>();
                while (result.next()) {
                    tickets.add(RowMappers.ticket(result));
                }
                return tickets;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query tickets for party", exception);
        }
    }

    @Override
    public List<Ticket> findByBookingId(UUID bookingId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE bookingId = ? ORDER BY createdAt, ticketId")) {
            statement.setString(1, JdbcCodecs.uuid(bookingId));
            try (var result = statement.executeQuery()) {
                List<Ticket> tickets = new ArrayList<>();
                while (result.next()) {
                    tickets.add(RowMappers.ticket(result));
                }
                return tickets;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query tickets by booking", exception);
        }
    }

    @Override
    public List<Ticket> findQueue(TicketStatus status, AssigneeFilter assignee, UUID agentId) {
        AssigneeFilter filter = assignee == null ? AssigneeFilter.ALL : assignee;
        if (filter == AssigneeFilter.MINE && agentId == null) {
            throw new IllegalArgumentException("AgentId is required for the MINE filter");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM tickets WHERE 1 = 1");
        List<String> params = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        if (filter == AssigneeFilter.UNASSIGNED) {
            sql.append(" AND assignedAgentId IS NULL");
        } else if (filter == AssigneeFilter.MINE) {
            sql.append(" AND assignedAgentId = ?");
            params.add(JdbcCodecs.uuid(agentId));
        }
        sql.append(" ORDER BY createdAt, ticketId");
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (var result = statement.executeQuery()) {
                List<Ticket> tickets = new ArrayList<>();
                while (result.next()) {
                    tickets.add(RowMappers.ticket(result));
                }
                return tickets;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query tickets", exception);
        }
    }

    @Override
    public List<Ticket> findByRaisedByUserId(UUID userId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE raisedByUserId = ? ORDER BY createdAt DESC")) {
            statement.setString(1, JdbcCodecs.uuid(userId));
            try (var result = statement.executeQuery()) {
                List<Ticket> tickets = new ArrayList<>();
                while (result.next()) {
                    tickets.add(RowMappers.ticket(result));
                }
                return tickets;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query tickets by user", exception);
        }
    }

    @Override
    public Ticket save(Ticket ticket) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO tickets (ticketId, bookingId, raisedByUserId, raisedByRole, category, "
                        + "title, description, requestedRemedy, supportingText, status, "
                        + "assignedAgentId, agentNotes, resolutionReason, createdAt, resolvedAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(ticketId) DO UPDATE SET status = excluded.status, "
                        + "assignedAgentId = excluded.assignedAgentId, "
                        + "agentNotes = excluded.agentNotes, "
                        + "resolutionReason = excluded.resolutionReason, "
                        + "resolvedAt = excluded.resolvedAt")) {
            statement.setString(1, JdbcCodecs.uuid(ticket.ticketId()));
            statement.setString(2, JdbcCodecs.uuid(ticket.bookingId()));
            statement.setString(3, JdbcCodecs.uuid(ticket.raisedByUserId()));
            statement.setString(4, ticket.raisedByRole().name());
            statement.setString(5, ticket.category());
            statement.setString(6, ticket.title());
            statement.setString(7, ticket.description());
            statement.setString(8, ticket.requestedRemedy().name());
            statement.setString(9, ticket.supportingText());
            statement.setString(10, ticket.status().name());
            statement.setString(11, JdbcCodecs.uuid(ticket.assignedAgentId()));
            statement.setString(12, ticket.agentNotes());
            statement.setString(13, ticket.resolutionReason());
            statement.setString(14, JdbcCodecs.instant(ticket.createdAt()));
            statement.setString(15, JdbcCodecs.instant(ticket.resolvedAt()));
            statement.executeUpdate();
            return ticket;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save ticket", exception);
        }
    }
}
