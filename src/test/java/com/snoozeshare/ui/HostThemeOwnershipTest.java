package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class HostThemeOwnershipTest {

    private static final Path HOST_RESOURCES = Path.of(
            "src/main/resources/com/snoozeshare/ui/host");
    private static final Pattern CSS_REFERENCE = Pattern.compile("@[^\\\"]+\\.css");

    @Test
    void everyHostFxmlUsesOnlyHostThemeStylesheet() throws Exception {
        try (Stream<Path> files = Files.walk(HOST_RESOURCES)) {
            for (Path fxml : files.filter(path -> path.toString().endsWith(".fxml")).toList()) {
                String source = Files.readString(fxml);
                Matcher matcher = CSS_REFERENCE.matcher(source);
                while (matcher.find()) {
                    assertTrue(matcher.group().endsWith("host-theme.css"),
                            fxml + " references non-canonical Host stylesheet " + matcher.group());
                }
            }
        }
    }

    @Test
    void secondaryHostStylesheetsAndRuntimeInjectionAreGone() throws Exception {
        try (Stream<Path> files = Files.walk(HOST_RESOURCES)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".css")
                    && !path.getFileName().toString().equals("host-theme.css")));
        }
        String shellController = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/HostShellController.java"));
        assertFalse(shellController.contains("calendarStylesheet"));
        assertFalse(shellController.contains("host-calendar.css"));
    }
}
