package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.repository.TicketCategoryRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcTicketCategoryRepository implements TicketCategoryRepository {

    private final Connection connection;

    public JdbcTicketCategoryRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<TicketCategory> findActive() {
        return query("SELECT * FROM ticket_categories WHERE active = 1 "
                + "ORDER BY label COLLATE NOCASE");
    }

    @Override
    public List<TicketCategory> findAll() {
        return query("SELECT * FROM ticket_categories ORDER BY label COLLATE NOCASE");
    }

    @Override
    public Optional<TicketCategory> findById(UUID categoryId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM ticket_categories WHERE categoryId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(categoryId));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(RowMappers.ticketCategory(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query ticket category", exception);
        }
    }

    @Override
    public boolean labelInUse(String label, UUID excludeCategoryId) {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM ticket_categories WHERE LOWER(label) = LOWER(?) "
                        + "AND (? IS NULL OR categoryId <> ?)")) {
            String excluded = JdbcCodecs.uuid(excludeCategoryId);
            statement.setString(1, label);
            statement.setString(2, excluded);
            statement.setString(3, excluded);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check ticket category label", exception);
        }
    }

    @Override
    public TicketCategory save(TicketCategory category) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO ticket_categories (categoryId, label, active) VALUES (?, ?, ?) "
                        + "ON CONFLICT(categoryId) DO UPDATE SET label = excluded.label, "
                        + "active = excluded.active")) {
            statement.setString(1, JdbcCodecs.uuid(category.categoryId()));
            statement.setString(2, category.label());
            statement.setInt(3, category.active() ? 1 : 0);
            statement.executeUpdate();
            return category;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save ticket category", exception);
        }
    }

    private List<TicketCategory> query(String sql) {
        try (var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            List<TicketCategory> categories = new ArrayList<>();
            while (result.next()) {
                categories.add(RowMappers.ticketCategory(result));
            }
            return categories;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query ticket categories", exception);
        }
    }
}
