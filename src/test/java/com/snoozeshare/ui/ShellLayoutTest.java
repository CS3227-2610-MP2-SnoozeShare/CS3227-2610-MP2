package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShellLayoutTest {

    private static final List<String> SHELLS = List.of(
            "guest/guest-shell.fxml", "host/host-shell.fxml", "admin/admin-shell.fxml");

    @Test
    void shellHeadersCenterAndRightAlignRoleLabels() throws Exception {
        for (String shell : SHELLS) {
            String fxml = Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/" + shell));
            assertTrue(fxml.contains("alignment=\"CENTER_LEFT\""), shell);
            assertTrue(fxml.contains("HBox.hgrow=\"ALWAYS\""), shell);
            assertTrue(fxml.contains("styleClass=\"outline-button\""), shell);
        }
    }

    @Test
    void navigationSidebarHasWiderSharedPreferredWidth() throws Exception {
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/theme.css"));

        assertTrue(theme.contains(".sidebar"));
        assertTrue(theme.contains("-fx-pref-width: 220px"));
    }

    @Test
    void guestAndHostWalletPanelsUseCenterTitleStyleAndTwoDecimalAmount() throws Exception {
        for (String shell : List.of("guest/guest-shell.fxml", "host/host-shell.fxml")) {
            String fxml = Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/" + shell));
            assertTrue(fxml.contains("fx:id=\"walletTitle\""), shell);
            assertTrue(fxml.contains("fx:id=\"walletAmount\""), shell);
            assertTrue(fxml.contains("styleClass=\"page-title\""), shell);
        }
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/common/NavShellController.java"));
        assertTrue(controller.contains("setScale(2"));
        assertTrue(controller.contains("SGD"));
    }
}
