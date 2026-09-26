package com.snoozeshare.ui.host;

import java.io.IOException;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.ui.common.NavShellController;
import com.snoozeshare.ui.common.wallet.WalletDashboardController;
import com.snoozeshare.ui.host.bookings.HostBookingsController;
import com.snoozeshare.ui.host.calendar.HostCalendarController;
import com.snoozeshare.ui.host.listings.HostListingDetailController;
import com.snoozeshare.ui.host.listings.HostListingFormController;
import com.snoozeshare.ui.host.listings.HostListingsController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

public final class HostShellController extends NavShellController {

    private static final String ACTIVE_TAB = "agent-tab-active";

    @FXML
    private BorderPane shellRoot;
    @FXML private Label listingsTab;
    @FXML private Label bookingsTab;
    @FXML private Label walletTab;

    private WalletDashboardController walletController;

    @Override
    public void setContext(AppContext appContext) {
        super.setContext(appContext);
        showListings();
    }

    @FXML
    private void showListings() {
        selectTab(listingsTab);
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
            controller.setOnOpenCalendar(this::showCalendar);
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

    private void showCalendar(Property property) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/host/calendar/host-calendar.fxml"));
            Node calendarView = loader.load();
            HostCalendarController controller = loader.getController();
            controller.setContext(getContext());
            controller.setProperty(property);
            controller.setOnBack(this::showListings);
            shellRoot.setCenter(calendarView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load host booking calendar", exception);
        }
    }

    @FXML
    private void showBookings() {
        selectTab(bookingsTab);
        cleanupWalletController();
        getContext().runBookingCompletionSweep();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/host/bookings/host-bookings.fxml"));
            Node bookingsView = loader.load();
            HostBookingsController controller = loader.getController();
            controller.setContext(getContext());
            shellRoot.setCenter(bookingsView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load host bookings view", exception);
        }
    }

    @FXML
    private void showWallet() {
        selectTab(walletTab);
        cleanupWalletController();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml"));
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

    private void selectTab(Label selected) {
        for (Label tab : new Label[] {listingsTab, bookingsTab, walletTab}) {
            tab.getStyleClass().remove(ACTIVE_TAB);
        }
        selected.getStyleClass().add(ACTIVE_TAB);
    }
}
