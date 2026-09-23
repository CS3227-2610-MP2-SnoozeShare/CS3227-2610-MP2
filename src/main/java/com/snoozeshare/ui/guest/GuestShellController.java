package com.snoozeshare.ui.guest;

import com.snoozeshare.ui.common.NavShellController;

import javafx.fxml.FXML;

public final class GuestShellController extends NavShellController {

    @FXML
    private void showExplore() {
        displayPage("Explore", "Browse available stays and discover your next trip.");
    }

    @FXML
    private void showMyTrips() {
        displayPage("My Trips", "Your upcoming and past trips will appear here.");
    }

    @FXML
    private void showSupport() {
        displayPage("Support", "Find help with bookings, payments, and stays.");
    }
}
