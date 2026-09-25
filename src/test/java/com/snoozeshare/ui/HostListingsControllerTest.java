package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostListingsControllerTest {

    @Test
    void listingsControllerExposesCreateAndStatusMutationFlow() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml"));

        assertTrue(source.contains("listingService().create"));
        assertTrue(source.contains("listingService().updateStatus"));
        assertTrue(source.contains("DateTimeFormatter.ofPattern(\"HH:mm\")"));
        assertTrue(source.contains("errorLabel.setText"));
        assertTrue(source.contains("success-message"));
        assertTrue(source.contains("form-error"));
        assertTrue(fxml.contains("onAction=\"#handleCreate\""));
        assertTrue(source.contains("status-toggle"));
        assertTrue(fxml.contains("bedroomsField"));
        assertTrue(fxml.contains("bathroomsField"));
    }

    @Test
    void listingsStylesDefineCardsStatusesErrorsAndEmptyState() throws Exception {
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/theme.css"));

        assertTrue(theme.contains(".listing-card"));
        assertTrue(theme.contains(".listing-status"));
        assertTrue(theme.contains(".form-error"));
        assertTrue(theme.contains(".empty-state"));
    }
}
