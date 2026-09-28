package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.model.Property;

class NumericPropertySchemaTest {

    @Test
    void propertyUsesIntegerBathroomAndPostalCodeComponents() {
        Map<String, Class<?>> components = java.util.Arrays.stream(Property.class.getRecordComponents())
                .collect(Collectors.toMap(component -> component.getName(), component -> component.getType()));

        assertEquals(int.class, components.get("bathrooms"));
        assertEquals(int.class, components.get("postalCode"));
    }

    @Test
    void schemaAndSeedDeclareNumericPropertyValues() throws Exception {
        String schema = Files.readString(Path.of("db/schema.sql"));
        String seed = Files.readString(Path.of("db/seed-mock-data.sql"));

        assertTrue(schema.contains("postalCode          INTEGER NOT NULL"));
        assertTrue(schema.contains("bathrooms           INTEGER NOT NULL"));
        assertFalse(schema.contains("bathrooms           REAL"));
        assertFalse(seed.contains("1200-192"));
        assertFalse(seed.contains("D02 XY45"));
    }

    @Test
    void committedMockDatabaseUsesIntegerPropertyColumns() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:db/snoozeshare-mock.db");
             var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(properties)")) {
            Map<String, String> types = new java.util.HashMap<>();
            while (result.next()) {
                types.put(result.getString("name"), result.getString("type"));
            }
            assertEquals("INTEGER", types.get("postalCode"));
            assertEquals("INTEGER", types.get("bathrooms"));
        }
    }
}
