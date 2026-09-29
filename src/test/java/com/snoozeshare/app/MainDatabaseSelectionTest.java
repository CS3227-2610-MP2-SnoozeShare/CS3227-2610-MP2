package com.snoozeshare.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainDatabaseSelectionTest {

    @TempDir
    Path directory;

    @Test
    void explicitUrlWinsOverMockDatabase() throws Exception {
        Path mock = Files.createFile(directory.resolve("mock.db"));
        assertEquals("jdbc:sqlite:custom.db", Main.resolveDatabaseUrl("jdbc:sqlite:custom.db", mock));
    }

    @Test
    void presentMockDatabaseIsOpenedWhenNothingIsConfigured() throws Exception {
        Path mock = Files.createFile(directory.resolve("mock.db"));
        assertEquals("jdbc:sqlite:" + mock, Main.resolveDatabaseUrl(null, mock));
        assertEquals("jdbc:sqlite:" + mock, Main.resolveDatabaseUrl("  ", mock));
    }

    @Test
    void missingMockDatabaseFallsBackToInMemory() {
        assertNull(Main.resolveDatabaseUrl(null, directory.resolve("absent.db")));
    }
}
