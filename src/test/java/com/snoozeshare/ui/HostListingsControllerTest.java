package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostListingsControllerTest {

    @Test
    void listingsPageExposesCreateStatusAndEditFlow() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml"));

        assertTrue(source.contains("listingService().updateStatus"));
        assertTrue(source.contains("ToggleButton"));
        assertTrue(source.contains("setOnEditListing"));
        assertTrue(source.contains("setMaxWidth(Double.MAX_VALUE)"));
        assertTrue(source.contains("new Button(\"Edit\")"));
        assertTrue(fxml.contains("onAction=\"#handleCreateListing\""));
        assertTrue(fxml.contains("listingCards"));
        assertTrue(!fxml.contains("descriptionField"));
    }

    @Test
    void listingFormExposesCreateEditBackAndSaveFlow() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingFormController.java"));
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml"));

        assertTrue(source.contains("listingService().create"));
        assertTrue(source.contains("listingService().update"));
        assertTrue(source.contains("setOnBack"));
        assertTrue(source.contains("setOnSaved"));
        assertTrue(fxml.contains("onAction=\"#handleBack\""));
        assertTrue(fxml.contains("onAction=\"#handleSave\""));
        assertTrue(fxml.contains("styleClass=\"listing-description\""));
        assertTrue(fxml.contains("fx:id=\"formTitle\""));
    }

    @Test
    void listingsStylesDefineCardsStatusesErrorsAndEmptyState() throws Exception {
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/theme.css"));

        assertTrue(theme.contains(".listing-card"));
        assertTrue(theme.contains(".listing-status"));
        assertTrue(theme.contains(".listing-description"));
        assertTrue(theme.contains(".form-error"));
        assertTrue(theme.contains(".empty-state"));
    }
}
