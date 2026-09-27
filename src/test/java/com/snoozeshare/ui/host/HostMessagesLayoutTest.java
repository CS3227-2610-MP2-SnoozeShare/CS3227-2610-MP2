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
        String css = Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/admin/agent-theme.css"));
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
        assertTrue(controller.contains("OverrunStyle.ELLIPSIS"));
        assertTrue(controller.contains("HBox.setHgrow(copy, Priority.ALWAYS)"));
        assertTrue(controller.contains("copy.setMinWidth(0)"));
        assertTrue(controller.contains("content.prefWidthProperty().bind(widthProperty().subtract(24))"));
        assertTrue(!fxml.contains("newTicketButton"));
        assertTrue(!controller.contains("setOnNewTicket"));
        assertTrue(!shellController.contains("setOnNewTicket"));
        assertTrue(!shellController.contains("showNewTicket"));
    }
}
