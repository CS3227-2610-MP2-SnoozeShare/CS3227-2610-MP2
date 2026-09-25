package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostCalendarControllerTest {

    @Test
    void calendarPageExposesNavigationLegendFormAndOverridePanel() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml"));

        assertTrue(fxml.contains("onAction=\"#handlePreviousMonth\""));
        assertTrue(fxml.contains("onAction=\"#handleNextMonth\""));
        assertTrue(fxml.contains("fx:id=\"monthLabel\""));
        assertTrue(fxml.contains("Available"));
        assertTrue(fxml.contains("Booked"));
        assertTrue(fxml.contains("Blocked"));
        assertTrue(fxml.contains("Other month"));
        assertTrue(fxml.contains("fx:id=\"calendarGrid\""));
        assertTrue(fxml.contains("fx:id=\"fromField\""));
        assertTrue(fxml.contains("fx:id=\"toField\""));
        assertTrue(fxml.contains("fx:id=\"reasonField\""));
        assertTrue(fxml.contains("Current overrides"));
        assertTrue(fxml.contains("onAction=\"#handleBack\""));
        assertFalse(fxml.contains("BorderPane.hgrow"));
    }

    @Test
    void controllerRendersListingBlocksAcrossNavigableMonths() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/calendar/HostCalendarController.java"));

        assertTrue(source.contains("YearMonth"));
        assertTrue(source.contains("blocksFor"));
        assertTrue(source.contains("HOST_BLOCK"));
        assertTrue(source.contains("BOOKING"));
        assertTrue(source.contains("calendar-cell-booked"));
        assertTrue(source.contains("calendar-cell-blocked"));
        assertTrue(source.contains("calendar-cell-other-month"));
        assertTrue(source.contains("setOnBack"));
        assertTrue(source.contains("setProperty"));
        assertTrue(source.contains("setContext"));
    }

    @Test
    void calendarSupportsBlockingAndRemovingAllManualOverrides() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/calendar/HostCalendarController.java"));
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml"));
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/theme.css"));

        assertTrue(source.contains("createHostBlock"));
        assertTrue(source.contains("fromField"));
        assertTrue(source.contains("toField"));
        assertTrue(source.contains("reasonField"));
        assertTrue(source.contains("removeHostBlock"));
        assertTrue(source.contains("handleRemoveOverride"));
        assertTrue(source.contains("refresh()"));
        assertTrue(fxml.contains("onAction=\"#handleBlockDates\""));
        assertTrue(theme.contains("calendar-cell-available"));
        assertTrue(theme.contains("calendar-cell-booked"));
        assertTrue(theme.contains("calendar-cell-blocked"));
        assertTrue(theme.contains("calendar-cell-other-month"));
        assertTrue(theme.contains("calendar-legend"));
        assertTrue(theme.contains("override-row"));
    }
}
