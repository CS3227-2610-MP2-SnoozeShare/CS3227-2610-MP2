package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostPageLayoutTest {

    private static final String STANDARD_PADDING =
            "<padding><Insets top=\"24\" right=\"32\" bottom=\"24\" left=\"32\"/></padding>";

    @Test
    void hostPagesUseAgentContentInsetsAndSpacing() throws Exception {
        assertStandardPage("bookings/host-bookings.fxml", "agent-content, host-bookings-page");
        assertStandardPage("listings/host-listings.fxml", "agent-content, host-listings-page");
        assertStandardPage("listings/host-listing-form.fxml", "agent-content, listing-form-page");
        assertStandardPage("listings/host-listing-detail.fxml", "agent-content, host-listing-detail-page");
        String calendar = read("calendar/host-calendar.fxml");
        assertTrue(calendar.contains("styleClass=\"agent-content, host-calendar-page calendar-content\""));
        assertTrue(calendar.contains(STANDARD_PADDING));
        String wallet = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml"));
        assertTrue(wallet.contains(STANDARD_PADDING));
    }

    @Test
    void hostHeadersReserveRightSpaceForOptionalActions() throws Exception {
        assertTrue(read("listings/host-listings.fxml").contains("<Region HBox.hgrow=\"ALWAYS\"/>"));
        assertTrue(read("listings/host-listing-detail.fxml").contains("<Region HBox.hgrow=\"ALWAYS\"/>"));
        assertTrue(read("bookings/host-bookings.fxml").contains("<Region HBox.hgrow=\"ALWAYS\"/>"));
        assertTrue(read("calendar/host-calendar.fxml").contains("<Region HBox.hgrow=\"ALWAYS\"/>"));
    }

    @Test
    void hostMessagesUseTheSameContentEdgeRhythm() throws Exception {
        String css = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-theme.css"));
        assertTrue(css.contains(".host-root .host-message-header {\n    -fx-padding: 16px 32px;"));
        assertTrue(css.contains(".host-root .host-message-composer {\n    -fx-padding: 16px 32px 24px 32px;"));
        assertTrue(css.contains(".host-root .host-message-thread {\n    -fx-padding: 24px 32px 24px 32px;"));
    }

    private static void assertStandardPage(String relativePath, String styleClass) throws Exception {
        String fxml = read(relativePath);
        assertTrue(fxml.contains("styleClass=\"" + styleClass + "\""));
        assertTrue(fxml.contains("spacing=\"16\""));
        assertTrue(fxml.contains(STANDARD_PADDING));
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/host", relativePath));
    }
}
