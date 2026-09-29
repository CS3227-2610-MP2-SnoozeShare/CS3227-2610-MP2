package com.snoozeshare.ui.guest;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.ui.common.NavShellController;
import com.snoozeshare.ui.common.wallet.WalletDashboardController;
import com.snoozeshare.ui.guest.listing.ListingDetailController;
import com.snoozeshare.ui.guest.messaging.GuestMessagesController;
import com.snoozeshare.ui.guest.search.GuestSearchController;
import com.snoozeshare.ui.guest.trips.TripDashboardController;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public final class GuestShellController extends NavShellController {

    private static final String ACTIVE_TAB = "agent-tab-active";
    private static final double MODAL_MAX_WIDTH = 960;
    private static final double MODAL_MAX_HEIGHT = 840;
    private static final double MODAL_MARGIN = 48;

    @FXML
    private BorderPane shellRoot;
    @FXML private Label searchTab;
    @FXML private Label tripsTab;
    @FXML private Label messagesTab;
    @FXML private Label walletTab;

    private GuestSearchController searchController;
    private TripDashboardController tripController;
    private GuestMessagesController messagesController;
    private WalletDashboardController walletController;
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
        cleanupMessagesController();
        cleanupWalletController();
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
            Region modal = controller.root();
            modal.maxWidthProperty().bind(Bindings.min(MODAL_MAX_WIDTH,
                    overlay.widthProperty().subtract(MODAL_MARGIN)));
            modal.maxHeightProperty().bind(Bindings.min(MODAL_MAX_HEIGHT,
                    overlay.heightProperty().subtract(MODAL_MARGIN)));

            Node currentCenter = shellRoot.getCenter();
            StackPane root = new StackPane();
            root.getChildren().addAll(currentCenter, overlay);
            shellRoot.setCenter(root);

            controller.setOnClose(() -> {
                shellRoot.setCenter(currentCenter);
            });
            overlay.setOnMouseClicked(event -> {
                if (event.getTarget() == overlay) {
                    shellRoot.setCenter(currentCenter);
                }
            });
            controller.populate(property, checkIn, checkOut);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load listing detail", exception);
        }
    }

    @FXML
    private void showMyTrips() {
        selectTab(tripsTab);
        cleanupMessagesController();
        cleanupWalletController();
        cleanupTripController();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/trips/trip-dashboard.fxml"));
            Node tripsView = loader.load();
            tripController = loader.getController();
            tripController.setOnMessageHost(this::showMessagesFor);
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

    private void showMessagesFor(UUID bookingId) {
        showMessages();
        messagesController.select(bookingId);
    }

    @FXML
    private void showMessages() {
        selectTab(messagesTab);
        cleanupTripController();
        cleanupWalletController();
        cleanupMessagesController();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/messaging/guest-messages.fxml"));
            Node messagesView = loader.load();
            messagesController = loader.getController();
            messagesController.setContext(getContext());
            shellRoot.setCenter(messagesView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load messages view", exception);
        }
    }

    private void cleanupMessagesController() {
        if (messagesController != null) {
            messagesController.cleanup();
            messagesController = null;
        }
    }

    @FXML
    private void showWallet() {
        selectTab(walletTab);
        cleanupWalletController();
        cleanupTripController();
        cleanupMessagesController();
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

    private void selectTab(Label selected) {
        for (Label tab : new Label[] {searchTab, tripsTab, messagesTab, walletTab}) {
            tab.getStyleClass().remove(ACTIVE_TAB);
        }
        selected.getStyleClass().add(ACTIVE_TAB);
    }
}
