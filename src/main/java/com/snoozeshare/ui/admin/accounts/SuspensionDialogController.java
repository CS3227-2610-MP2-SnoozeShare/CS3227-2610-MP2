package com.snoozeshare.ui.admin.accounts;

import java.io.IOException;
import java.util.function.Consumer;

import com.snoozeshare.service.AccountSummary;
import com.snoozeshare.ui.admin.AgentModal;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The Suspend / Reactivate modal card (C34, C35). The service call is passed in as a callback that may throw; its
 * message is shown inline in the card (which stays open) instead of closing it.
 */
public final class SuspensionDialogController {

    /** Which action the card confirms; it decides the title, banner colour, note and button text. */
    public enum Mode { SUSPEND, REACTIVATE }

    private static final String FXML = "/com/snoozeshare/ui/admin/accounts/suspension-dialog.fxml";

    @FXML private Label titleLabel;
    @FXML private VBox bannerBox;
    @FXML private Label nameLabel;
    @FXML private Label emailLabel;
    @FXML private Label metaLabel;
    @FXML private Label noteLabel;
    @FXML private TextArea reasonArea;
    @FXML private Label errorLabel;
    @FXML private Button confirmButton;

    private Consumer<String> confirmAction = reason -> { };
    private Runnable closeAction = () -> { };
    private Runnable resizeAction = () -> { };

    /** Builds the modal (not yet shown); {@code onConfirm} receives the trimmed reason. */
    public static Stage createDialog(Mode mode, AccountSummary account, Consumer<String> onConfirm) {
        try {
            FXMLLoader loader = new FXMLLoader(SuspensionDialogController.class.getResource(FXML));
            Parent card = loader.load();
            SuspensionDialogController controller = loader.getController();
            controller.configure(mode, account, onConfirm);
            AgentModal modal = AgentModal.create(card, controller.titleLabel.getText());
            controller.closeAction = modal::close;
            controller.resizeAction = modal::refit;
            return modal.stage();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the account dialog", exception);
        }
    }

    public static void show(Mode mode, AccountSummary account, Consumer<String> onConfirm) {
        createDialog(mode, account, onConfirm).showAndWait();
    }

    private void configure(Mode mode, AccountSummary account, Consumer<String> onConfirm) {
        confirmAction = onConfirm;
        boolean suspend = mode == Mode.SUSPEND;
        errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.textProperty().addListener((observable, previous, text) -> resizeAction.run());
        titleLabel.setText(suspend ? "Suspend account" : "Reactivate account");
        bannerBox.getStyleClass().add(suspend ? "agent-banner-danger" : "agent-banner-success");
        nameLabel.setText(account.displayName());
        emailLabel.setText(account.email());
        metaLabel.setText(AccountText.role(account.role()) + " · joined "
                + AccountText.joined(account.createdAt()));
        noteLabel.setText(suspend
                ? "The user cannot log in or book until reactivated. Their pending requests are cancelled with a "
                        + "full refund, upcoming confirmed stays are force-cancelled with a full refund, and a "
                        + "host's active listings are deactivated."
                : "The user can log in and use the app again. Cancelled bookings and deactivated listings are "
                        + "not restored; a host re-activates their listings.");
        reasonArea.setPromptText(suspend
                ? "e.g. repeated late cancellations flagged across 3 bookings"
                : "e.g. appeal accepted after review");
        confirmButton.setText(suspend ? "Confirm suspend" : "Confirm reactivate");
        confirmButton.getStyleClass().add(suspend ? "agent-confirm-danger" : "agent-confirm-success");
        confirmButton.disableProperty().bind(reasonArea.textProperty().map(text -> text == null || text.isBlank()));
    }

    @FXML
    private void handleCancel() {
        closeAction.run();
    }

    @FXML
    private void handleConfirm() {
        String reason = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
        if (reason.isEmpty()) {
            return;
        }
        try {
            confirmAction.accept(reason);
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
            return;
        }
        closeAction.run();
    }
}
