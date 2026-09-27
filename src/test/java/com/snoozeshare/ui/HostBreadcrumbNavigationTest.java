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
        assertTrue(fxml.contains("styleClass=\"agent-crumb-link\""));
        assertTrue(fxml.contains("fx:id=\"titleCrumb\""));
        assertFalse(fxml.contains("text=\"Back\""));
    }

    @Test
    void listingFormUsesCreateOrEditBreadcrumbsWithoutPageTitle() throws Exception {
        String fxml = read("src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml");
        String source = read("src/main/java/com/snoozeshare/ui/host/listings/HostListingFormController.java");

        assertTrue(fxml.contains("text=\"Listings\""));
        assertTrue(fxml.contains("fx:id=\"listingCrumb\""));
        assertTrue(fxml.contains("fx:id=\"formBreadcrumb\""));
        assertFalse(fxml.contains("fx:id=\"formTitle\""));
        assertFalse(fxml.contains("text=\"Back\""));
        assertTrue(source.contains("formBreadcrumb.setText"));
        assertTrue(source.contains("listingCrumb.setText"));
    }

    @Test
    void calendarUsesListingBreadcrumbWithoutTitleOrDescription() throws Exception {
        String fxml = read("src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml");
        String source = read("src/main/java/com/snoozeshare/ui/host/calendar/HostCalendarController.java");

        assertTrue(fxml.contains("text=\"Listings\""));
        assertTrue(fxml.contains("fx:id=\"listingCrumb\""));
        assertTrue(fxml.contains("text=\"Booking Calendar\""));
        assertTrue(source.contains("listingCrumb.setText"));
        assertFalse(fxml.contains("text=\"Booking calendar\""));
        assertFalse(fxml.contains("View availability and manage manual date overrides."));
        assertFalse(fxml.contains("text=\"Back to listings\""));
    }

    @Test
    void shellWiresListingBreadcrumbTargets() throws Exception {
        String source = read("src/main/java/com/snoozeshare/ui/host/HostShellController.java");

        assertTrue(source.contains("controller.setOnListings(this::showListings)"));
        assertTrue(source.contains("controller.setOnListingDetail"));
    }

    private static String read(String file) throws Exception {
        return Files.readString(Path.of(file));
    }
}
