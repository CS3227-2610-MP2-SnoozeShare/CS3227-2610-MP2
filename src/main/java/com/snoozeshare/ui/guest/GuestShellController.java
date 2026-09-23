package com.snoozeshare.ui.guest;

import java.io.IOException;
import java.time.LocalDate;

import com.snoozeshare.domain.model.Property;
import com.snoozeshare.ui.common.NavShellController;
import com.snoozeshare.ui.guest.listing.ListingDetailController;
import com.snoozeshare.ui.guest.search.GuestSearchController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

public final class GuestShellController extends NavShellController {

    @FXML
    private BorderPane shellRoot;

    private GuestSearchController searchController;

    @FXML
    private void showExplore() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/search/guest-search.fxml"));
            Node searchView = loader.load();
            searchController = loader.getController();
            searchController.setContext(getContext());
            searchController.setOnPropertySelected(property -> {
                showDetailModal(property,
                        searchController.getCheckIn(), searchController.getCheckOut());
            });
            shellRoot.setCenter(searchView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load search view", exception);
        }
    }

    private void showDetailModal(Property property, LocalDate checkIn, LocalDate checkOut) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/listing/listing-detail.fxml"));
            Node detailView = loader.load();
            ListingDetailController controller = loader.getController();
            controller.setContext(getContext());

            StackPane overlay = new StackPane();
            overlay.getStyleClass().add("modal-overlay");
            overlay.getChildren().add(detailView);
            StackPane.setAlignment(detailView, Pos.CENTER);

            Node currentCenter = shellRoot.getCenter();
            StackPane root = new StackPane();
            root.getChildren().addAll(currentCenter, overlay);
            shellRoot.setCenter(root);

            controller.setOnClose(() -> {
                shellRoot.setCenter(currentCenter);
            });
            controller.populate(property, checkIn, checkOut);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load listing detail", exception);
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
