package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertTrue(fxml.contains("@host-bookings.css"));
        assertTrue(!fxml.contains("agent-theme.css"));
        assertTrue(!fxml.contains("agent-table"));
        assertTrue(!fxml.contains("agent-card"));
        assertTrue(fxml.contains("pendingGuestColumn\" text=\"Guest\" prefWidth=\"150\""));
        assertTrue(fxml.contains("pendingListingColumn\" text=\"Listing\" prefWidth=\"190\""));
        assertTrue(fxml.contains("pendingDatesColumn\" text=\"Dates\" prefWidth=\"130\""));
        assertTrue(fxml.contains("pendingNightsColumn\" text=\"Nights\" prefWidth=\"90\""));
        assertTrue(fxml.contains("pendingGrossColumn\" text=\"Gross\" prefWidth=\"130\""));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/bookings/HostBookingsController.java"));
        assertTrue(controller.contains("Approve"));
        assertTrue(controller.contains("Reject"));
        assertTrue(controller.contains("pastStatusColumn.setCellFactory"));
        assertTrue(controller.contains("host-bookings-status-confirmed"));
        assertTrue(controller.contains("host-bookings-status-rejected"));
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
        assertTrue(fxml.contains("text=\"\\$0.00\""));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/bookings/"
                        + "HostBookingDecisionDialogController.java"));
        assertTrue(controller.contains("Reject booking request?"));
        assertTrue(controller.contains("Confirm reject"));
    }

    @Test
    void decisionModalUsesAnAbsoluteClasspathResource() throws Exception {
        assertNotNull(HostBookingsControllerTest.class.getResource(
                "/com/snoozeshare/ui/host/bookings/host-booking-decision-dialog.fxml"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/bookings/HostBookingDecisionDialogController.java"));
        assertTrue(controller.contains("getResource(\"/com/snoozeshare/ui/host/bookings/"
                + "host-booking-decision-dialog.fxml\")"));
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
