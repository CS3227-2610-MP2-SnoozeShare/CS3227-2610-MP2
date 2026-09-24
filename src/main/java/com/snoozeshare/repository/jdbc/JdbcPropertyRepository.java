package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;
import com.snoozeshare.service.SearchCriteria;

public final class JdbcPropertyRepository implements PropertyRepository {

    private final Connection connection;

    public JdbcPropertyRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Property> findById(UUID propertyId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM properties WHERE propertyId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(RowMappers.property(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query property", exception);
        }
    }

    @Override
    public List<Property> findByHostId(UUID hostId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM properties WHERE hostId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(hostId));
            try (var result = statement.executeQuery()) {
                List<Property> properties = new ArrayList<>();
                while (result.next()) {
                    properties.add(RowMappers.property(result));
                }
                return properties;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query properties by host", exception);
        }
    }

    @Override
    public List<Property> findBySearchCriteria(SearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("SELECT * FROM properties WHERE status = 'ACTIVE'");
        List<String> values = new ArrayList<>();
        if (criteria.city() != null) {
            sql.append(" AND LOWER(city) LIKE LOWER('%' || ? || '%')");
            values.add(criteria.city());
        }
        if (criteria.guests() != null) {
            sql.append(" AND maxGuests >= ?");
            values.add(String.valueOf(criteria.guests()));
        }
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
            try (var result = statement.executeQuery()) {
                List<Property> properties = new ArrayList<>();
                while (result.next()) {
                    properties.add(RowMappers.property(result));
                }
                return properties;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search properties", exception);
        }
    }

    @Override
    public Property save(Property property) {
        String amenitiesValue = property.amenities().stream()
                .map(AmenityType::name)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        try (var statement = connection.prepareStatement(
                "INSERT INTO properties (propertyId, hostId, status, title, description, "
                        + "propertyType, streetAddress, city, region, postalCode, maxGuests, "
                        + "bedrooms, bathrooms, baseNightlyRate, checkInTime, checkOutTime, "
                        + "amenities, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?) ON CONFLICT(propertyId) DO UPDATE SET "
                        + "hostId = excluded.hostId, status = excluded.status, "
                        + "title = excluded.title, description = excluded.description, "
                        + "propertyType = excluded.propertyType, "
                        + "streetAddress = excluded.streetAddress, city = excluded.city, "
                        + "region = excluded.region, postalCode = excluded.postalCode, "
                        + "maxGuests = excluded.maxGuests, bedrooms = excluded.bedrooms, "
                        + "bathrooms = excluded.bathrooms, "
                        + "baseNightlyRate = excluded.baseNightlyRate, "
                        + "checkInTime = excluded.checkInTime, "
                        + "checkOutTime = excluded.checkOutTime, "
                        + "amenities = excluded.amenities")) {
            statement.setString(1, JdbcCodecs.uuid(property.propertyId()));
            statement.setString(2, JdbcCodecs.uuid(property.hostId()));
            statement.setString(3, property.status().name());
            statement.setString(4, property.title());
            statement.setString(5, property.description());
            statement.setString(6, property.propertyType().name());
            statement.setString(7, property.streetAddress());
            statement.setString(8, property.city());
            statement.setString(9, property.region());
            statement.setString(10, property.postalCode());
            statement.setInt(11, property.maxGuests());
            statement.setInt(12, property.bedrooms());
            statement.setDouble(13, property.bathrooms());
            statement.setString(14, JdbcCodecs.decimal(property.baseNightlyRate()));
            statement.setString(15, JdbcCodecs.localTime(property.checkInTime()));
            statement.setString(16, JdbcCodecs.localTime(property.checkOutTime()));
            statement.setString(17, amenitiesValue);
            statement.setString(18, JdbcCodecs.instant(property.createdAt()));
            statement.executeUpdate();
            return property;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save property", exception);
        }
    }
}
