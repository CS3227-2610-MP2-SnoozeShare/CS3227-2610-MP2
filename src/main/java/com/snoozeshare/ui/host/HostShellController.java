package com.snoozeshare.ui.host;

import java.io.IOException;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.ui.common.NavShellController;
import com.snoozeshare.ui.guest.wallet.WalletDashboardController;
import com.snoozeshare.ui.host.listings.HostListingDetailController;
import com.snoozeshare.ui.host.listings.HostListingFormController;
import com.snoozeshare.ui.host.listings.HostListingsController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public final class HostShellController extends NavShellController {

    @FXML
    private BorderPane shellRoot;

    private WalletDashboardController walletController;

    @Override
    public void setContext(AppContext appContext) {
        super.setContext(appContext);
        showListings();
    }

    @FXML
    private void showListings() {
        cleanupWalletController();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/host/listings/host-listings.fxml"));
            Node listingsView = loader.load();
            HostListingsController controller = loader.getController();
            controller.setContext(getContext());
            controller.setOnCreateListing(this::showCreateListing);
            controller.setOnEditListing(this::showEditListing);
            controller.setOnViewListing(this::showListingDetail);
            shellRoot.setCenter(listingsView);
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

    private void showListingDetail(Property property) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/host/listings/host-listing-detail.fxml"));
            Node detailView = loader.load();
            HostListingDetailController controller = loader.getController();
            controller.setProperty(property);
            controller.setOnBack(this::showListings);
            shellRoot.setCenter(detailView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load host listing detail view", exception);
        }
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
            shellRoot.setCenter(formView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load listing form", exception);
        }
    }

    @FXML
    private void showBookings() {
        cleanupWalletController();
        try {
            Node bookingsView = FXMLLoader.load(getClass().getResource(
                    "/com/snoozeshare/ui/host/bookings/host-bookings.fxml"));
            shellRoot.setCenter(bookingsView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load host bookings view", exception);
        }
    }

    @FXML
    private void showWallet() {
        cleanupWalletController();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/host/wallet/host-wallet-dashboard.fxml"));
            Node walletView = loader.load();
            walletController = loader.getController();
            walletController.setContext(getContext());
            shellRoot.setCenter(walletView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load host wallet dashboard", exception);
        }
    }

    private void cleanupWalletController() {
        if (walletController != null) {
            walletController.cleanup();
            walletController = null;
        }
    }

}
