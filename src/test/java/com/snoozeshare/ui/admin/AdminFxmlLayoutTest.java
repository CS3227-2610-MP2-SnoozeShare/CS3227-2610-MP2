package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminFxmlLayoutTest {

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/admin/" + relative));
    }

    private static String controller(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/snoozeshare/ui/admin/" + relative));
    }

    @Test
    void adminShellHostsDisputesAndCategoriesInItsCenterPane() throws Exception {
        String shell = read("admin-shell.fxml");
        String source = controller("AdminShellController.java");

        assertTrue(shell.contains("fx:id=\"shellRoot\""));
        assertTrue(shell.contains("onMouseClicked=\"#showCategories\""));
        assertTrue(shell.contains("onMouseClicked=\"#showDisputes\""));
        assertTrue(source.contains("showCategories"));
        assertTrue(source.contains("showDisputes"));
        assertTrue(source.contains("showOperations"));
        assertTrue(source.contains("showAccounts"));
    }

    @Test
    void queueScreenHasTheFiltersAndTable() throws Exception {
        String queue = read("tickets/dispute-queue.fxml");

        String[] ids = {"table", "allChip", "unassignedChip", "mineChip", "statusCombo", "unassignedBadge"};
        for (String id : ids) {
            assertTrue(queue.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(queue.contains("Dispute queue"));
    }

    @Test
    void noForceActionExistsAnywhereInTheAgentScreens() throws Exception {
        for (String file : new String[] {"admin-shell.fxml", "tickets/dispute-queue.fxml"}) {
            assertFalse(read(file).toLowerCase().contains("force"), file);
        }
        assertFalse(controller("AdminShellController.java").toLowerCase().contains("force"));
    }

    @Test
    void detailScreenHasBookingSummaryChatPanesNotesAndTheThreeResolutionActions() throws Exception {
        String detail = read("tickets/dispute-detail.fxml");
        String[] ids = {"crumbLabel", "statusBadge", "assignButton", "listingLabel",
            "datesLabel", "guestLabel", "hostLabel", "escrowLabel", "phaseLabel", "guestThread",
            "hostThread", "guestInput", "hostInput", "notesArea", "saveNotesButton",
            "acceptButton", "rejectButton", "manualButton", "errorLabel"};

        for (String id : ids) {
            assertTrue(detail.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(detail.contains("Internal notes"));
        assertFalse(detail.contains("notesHistory"), "notes are one persisted field, no history list");
        assertFalse(detail.toLowerCase().contains("force"));
    }

    @Test
    void resolutionDialogHasModeChipsAmountPreviewAndRequiredReason() throws Exception {
        String dialog = read("tickets/resolution-dialog.fxml");
        String[] ids = {"remedyLabel", "fullRefundChip", "fullPayoutChip", "customChip",
            "amountBox", "amountField", "previewLabel", "reasonArea", "errorLabel"};

        for (String id : ids) {
            assertTrue(dialog.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(dialog.contains("Reason (required, written to the audit log)"));
        assertFalse(dialog.contains("Adjust wallet"));
    }

    @Test
    void categoryScreenHasTheRowListHeaderAndAddButton() throws Exception {
        String screen = read("categories/category-admin.fxml");

        assertTrue(screen.contains("fx:id=\"rows\""));
        assertTrue(screen.contains("ACTIVE"));
        assertTrue(screen.contains("fx:id=\"errorLabel\""));
        assertTrue(screen.contains("Ticket categories"));
        assertTrue(screen.contains("onAction=\"#handleAdd\""));
        assertTrue(screen.contains("+ Add category"));
    }
}
