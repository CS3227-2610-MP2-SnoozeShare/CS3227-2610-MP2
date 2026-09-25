package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShellNavigationTest {

    private static final List<String> SHELLS = List.of(
            "guest/guest-shell.fxml", "host/host-shell.fxml", "admin/admin-shell.fxml");

    @Test
    void eachShellUsesTextNavigationAndASharedCenterPageMessage() throws Exception {
        for (String shell : SHELLS) {
            String fxml = Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/" + shell));
            assertTrue(fxml.contains("fx:id=\"pageMessage\""), shell);
            assertTrue(fxml.contains("styleClass=\"nav-item\""), shell);
            assertTrue(fxml.contains("onMouseClicked=\"#"), shell);
        }
    }

    @Test
    void roleControllersDeclareTheirNavigationDestinations() throws Exception {
        assertControllerMethods("guest/GuestShellController.java", "showExplore",
                "showMyTrips", "showWallet", "showSupport");
        assertControllerMethods("host/HostShellController.java", "showDashboard",
                "showListings", "showBookings");
        assertControllerMethods("admin/AdminShellController.java", "showOperations",
                "showDisputes", "showAccounts");
    }

    @Test
    void hostListingsPageProvidesManagementSurface() throws Exception {
        String shell = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-shell.fxml"));
        String page = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java"));

        assertTrue(shell.contains("fx:id=\"shellRoot\""));
        assertTrue(page.contains("HostListingsController"));
        assertTrue(page.contains("Create Listing"));
        assertTrue(page.contains("listingCards"));
        assertTrue(page.contains("statusLabel"));
        assertTrue(!page.contains("descriptionField"));
        assertTrue(controller.contains("setContext"));
        assertTrue(controller.contains("reload"));

        String hostController = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/HostShellController.java"));
        assertTrue(hostController.contains("showCreateListing"));
        assertTrue(hostController.contains("showEditListing"));
        assertTrue(hostController.contains("showListingDetail"));
        assertTrue(hostController.contains("host-listing-detail.fxml"));
        assertTrue(hostController.contains("host-listing-form.fxml"));
    }

    private static void assertControllerMethods(String file, String... methods) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/snoozeshare/ui/" + file));
        for (String method : methods) {
            assertTrue(source.contains(method), file + " missing " + method);
        }
    }
}
