package com.snoozeshare.ui.host;

import com.snoozeshare.ui.common.NavShellController;

import javafx.fxml.FXML;

public final class HostShellController extends NavShellController {

    @FXML
    private void showDashboard() {
        displayPage("Dashboard", "Review your hosting activity and open tasks.");
    }

    @FXML
    private void showListings() {
        displayPage("Listings", "Manage your properties and listing status.");
    }

    @FXML
    private void showBookings() {
        displayPage("Bookings", "Review booking requests and upcoming stays.");
    }
}
