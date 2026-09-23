package com.snoozeshare.ui.guest;

import java.io.IOException;

import com.snoozeshare.ui.common.NavShellController;
import com.snoozeshare.ui.guest.search.GuestSearchController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public final class GuestShellController extends NavShellController {

    @FXML
    private BorderPane shellRoot;

    @FXML
    private void showExplore() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/search/guest-search.fxml"));
            Node searchView = loader.load();
            GuestSearchController controller = loader.getController();
            controller.setContext(getContext());
            controller.setOnPropertySelected(property -> {
                // Detail modal — implemented in Task 6
            });
            shellRoot.setCenter(searchView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load search view", exception);
        }
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
