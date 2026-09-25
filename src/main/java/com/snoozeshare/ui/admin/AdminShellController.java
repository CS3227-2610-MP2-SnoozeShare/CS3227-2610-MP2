package com.snoozeshare.ui.admin;

import java.io.IOException;
import java.util.UUID;

import com.snoozeshare.ui.admin.categories.CategoryAdminController;
import com.snoozeshare.ui.admin.tickets.DisputeDetailController;
import com.snoozeshare.ui.admin.tickets.DisputeQueueController;
import com.snoozeshare.ui.common.NavShellController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public final class AdminShellController extends NavShellController {

    @FXML private BorderPane shellRoot;

    private Node defaultCenter;
    private DisputeQueueController queueController;

    @FXML
    private void initialize() {
        defaultCenter = shellRoot.getCenter();
    }

    @FXML
    private void showOperations() {
        restoreDefaultCenter();
        displayPage("Operations", "Monitor support operations and platform activity.");
    }

    @FXML
    private void showDisputes() {
        showQueue();
    }

    @FXML
    private void showAccounts() {
        restoreDefaultCenter();
        displayPage("Accounts", "Manage account status and support access.");
    }

    @FXML
    private void showCategories() {
        disposeQueue();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/admin/categories/category-admin.fxml"));
            Node view = loader.load();
            CategoryAdminController controller = loader.getController();
            controller.setContext(getContext());
            shellRoot.setCenter(view);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load category administration", exception);
        }
    }

    private void showQueue() {
        disposeQueue();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/admin/tickets/dispute-queue.fxml"));
            Node view = loader.load();
            queueController = loader.getController();
            queueController.setContext(getContext());
            queueController.setOnOpen(this::showDetail);
            shellRoot.setCenter(view);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load dispute queue", exception);
        }
    }

    private void showDetail(UUID ticketId) {
        disposeQueue();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
            Node view = loader.load();
            DisputeDetailController controller = loader.getController();
            controller.setContext(getContext());
            controller.setOnBack(this::showQueue);
            controller.load(ticketId);
            shellRoot.setCenter(view);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load dispute detail", exception);
        }
    }

    private void restoreDefaultCenter() {
        disposeQueue();
        shellRoot.setCenter(defaultCenter);
    }

    private void disposeQueue() {
        if (queueController != null) {
            queueController.dispose();
            queueController = null;
        }
    }
}
