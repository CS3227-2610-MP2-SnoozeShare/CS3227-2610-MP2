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
        assertTrue(fxml.contains("<?import javafx.scene.control.DatePicker?>"));
        assertTrue(fxml.contains("<DatePicker fx:id=\"fromPicker\""));
        assertTrue(fxml.contains("<DatePicker fx:id=\"toPicker\""));
        assertTrue(!fxml.contains("fx:id=\"fromField\""));
        assertTrue(!fxml.contains("fx:id=\"toField\""));
        assertTrue(fxml.contains("fx:id=\"reasonField\""));
        assertTrue(fxml.contains("Current blocked dates"));
        assertTrue(fxml.contains("Block these dates"));
        assertTrue(fxml.contains("text=\"Reason\""));
        assertTrue(fxml.contains("hbarPolicy=\"NEVER\""));
        assertTrue(fxml.contains("vbarPolicy=\"AS_NEEDED\""));
        assertTrue(fxml.contains("minHeight=\"0\""));
        assertTrue(fxml.contains("onAction=\"#handleBack\""));
        assertFalse(fxml.contains("BorderPane.hgrow"));
        assertTrue(fxml.contains("<DatePicker fx:id=\"fromPicker\" maxWidth=\"Infinity\"/>"));
        assertTrue(fxml.contains("<DatePicker fx:id=\"toPicker\" maxWidth=\"Infinity\"/>"));
        assertTrue(fxml.contains("-fx-pref-width: 14px; -fx-max-width: 14px;"));
        assertTrue(fxml.contains("-fx-pref-height: 14px; -fx-max-height: 14px;"));
    }

    @Test
    void calendarPageOwnsArtifactAlignedSplitStyles() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml"));
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertFalse(fxml.contains("host-calendar.css"));
        assertTrue(fxml.contains("host-calendar-page"));
        assertTrue(fxml.contains("calendar-content"));
        assertTrue(fxml.contains("prefWidth=\"340\""));
        assertTrue(css.contains(".host-calendar-page"));
        assertTrue(css.contains(".calendar-side-panel"));
        assertTrue(css.contains("-fx-pref-width: 340px"));
        assertTrue(css.contains(".calendar-side-panel .date-picker"));
        assertTrue(css.contains("-fx-background-color: #fdf8f0;"));
        assertTrue(css.contains(".calendar-side-panel {\n"
                + "    -fx-background-color: #ffffff;\n"
                + "    -fx-background-radius: 12px;\n"
                + "    -fx-border-color: #d8c4a8;\n"
                + "    -fx-border-radius: 12px;\n"
                + "    -fx-border-width: 1px;\n"
                + "    -fx-padding: 20px;\n"
                + "    -fx-spacing: 12px;"));
        assertTrue(fxml.contains("styleClass=\"calendar-input-group\" spacing=\"4\""));
        assertTrue(css.contains(".calendar-cell-available"));
        assertTrue(css.contains(".calendar-cell-booked"));
        assertTrue(css.contains(".calendar-cell-blocked"));
        assertTrue(css.contains(".calendar-grid"));
        assertTrue(css.contains("-fx-border-color: #d8c4a8;"));
        assertTrue(css.contains(".calendar-section-divider"));
        assertTrue(css.contains(".override-reason"));
        assertTrue(css.contains(".override-dates"));
        assertTrue(css.contains("-fx-alignment: top-center;"));
        assertTrue(css.contains("-fx-padding: 12px 0 0 0;"));
        assertTrue(css.contains("-fx-min-width: 14px;"));
        assertTrue(css.contains("-fx-min-height: 14px;"));
        assertTrue(css.contains("-fx-max-height: 14px;"));
        assertTrue(css.contains("-fx-spacing: 4px;"));
        assertTrue(css.contains("-fx-font-size: 12px;"));
        assertTrue(css.contains("-fx-background-color: rgba(16,6,4,0.15);"));
        assertTrue(css.contains(".calendar-cell-other-month {\n    -fx-background-color: #e2ded6;"));
        assertTrue(css.contains(".override-reason {"));
        assertTrue(css.contains(".override-row .text-button {"));
        assertTrue(!fxml.contains("agent-theme.css"));
        assertTrue(!css.contains("agent-theme.css"));
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
        assertTrue(source.contains("WEEKDAY_LABELS"));
        assertTrue(source.contains("{\"SUN\", \"MON\", \"TUE\", \"WED\", \"THU\", \"FRI\", \"SAT\"}"));
        assertTrue(source.contains("calendar-weekday"));
        assertTrue(source.contains("setOnBack"));
        assertTrue(source.contains("setProperty"));
        assertTrue(source.contains("setContext"));
        assertTrue(source.contains("fieldName + \" date must be provided.\""));
        assertTrue(source.contains("fieldName + \" date must be valid.\""));
        assertTrue(source.contains("Reason must be provided."));
        assertTrue(!source.contains("Dates blocked."));
        assertTrue(source.contains("No reason provided"));
        assertTrue(source.contains("new Button(\"remove\")"));
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
        assertTrue(source.contains("fromPicker"));
        assertTrue(source.contains("toPicker"));
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
