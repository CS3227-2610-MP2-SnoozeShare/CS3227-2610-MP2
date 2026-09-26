package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostShellControllerTest {

    @Test
    void hostShellLoadsListingSpecificCalendarWithContextAndBackCallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/HostShellController.java"));

        assertTrue(source.contains("showCalendar(Property"));
        assertTrue(source.contains("host/calendar/host-calendar.fxml"));
        assertTrue(source.contains("controller.setContext(getContext())"));
        assertTrue(source.contains("controller.setProperty(property)"));
        assertTrue(source.contains("controller.setOnBack(this::showListings)"));
    }
}
