package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostBreadcrumbNavigationTest {

    @Test
    void listingDetailUsesListingsBreadcrumbInsteadOfBackButton() throws Exception {
        String fxml = read("src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.fxml");

        assertTrue(fxml.contains("text=\"Listings\""));
        assertTrue(fxml.contains("styleClass=\"host-crumb-link\""));
        assertFalse(fxml.contains("agent-crumb"));
        assertTrue(fxml.contains("fx:id=\"titleCrumb\""));
        assertFalse(fxml.contains("text=\"Back\""));
    }

    @Test
    void listingFormUsesCreateOrEditBreadcrumbsWithoutPageTitle() throws Exception {
        String fxml = read("src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml");
        String source = read("src/main/java/com/snoozeshare/ui/host/listings/HostListingFormController.java");

        assertTrue(fxml.contains("text=\"Listings\""));
        assertTrue(fxml.contains("fx:id=\"listingCrumb\""));
        assertTrue(fxml.contains("fx:id=\"editSeparator\""));
        assertTrue(fxml.contains("fx:id=\"formBreadcrumb\""));
        assertTrue(fxml.contains("styleClass=\"host-crumb-current\""));
        assertFalse(fxml.contains("agent-crumb"));
        assertFalse(fxml.contains("fx:id=\"formTitle\""));
        assertFalse(fxml.contains("text=\"Back\""));
        assertTrue(source.contains("formBreadcrumb.setText"));
        assertTrue(source.contains("listingCrumb.setText"));
        assertTrue(source.contains("editSeparator.setVisible"));
    }

    @Test
    void calendarUsesListingBreadcrumbWithoutTitleOrDescription() throws Exception {
        String fxml = read("src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml");
        String source = read("src/main/java/com/snoozeshare/ui/host/calendar/HostCalendarController.java");

        assertTrue(fxml.contains("text=\"Listings\""));
        assertTrue(fxml.contains("fx:id=\"listingCrumb\""));
        assertTrue(fxml.contains("text=\"Booking Calendar\""));
        assertTrue(fxml.contains("styleClass=\"host-crumb-current\""));
        assertFalse(fxml.contains("agent-crumb"));
        assertTrue(source.contains("listingCrumb.setText"));
        assertFalse(fxml.contains("text=\"Booking calendar\""));
        assertFalse(fxml.contains("View availability and manage manual date overrides."));
        assertFalse(fxml.contains("text=\"Back to listings\""));
    }

    @Test
    void shellWiresListingBreadcrumbTargets() throws Exception {
        String source = read("src/main/java/com/snoozeshare/ui/host/HostShellController.java");
        String shell = read("src/main/resources/com/snoozeshare/ui/host/host-shell.fxml");
        String css = read("src/main/resources/com/snoozeshare/ui/host/host-navigation.css");

        assertTrue(source.contains("controller.setOnListings(this::showListings)"));
        assertTrue(source.contains("controller.setOnListingDetail"));
        assertTrue(shell.contains("host-theme.css"));
        assertTrue(!shell.contains("agent-theme.css"));
        assertTrue(shell.contains("host-navigation.css"));
        assertTrue(css.contains(".host-crumb-link"));
    }

    private static String read(String file) throws Exception {
        return Files.readString(Path.of(file));
    }
}
