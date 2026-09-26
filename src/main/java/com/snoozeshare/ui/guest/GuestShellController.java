package com.snoozeshare.ui.guest;

import java.io.IOException;
import java.time.LocalDate;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.ui.common.NavShellController;
import com.snoozeshare.ui.common.wallet.WalletDashboardController;
import com.snoozeshare.ui.guest.listing.ListingDetailController;
import com.snoozeshare.ui.guest.search.GuestSearchController;
import com.snoozeshare.ui.guest.tickets.TicketHistoryController;
import com.snoozeshare.ui.guest.trips.TripDashboardController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

public final class GuestShellController extends NavShellController {

    private static final String ACTIVE_TAB = "agent-tab-active";

    @FXML
    private BorderPane shellRoot;
    @FXML private Label searchTab;
    @FXML private Label tripsTab;
    @FXML private Label walletTab;
    @FXML private Label supportTab;

    private GuestSearchController searchController;
    private TripDashboardController tripController;
    private WalletDashboardController walletController;
    private TicketHistoryController ticketController;
    private Node defaultCenter;

    @FXML
    private void initialize() {
        defaultCenter = shellRoot.getCenter();
    }

    @Override
    public void setContext(AppContext appContext) {
        super.setContext(appContext);
        showExplore();
    }

    @FXML
    private void showExplore() {
        selectTab(searchTab);
        cleanupTripController();
        cleanupWalletController();
        cleanupTicketController();
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
        selectTab(tripsTab);
        cleanupWalletController();
        cleanupTripController();
        cleanupTicketController();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/trips/trip-dashboard.fxml"));
            Node tripsView = loader.load();
            tripController = loader.getController();
            tripController.setContext(getContext());
            shellRoot.setCenter(tripsView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load trip dashboard", exception);
        }
    }

    private void cleanupTripController() {
        if (tripController != null) {
            tripController.cleanup();
            tripController = null;
        }
    }

    @FXML
    private void showWallet() {
        selectTab(walletTab);
        cleanupWalletController();
        cleanupTripController();
        cleanupTicketController();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml"));
            Node walletView = loader.load();
            walletController = loader.getController();
            walletController.setContext(getContext());
            shellRoot.setCenter(walletView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load wallet dashboard", exception);
        }
    }

    private void cleanupWalletController() {
        if (walletController != null) {
            walletController.cleanup();
            walletController = null;
        }
    }

    @FXML
    private void showSupport() {
        selectTab(supportTab);
        cleanupTripController();
        cleanupWalletController();
        cleanupTicketController();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/tickets/ticket-history.fxml"));
            Node supportView = loader.load();
            ticketController = loader.getController();
            ticketController.setContext(getContext());
            shellRoot.setCenter(supportView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load ticket history", exception);
        }
    }

    private void cleanupTicketController() {
        if (ticketController != null) {
            ticketController.cleanup();
            ticketController = null;
        }
    }

    private void selectTab(Label selected) {
        for (Label tab : new Label[] {searchTab, tripsTab, walletTab, supportTab}) {
            tab.getStyleClass().remove(ACTIVE_TAB);
        }
        selected.getStyleClass().add(ACTIVE_TAB);
    }
}
