package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
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
        assertTrue(fxml.contains("onAction=\"#handleListings\""));
        assertTrue(fxml.contains("onAction=\"#handleListingDetail\""));
        assertTrue(fxml.contains("onAction=\"#handleSave\""));
        assertTrue(fxml.contains("styleClass=\"listing-description, listing-form-field\""));
        assertTrue(fxml.contains("fx:id=\"formBreadcrumb\""));
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
        assertTrue(source.contains("new Button(\"Open Booking Calendar\")"));
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
    void listingsPageOwnsArtifactAlignedStyles() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml"));
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertFalse(fxml.contains("host-listings.css"));
        assertTrue(fxml.contains("styleClass=\"host-listings-page\""));
        assertTrue(fxml.contains("styleClass=\"host-listings-header\""));
        assertTrue(css.contains(".host-listings-page"));
        assertTrue(css.contains(".listing-card"));
        assertTrue(css.contains("-fx-pref-width: 64px"));
        assertTrue(css.contains(".listing-metric-value"));
        assertTrue(css.contains(".listing-status-toggle"));
        assertTrue(!fxml.contains("agent-theme.css"));
        assertTrue(!css.contains("agent-theme.css"));
    }

    @Test
    void listingRowsUseArtifactActionAndMetricClasses() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));

        assertTrue(source.contains("listing-card"));
        assertTrue(source.contains("listing-image-placeholder"));
        assertTrue(source.contains("listing-metric-value"));
        assertTrue(source.contains("listing-metric-caption"));
        assertTrue(source.contains("listing-status-control"));
        assertTrue(source.contains("listing-card-action"));
    }

    @Test
    void activeListingToggleUsesAgentStyleKnob() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertTrue(source.contains("new Region()"));
        assertTrue(source.contains("listing-status-toggle-knob"));
        assertTrue(source.contains("setContentDisplay(ContentDisplay.GRAPHIC_ONLY)"));
        assertTrue(css.contains(".listing-status-toggle-knob"));
        assertTrue(css.contains("-fx-alignment: CENTER_LEFT"));
        assertTrue(css.contains(".listing-status-toggle:selected"));
        assertTrue(css.contains("-fx-alignment: CENTER_RIGHT"));
    }

    @Test
    void listingFormUsesHelpfulPlaceholdersAndOptionalAgentStyleAmenities() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml"));
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertTrue(fxml.contains("text=\"Optional\""));
        assertTrue(fxml.contains("promptText=\"e.g. Sunset Loft\""));
        assertTrue(fxml.contains("promptText=\"e.g. 12 Rua das Flores\""));
        assertTrue(fxml.contains("promptText=\"e.g. 1200\""));
        assertTrue(fxml.contains("promptText=\"e.g. 1\""));
        assertTrue(fxml.contains("promptText=\"e.g. 120.00\""));
        assertTrue(fxml.contains("promptText=\"e.g. 15:00\""));
        assertTrue(fxml.contains("styleClass=\"listing-form-field\""));
        assertTrue(css.contains(".amenity-option"));
        assertTrue(css.contains("-fx-padding: 7px 10px 7px 10px"));
        assertTrue(css.contains("-fx-spacing: 4px"));
        assertTrue(css.contains(".listing-form-card"));
    }

    @Test
    void listingCardsExposeMetricsPlaceholderAndRightToLeftActions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertTrue(source.contains("listingMetricsService"));
        assertTrue(source.contains("listing-image-placeholder"));
        assertTrue(source.contains("RATING"));
        assertTrue(source.contains("BOOKINGS"));
        assertTrue(source.contains("\"★\""));
        assertTrue(theme.contains(".listing-card"));
        assertTrue(theme.contains("dropshadow"));
        assertTrue(theme.contains(".host-listings-page .listing-card-action {"));
        assertTrue(theme.contains(".host-listings-page .listing-card-action {\n"
                + "    -fx-background-color: transparent;"));
        assertTrue(theme.contains(".host-listings-page .listing-card-action {\n"
                + "    -fx-background-color: transparent;\n"
                + "    -fx-background-radius: 8px;"));
        assertTrue(theme.contains("-fx-font-weight: bold;"));
    }

    @Test
    void listingStatusControlReservesSpaceForBothStates() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertTrue(source.contains("listing-status-control"));
        assertTrue(theme.contains(".host-root .listing-status-control"));
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
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertTrue(fxml.contains("styleClass=\"listing-form-action\""));
        assertTrue(fxml.contains("styleClass=\"outline-button, listing-form-action\""));
        assertTrue(fxml.contains("<HBox alignment=\"CENTER_LEFT\" spacing=\"10\""
                + " styleClass=\"listing-form-actions\">"));
        assertTrue(fxml.contains("<VBox fx:id=\"formRoot\" styleClass=\"listing-form-page\" spacing=\"8\">"));
        assertTrue(theme.contains(".host-root .listing-form-action"));
        assertTrue(theme.contains("-fx-pref-height: 34px"));
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
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertTrue(source.contains("setOnEdit"));
        assertTrue(source.contains("setOnOpenCalendar"));
        assertTrue(source.contains("capacityLabel.setText(Integer.toString(property.maxGuests()))"));
        assertTrue(source.contains("bedroomsLabel.setText(Integer.toString(property.bedrooms()))"));
        assertTrue(source.contains("bathroomsLabel.setText(formatNumber(property.bathrooms()))"));
        assertTrue(source.contains("rateLabel.setText(\"$\""));
        assertTrue(source.contains("statusLabel.getStyleClass().removeAll"));
        assertTrue(source.contains("listing-detail-status-active"));
        assertTrue(source.contains("listing-detail-status-inactive"));
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
        assertTrue(css.contains(".host-listings-page .listing-image-placeholder {\n"
                + "    -fx-background-color: linear-gradient(to right, #e06830, #c0a080, #a07c5c);"));
        assertTrue(css.contains(".listing-detail-status-inactive"));
        assertTrue(css.contains("-fx-background-color: #fcd8da;"));
        assertTrue(css.contains("-fx-text-fill: #980c1c;"));
        assertTrue(!source.contains("Check-in: "));
        assertTrue(!source.contains("Check-out: "));
        assertTrue(source.contains("\"$\" + property.baseNightlyRate()"));
        assertTrue(!source.contains(" / night"));
    }

    @Test
    void hostListingDetailContentUsesArtifactAlignedVisualTreatment() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.fxml"));
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertTrue(fxml.contains("<VBox spacing=\"18\" styleClass=\"host-listing-detail-page\">"));
        assertTrue(fxml.contains("prefHeight=\"200\""));
        assertTrue(fxml.contains("<GridPane hgap=\"24\" styleClass=\"listing-detail-content\">"));
        assertTrue(css.contains("-fx-pref-height: 200px;"));
        assertTrue(css.contains("-fx-background-color: #f0e8d8;"));
        assertTrue(css.contains("-fx-background-color: #ffffff;"));
        assertTrue(css.contains("-fx-border-color: #c0a080;"));
        assertTrue(css.contains("-fx-font-size: 22px;"));
    }

    @Test
    void hostListingDetailUsesArtifactSectionsForReviewsPerformanceAndAmenities() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.fxml"));
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertTrue(fxml.contains("fx:id=\"reviewsTitleLabel\""));
        assertTrue(fxml.contains("styleClass=\"listing-detail-reviews\""));
        assertTrue(fxml.contains("text=\"Occupancy (30d)\""));
        assertTrue(fxml.contains("text=\"Earnings (30d)\""));
        assertTrue(fxml.contains("percentWidth=\"60\""));
        assertTrue(fxml.contains("percentWidth=\"40\""));
        assertTrue(fxml.contains("fx:id=\"reviewsList\""));
        assertTrue(fxml.contains("fx:id=\"occupancyLabel\""));
        assertTrue(fxml.contains("fx:id=\"earningsLabel\""));
        assertTrue(css.contains(".listing-detail-reviews"));
        assertTrue(css.contains(".listing-detail-review"));
        assertTrue(css.contains(".listing-detail-review-row"));
        assertTrue(css.contains(".listing-detail-amenities"));
        assertTrue(css.contains("-fx-padding: 5px 12px 5px 12px;"));
        assertTrue(css.contains("-fx-font-size: 12px;"));
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
        assertTrue(fxml.contains("<?import javafx.scene.control.TextArea?>"));
        assertTrue(fxml.contains("<TextArea fx:id=\"descriptionField\""));
        assertTrue(fxml.contains("wrapText=\"true\""));
        assertTrue(fxml.contains("prefRowCount=\"3\""));
        assertTrue(fxml.contains("styleClass=\"listing-form-column\""));
        assertTrue(fxml.contains("text=\"Create listing\""));
        assertTrue(source.contains("saveButton.setText(\"Save listing\")"));
        assertTrue(fxml.contains("text=\"Cancel\""));
        assertTrue(fxml.contains("styleClass=\"outline-button, listing-form-action\""));
        assertTrue(!fxml.contains("fillWidth"));
    }

    @Test
    void listingFormUsesArtifactPageOwnedStructure() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml"));
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));

        assertFalse(fxml.contains("host-listing-form.css"));
        assertTrue(fxml.contains("styleClass=\"listing-form-page\""));
        assertTrue(fxml.contains("percentWidth=\"50\""));
        assertTrue(fxml.contains("styleClass=\"listing-form-actions\""));
        assertTrue(fxml.contains("styleClass=\"amenity-option\""));
        assertTrue(css.contains(".listing-form-page"));
        assertTrue(css.contains(".listing-form-card"));
        assertTrue(css.contains(".listing-form-column"));
        assertTrue(css.contains(".listing-form-page .listing-description .content"));
        assertTrue(css.contains("-fx-padding: 0;"));
        assertTrue(css.contains(".listing-form-actions"));
        assertTrue(css.contains("-fx-padding: 20px"));
        assertTrue(!fxml.contains("agent-theme.css"));
        assertTrue(!css.contains("agent-theme.css"));
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

    @Test
    void listingFormReportsFriendlyRateAndIntegerInputErrors() throws Exception {
        Class<?> controller = Class.forName(
                "com.snoozeshare.ui.host.listings.HostListingFormController");
        Method parseRate = controller.getDeclaredMethod("parseRate", String.class);
        parseRate.setAccessible(true);

        InvocationTargetException blank = assertThrows(InvocationTargetException.class, () ->
                parseRate.invoke(null, ""));
        assertEquals("Rate per night must be provided", blank.getCause().getMessage());

        InvocationTargetException malformed = assertThrows(InvocationTargetException.class, () ->
                parseRate.invoke(null, "abc"));
        assertEquals("Rate per night must be a number", malformed.getCause().getMessage());
        assertEquals(new BigDecimal("120.00"), parseRate.invoke(null, "120.00"));
    }

    @Test
    void listingFormReadsFieldsInSectionOrder() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingFormController.java"));
        assertTrue(source.indexOf("requireText(titleField") < source.indexOf("parseInteger(maxGuestsField"));
        assertTrue(source.indexOf("parseInteger(maxGuestsField") < source.indexOf("requireText(streetAddressField"));
        assertTrue(source.indexOf("requireText(streetAddressField") < source.indexOf("selectedAmenities()"));
    }

    private static Object invokeInvalidTimeRange(Method validator) throws Exception {
        return validator.invoke(null, LocalTime.of(15, 0), LocalTime.of(16, 0));
    }
}
