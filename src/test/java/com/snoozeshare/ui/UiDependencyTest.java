package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class UiDependencyTest {

    @Test
    void uiSourcesDoNotImportRepositoriesOrJdbc() throws Exception {
        Path root = Path.of("src/main/java/com/snoozeshare/ui");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("com.snoozeshare.repository"), file.toString());
                assertFalse(source.contains("java.sql"), file.toString());
            }
        }
    }
}
