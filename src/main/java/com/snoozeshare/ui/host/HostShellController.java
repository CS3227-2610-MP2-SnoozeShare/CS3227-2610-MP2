package com.snoozeshare.ui.host;

import java.io.IOException;

import com.snoozeshare.domain.model.Property;
import com.snoozeshare.ui.common.NavShellController;
import com.snoozeshare.ui.host.listings.HostListingFormController;
import com.snoozeshare.ui.host.listings.HostListingsController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

public final class HostShellController extends NavShellController {

    @FXML
    private BorderPane shellRoot;

    @FXML
    private StackPane contentPane;

    @FXML
    private void showDashboard() {
        contentPane.getChildren().clear();
        displayPage("Dashboard", "Review your hosting activity and open tasks.");
    }

    @FXML
    private void showListings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/host/listings/host-listings.fxml"));
            Node listingsView = loader.load();
            HostListingsController controller = loader.getController();
            controller.setContext(getContext());
            controller.setOnCreateListing(this::showCreateListing);
            controller.setOnEditListing(this::showEditListing);
            displayPage("Listings", "Manage your properties and listing status.");
            contentPane.getChildren().setAll(listingsView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load host listings view", exception);
        }
    }

    private void showCreateListing() {
        showListingForm(null);
    }

    private void showEditListing(Property property) {
        showListingForm(property);
    }

    private void showListingForm(Property property) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/host/listings/host-listing-form.fxml"));
            Node formView = loader.load();
            HostListingFormController controller = loader.getController();
            controller.setContext(getContext());
            controller.setProperty(property);
            controller.setOnBack(this::showListings);
            controller.setOnSaved(this::showListings);
            displayPage(property == null ? "Create Listing" : "Edit Listing",
                    property == null ? "Publish a new property." : "Update your property details.");
            contentPane.getChildren().setAll(formView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load listing form", exception);
        }
    }

    @FXML
    private void showBookings() {
        contentPane.getChildren().clear();
        displayPage("Bookings", "Review booking requests and upcoming stays.");
    }
}
