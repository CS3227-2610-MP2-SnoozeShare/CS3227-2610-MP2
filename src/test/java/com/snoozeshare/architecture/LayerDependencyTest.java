package com.snoozeshare.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class LayerDependencyTest {

    @Test
    void domainAndUiRemainIndependentOfFrameworkAndPersistence() throws Exception {
        assertNoImport("src/main/java/com/snoozeshare/domain", "javafx", "java.sql");
        assertNoImport("src/main/java/com/snoozeshare/ui", "com.snoozeshare.repository", "java.sql");
    }

    private static void assertNoImport(String directory, String... forbidden) throws Exception {
        try (Stream<Path> files = Files.walk(Path.of(directory))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String token : forbidden) {
                    assertFalse(source.contains(token), file + " imports " + token);
                }
            }
        }
    }
}
