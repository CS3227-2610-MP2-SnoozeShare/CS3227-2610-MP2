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
            if (!shell.equals("host/host-shell.fxml")) {
                assertTrue(fxml.contains("fx:id=\"pageMessage\""), shell);
            }
            String navClass = "agent-tab";
            assertTrue(fxml.contains("styleClass=\"" + navClass), shell);
            assertTrue(fxml.contains("onMouseClicked=\"#"), shell);
        }
    }

    @Test
    void guestAndHostShellsExposeTheApprovedInitialNavigation() throws Exception {
        String guestPage = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/guest/guest-shell.fxml"));
        String hostPage = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/host-shell.fxml"));

        assertTrue(guestPage.contains("text=\"Search\""));
        assertTrue(hostPage.contains("text=\"Listings\""));
        assertTrue(!hostPage.contains("text=\"Dashboard\""));
        assertTrue(hostPage.contains("text=\"Requests\""));
        assertTrue(hostPage.contains("</center>"));
    }

    @Test
    void roleControllersDeclareTheirNavigationDestinations() throws Exception {
        assertControllerMethods("guest/GuestShellController.java", "showExplore",
                "showMyTrips", "showWallet", "showSupport");
        assertControllerMethods("host/HostShellController.java", "showListings",
                "showBookings", "showWallet", "cleanupWalletController");
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
        assertTrue(page.contains("+ New listing"));
        assertTrue(page.contains("listingCards"));
        assertTrue(!page.contains("statusLabel"));
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
        assertTrue(hostController.contains("host-wallet-dashboard.fxml"));
        assertTrue(hostController.contains("shellRoot.setCenter(walletView)"));
        assertTrue(hostController.contains("shellRoot.setCenter(listingsView)"));
        assertTrue(hostController.contains("shellRoot.setCenter(detailView)"));
        assertTrue(hostController.contains("shellRoot.setCenter(formView)"));
    }

    @Test
    void authenticatedShellsLoadTheirFirstPagesFromContextSetup() throws Exception {
        String guestController = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/guest/GuestShellController.java"));
        String hostController = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/host/HostShellController.java"));

        assertTrue(guestController.contains("void setContext(AppContext"));
        assertTrue(guestController.contains("showExplore();"));
        assertTrue(hostController.contains("void setContext(AppContext"));
        assertTrue(hostController.contains("showListings();"));
    }

    @Test
    void hostPagesCarryTheirOwnPageTitlesLikeGuestPages() throws Exception {
        String listings = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml"));
        String form = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml"));
        String detail = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.fxml"));
        String wallet = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/host/wallet/host-wallet-dashboard.fxml"));

        assertTrue(listings.contains("styleClass=\"page-title\""));
        assertTrue(form.contains("styleClass=\"page-title\""));
        assertTrue(detail.contains("styleClass=\"page-title\""));
        assertTrue(wallet.contains("styleClass=\"page-title\""));
    }

    private static void assertControllerMethods(String file, String... methods) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/snoozeshare/ui/" + file));
        for (String method : methods) {
            assertTrue(source.contains(method), file + " missing " + method);
        }
    }
}
