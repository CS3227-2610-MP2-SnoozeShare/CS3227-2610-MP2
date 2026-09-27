package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javafx.application.Platform;

class HostListingsControllerTest {

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Another JavaFX test owns the toolkit in this Gradle worker.
        }
    }

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
    void listingCardsOpenDetailsWithoutHijackingEdit() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));

        assertTrue(source.contains("setOnMouseClicked"));
        assertTrue(source.contains("onEditListing.accept"));
        assertTrue(source.contains("event.consume()"));
        assertTrue(source.contains("setOnViewListing"));
    }

    @Test
    void listingCardsExposeCalendarEntryWithoutHijackingDetails() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));

        assertTrue(source.contains("setOnOpenCalendar"));
        assertTrue(source.contains("Open booking calendar"));
        assertTrue(source.contains("onOpenCalendar.accept"));
        assertTrue(source.contains("event.consume()"));
    }

    @Test
    void listingsPageUsesMyListingsHeadingAndRightAlignedNewListingAction() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml"));

        assertTrue(fxml.contains("text=\"My listings\""));
        assertTrue(fxml.contains("text=\"+ New listing\""));
        assertTrue(fxml.contains("HBox.hgrow=\"ALWAYS\""));
        assertTrue(!fxml.contains("Manage your properties"));
        assertTrue(!fxml.contains("statusLabel"));
    }

    @Test
    void listingCardsExposeMetricsPlaceholderAndRightToLeftActions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/admin/agent-theme.css"));

        assertTrue(source.contains("listingMetricsService"));
        assertTrue(source.contains("listing-image-placeholder"));
        assertTrue(source.contains("RATING"));
        assertTrue(source.contains("BOOKINGS"));
        assertTrue(source.contains("\"★\""));
        assertTrue(theme.contains(".listing-card"));
        assertTrue(theme.contains("dropshadow"));
    }

    @Test
    void listingStatusControlReservesSpaceForBothStates() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/admin/agent-theme.css"));

        assertTrue(source.contains("listing-status-control"));
        assertTrue(theme.contains(".agent-root .listing-status-control"));
        assertTrue(theme.contains("-fx-pref-width: 100px"));
        assertTrue(theme.contains("-fx-max-width: 100px"));
    }

    @Test
    void hostRequestsAndWalletUseApprovedCopyAndButtonSizing() throws Exception {
        String shell = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-shell.fxml"));
        String bookings = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/bookings/host-bookings.fxml"));
        String wallet = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml"));

        assertTrue(shell.contains("text=\"Requests\""));
        assertTrue(bookings.contains("text=\"Booking requests\""));
        assertTrue(wallet.contains("text=\"Top up\""));
        assertTrue(wallet.contains("wallet-top-up-button"));
        assertTrue(wallet.contains("wallet-withdraw-button"));
    }

    @Test
    void walletActionsShareDimensionsAcrossGuestAndHost() throws Exception {
        String wallet = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml"));
        String walletCss = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/wallet/wallet.css"));

        assertTrue(wallet.contains("text=\"Top up\""));
        assertTrue(wallet.contains("text=\"Withdraw\""));
        assertTrue(walletCss.contains(".wallet-top-up-button"));
        assertTrue(walletCss.contains(".wallet-withdraw-button"));
    }

    @Test
    void listingFormActionsShareDimensions() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml"));
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/admin/agent-theme.css"));

        assertTrue(fxml.contains("styleClass=\"listing-form-action\""));
        assertTrue(fxml.contains("styleClass=\"outline-button, listing-form-action\""));
        assertTrue(theme.contains(".agent-root .listing-form-action"));
        assertTrue(theme.contains("-fx-pref-height: 40px"));
    }

    @Test
    void hostListingDetailPageDisplaysFullListingAndBackNavigation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingDetailController.java"));
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.fxml"));

        assertTrue(source.contains("setProperty"));
        assertTrue(source.contains("setOnBack"));
        assertTrue(source.contains("property.description()"));
        assertTrue(source.contains("property.amenities()"));
        assertTrue(fxml.contains("onAction=\"#handleBack\""));
        assertTrue(fxml.contains("fx:id=\"descriptionLabel\""));
        assertTrue(fxml.contains("fx:id=\"amenitiesPane\""));
        assertTrue(fxml.contains("fx:id=\"statusLabel\""));
    }

    @Test
    void hostListingDetailMatchesMockupActionsAndLayout() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingDetailController.java"));
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.fxml"));
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.css"));

        assertTrue(source.contains("setOnEdit"));
        assertTrue(source.contains("setOnOpenCalendar"));
        assertTrue(source.contains("capacityLabel.setText(Integer.toString(property.maxGuests()))"));
        assertTrue(source.contains("bedroomsLabel.setText(Integer.toString(property.bedrooms()))"));
        assertTrue(source.contains("bathroomsLabel.setText(formatNumber(property.bathrooms()))"));
        assertTrue(source.contains("rateLabel.setText(\"$\""));
        assertTrue(!source.contains(" + \" guests\""));
        assertTrue(!source.contains(" + \" bedrooms\""));
        assertTrue(!source.contains(" + \" bathrooms\""));
        assertTrue(fxml.contains("onAction=\"#handleEdit\""));
        assertTrue(fxml.contains("onAction=\"#handleOpenCalendar\""));
        assertTrue(fxml.contains("listing-detail-image-placeholder"));
        assertTrue(fxml.contains("listing-detail-stats"));
        assertTrue(fxml.contains("listing-detail-performance"));
        assertTrue(fxml.contains("text=\"Open booking calendar\""));
        assertTrue(fxml.contains("text=\"Edit\""));
        assertTrue(css.contains("linear-gradient"));
        assertTrue(css.contains(".listing-detail-action-edit"));
        assertTrue(css.contains(".listing-detail-performance"));
        assertTrue(!source.contains("Check-in: "));
        assertTrue(!source.contains("Check-out: "));
        assertTrue(source.contains("\"$\" + property.baseNightlyRate()"));
        assertTrue(!source.contains(" / night"));
    }

    @Test
    void hostListingDetailFxmlLoads() throws Exception {
        javafx.fxml.FXMLLoader.load(getClass().getResource(
                "/com/snoozeshare/ui/host/listings/host-listing-detail.fxml"));
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

    @Test
    void listingFormUsesFourCardsWithRequestedFieldsAndActions() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml"));
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingFormController.java"));

        assertTrue(fxml.contains("styleClass=\"listing-form-card\""));
        assertTrue(fxml.contains("text=\"Basic details\""));
        assertTrue(fxml.contains("text=\"Location\""));
        assertTrue(fxml.contains("text=\"Capacity &amp; Pricing\""));
        assertTrue(fxml.contains("text=\"Amenities\""));
        assertTrue(fxml.contains("fx:id=\"statusCombo\""));
        assertTrue(fxml.contains("fx:id=\"maxGuestsField\""));
        assertTrue(fxml.contains("text=\"Create listing\""));
        assertTrue(source.contains("saveButton.setText(\"Save listing\")"));
        assertTrue(fxml.contains("text=\"Cancel\""));
        assertTrue(fxml.contains("styleClass=\"outline-button\""));
        assertTrue(!fxml.contains("fillWidth"));
    }

    @Test
    void listingFormRejectsCheckoutAfterCheckin() throws Exception {
        Class<?> controller = Class.forName(
                "com.snoozeshare.ui.host.listings.HostListingFormController");
        Method validator = controller.getDeclaredMethod("validateTimeRange", LocalTime.class,
                LocalTime.class);
        validator.setAccessible(true);

        Executable invalidTimeRange = () -> invokeInvalidTimeRange(validator);
        InvocationTargetException exception = org.junit.jupiter.api.Assertions.assertThrows(
                InvocationTargetException.class, invalidTimeRange);

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("Check-out"));
    }

    private static Object invokeInvalidTimeRange(Method validator) throws Exception {
        return validator.invoke(null, LocalTime.of(15, 0), LocalTime.of(16, 0));
    }
}
