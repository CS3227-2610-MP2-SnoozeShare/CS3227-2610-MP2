package com.snoozeshare.ui.host;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostMessagesLayoutTest {

    @Test
    void hostMessagesSidebarHasFixedWidthAndOverflowLayout() throws IOException {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/messaging/host-messages.fxml"));
        String css = Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/host/host-theme.css"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/messaging/HostMessagesController.java"));
        String shellController = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/HostShellController.java"));

        assertTrue(fxml.contains("minWidth=\"380\""));
        assertTrue(fxml.contains("prefWidth=\"380\""));
        assertTrue(fxml.contains("maxWidth=\"380\""));
        assertTrue(css.contains("-fx-min-width: 380px;"));
        assertTrue(css.contains("-fx-max-width: 380px;"));
        assertTrue(css.contains(".host-message-sidebar .list-view .scroll-bar:horizontal"));
        assertTrue(fxml.contains("@../host-theme.css"));
        assertTrue(!fxml.contains("host-navigation.css"));
        assertTrue(!fxml.contains("agent-theme.css"));
        assertTrue(css.contains(".host-root .host-message-sidebar-title {"));
        assertTrue(!fxml.contains("top=\"18\""));
        assertTrue(css.contains(".host-root .host-message-row-title"));
        assertTrue(css.contains("-fx-font-size: 14px;"));
        assertTrue(css.contains(".host-root .host-message-row-subtitle { -fx-font-size: 13px;"));
        assertTrue(css.contains(".host-root .host-message-header-title {"));
        assertTrue(css.contains(".host-root .host-message-header-subtitle {"));
        assertTrue(css.contains(".host-crumb-link"));
        assertTrue(controller.contains("OverrunStyle.ELLIPSIS"));
        assertTrue(controller.contains("HBox.setHgrow(copy, Priority.ALWAYS)"));
        assertTrue(controller.contains("copy.setMinWidth(0)"));
        assertTrue(controller.contains("BorderPane titleLine = new BorderPane()"));
        assertTrue(controller.contains("titleLine.setRight(status)"));
        assertTrue(controller.contains("content.prefWidthProperty().bind(widthProperty().subtract(42))"));
        assertTrue(!fxml.contains("newTicketButton"));
        assertTrue(!controller.contains("setOnNewTicket"));
        assertTrue(!shellController.contains("setOnNewTicket"));
        assertTrue(!shellController.contains("showNewTicket"));
    }
}
