package com.snoozeshare.ui.admin;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.ui.admin.categories.CategoryAdminController;
import com.snoozeshare.ui.admin.tickets.DisputeDetailController;
import com.snoozeshare.ui.admin.tickets.DisputeQueueController;
import com.snoozeshare.ui.common.NavShellController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

public final class AdminShellController extends NavShellController {

    private static final String WORKSPACE_LABEL = "Support Agent workspace";
    private static final String ACTIVE_TAB = "agent-tab-active";

    @FXML private BorderPane shellRoot;
    @FXML private Label avatarLabel;
    @FXML private Label disputesTab;
    @FXML private Label accountsTab;
    @FXML private Label auditTab;
    @FXML private Label categoriesTab;

    private Node defaultCenter;
    private DisputeQueueController queueController;

    @FXML
    private void initialize() {
        defaultCenter = shellRoot.getCenter();
    }

    @Override
    public void setContext(AppContext appContext) {
        super.setContext(appContext);
        roleLabel.setText(WORKSPACE_LABEL);
        avatarLabel.setText(initials(appContext.session().currentUser().map(user -> user.displayName())
                .orElse(null)));
        showDisputes();
    }

    /** Two-letter avatar text from a display name ("Dana Kim" gives "DK"); "SA" when there is no name. */
    static String initials(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "SA";
        }
        String[] words = displayName.trim().split("\\s+");
        String initials = words.length == 1
                ? words[0].substring(0, Math.min(2, words[0].length()))
                : words[0].substring(0, 1) + words[words.length - 1].substring(0, 1);
        return initials.toUpperCase(Locale.ROOT);
    }

    /** The default landing view: the dispute queue. */
    @FXML
    private void showOperations() {
        showDisputes();
    }

    @FXML
    private void showDisputes() {
        selectTab(disputesTab);
        showQueue();
    }

    @FXML
    private void showAccounts() {
        selectTab(accountsTab);
        restoreDefaultCenter();
        displayPage("Accounts", "Account governance is coming soon.");
    }

    @FXML
    private void showAuditLog() {
        selectTab(auditTab);
        restoreDefaultCenter();
        displayPage("Audit log", "The platform audit trail is coming soon.");
    }

    @FXML
    private void showCategories() {
        selectTab(categoriesTab);
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

    private void selectTab(Label selected) {
        for (Label tab : new Label[] {disputesTab, accountsTab, auditTab, categoriesTab}) {
            tab.getStyleClass().remove(ACTIVE_TAB);
        }
        selected.getStyleClass().add(ACTIVE_TAB);
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
