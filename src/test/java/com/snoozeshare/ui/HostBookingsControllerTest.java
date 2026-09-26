package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostBookingsControllerTest {

    @Test
    void bookingRequestsPageDeclaresMockupTablesAndCopy() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/bookings/host-bookings.fxml"));
        assertTrue(fxml.contains("Booking requests"));
        assertTrue(fxml.contains("pendingCountLabel"));
        assertTrue(fxml.contains("pendingTable"));
        assertTrue(fxml.contains("pastTable"));
        assertTrue(fxml.contains("Guest"));
        assertTrue(fxml.contains("Listing"));
        assertTrue(fxml.contains("Net earning"));
        assertTrue(fxml.contains("Past requests"));
        assertTrue(fxml.contains("host-bookings-table"));
        assertTrue(fxml.contains("host-bookings-table-card"));
        assertTrue(!fxml.contains("agent-table"));
        assertTrue(!fxml.contains("agent-card"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/bookings/HostBookingsController.java"));
        assertTrue(controller.contains("Approve"));
        assertTrue(controller.contains("Reject"));
    }

    @Test
    void decisionModalContainsApprovedCopyAndOptionalRejectMessage() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/bookings/"
                        + "host-booking-decision-dialog.fxml"));
        assertTrue(fxml.contains("Approve booking request?"));
        assertTrue(fxml.contains("Confirm approve"));
        assertTrue(fxml.contains("Message to guest (optional)"));
        assertTrue(fxml.contains("agent-booking-summary"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/bookings/"
                        + "HostBookingDecisionDialogController.java"));
        assertTrue(controller.contains("Reject booking request?"));
        assertTrue(controller.contains("Confirm reject"));
    }

    @Test
    void controllerUsesScopedRowsAndDecisionModal() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/bookings/HostBookingsController.java"));
        assertTrue(source.contains("pendingRequestRowsFor"));
        assertTrue(source.contains("historyRowsFor"));
        assertTrue(source.contains("HostBookingDecisionDialogController"));
        assertTrue(source.contains("feedbackLabel"));
    }

    @Test
    void hostShellPassesContextToBookingRequestsController() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/HostShellController.java"));
        assertTrue(source.contains("HostBookingsController controller = loader.getController();"));
        assertTrue(source.contains("controller.setContext(getContext());"));
    }
}
