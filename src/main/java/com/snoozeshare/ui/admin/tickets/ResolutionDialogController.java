package com.snoozeshare.ui.admin.tickets;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.service.DisputeDetail;
import com.snoozeshare.service.requests.ResolutionRequest;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class ResolutionDialogController {

    @FXML private Label subtitleLabel;
    @FXML private Label remedyLabel;
    @FXML private HBox modeChips;
    @FXML private ToggleButton fullRefundChip;
    @FXML private ToggleButton fullPayoutChip;
    @FXML private ToggleButton customChip;
    @FXML private VBox amountBox;
    @FXML private TextField amountField;
    @FXML private Label previewLabel;
    @FXML private TextArea reasonArea;
    @FXML private Label errorLabel;

    private DisputeDetail detail;
    private ResolutionMode mode;

    /** Shows the dialog modally; empty when the agent cancels. */
    public static Optional<ResolutionRequest> show(DisputeDetail detail, ResolutionMode mode) {
        try {
            FXMLLoader loader = new FXMLLoader(ResolutionDialogController.class.getResource(
                    "/com/snoozeshare/ui/admin/tickets/resolution-dialog.fxml"));
            Node content = loader.load();
            ResolutionDialogController controller = loader.getController();
            controller.configure(detail, mode);

            ButtonType confirm = new ButtonType(controller.confirmLabel(), ButtonBar.ButtonData.OK_DONE);
            Dialog<ResolutionRequest> dialog = new Dialog<>();
            dialog.setTitle(controller.title());
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, confirm);
            dialog.getDialogPane().getStylesheets().add(ResolutionDialogController.class.getResource(
                    "/com/snoozeshare/ui/common/theme.css").toExternalForm());
            Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirm);
            confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.validate()) {
                    event.consume();
                }
            });
            dialog.setResultConverter(button -> button == confirm ? controller.buildRequest() : null);
            return dialog.showAndWait();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load resolution dialog", exception);
        }
    }

    private void configure(DisputeDetail disputeDetail, ResolutionMode resolutionMode) {
        detail = disputeDetail;
        mode = resolutionMode;
        String target = detail.ticket().raisedByRole() == Role.HOST ? "host" : "guest";
        subtitleLabel.setText(detail.ticketLabel() + " · " + detail.listingTitle() + " · "
                + detail.guestName() + " vs " + detail.hostName());
        remedyLabel.setText(switch (mode) {
            case ACCEPT -> "Requested remedy: " + detail.ticket().requestedRemedy() + " (" + target + ")";
            case REJECT -> "The requested remedy will not be issued; the host is paid in full.";
            case MANUAL -> "Settle the held escrow of SGD " + detail.escrowAmount().toPlainString();
        });

        ToggleGroup group = new ToggleGroup();
        fullRefundChip.setToggleGroup(group);
        fullPayoutChip.setToggleGroup(group);
        customChip.setToggleGroup(group);
        fullRefundChip.setSelected(true);
        group.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true);
                return;
            }
            updateAmountVisibility();
            updatePreview();
        });
        amountField.textProperty().addListener((observable, previous, text) -> updatePreview());

        boolean manual = mode == ResolutionMode.MANUAL;
        modeChips.setVisible(manual);
        modeChips.setManaged(manual);
        updateAmountVisibility();
        updatePreview();
    }

    private boolean amountIsEntered() {
        return switch (mode) {
            case REJECT -> false;
            case MANUAL -> customChip.isSelected();
            case ACCEPT -> detail.ticket().requestedRemedy() == RemedyType.PARTIAL_REFUND
                    || detail.ticket().requestedRemedy() == RemedyType.OTHER;
        };
    }

    private void updateAmountVisibility() {
        boolean entered = amountIsEntered();
        amountBox.setVisible(entered);
        amountBox.setManaged(entered);
    }

    private String refundText() {
        if (amountIsEntered()) {
            return amountField.getText();
        }
        return switch (mode) {
            case REJECT -> "0";
            case MANUAL -> fullRefundChip.isSelected() ? detail.escrowAmount().toPlainString() : "0";
            case ACCEPT -> detail.ticket().requestedRemedy() == RemedyType.FULL_REFUND
                    ? detail.escrowAmount().toPlainString() : "0";
        };
    }

    private void updatePreview() {
        ResolutionPreview.Result result = ResolutionPreview.compute(detail.escrowAmount(), refundText());
        previewLabel.setText(result.valid() ? result.summary() : result.message());
    }

    private boolean validate() {
        ResolutionPreview.Result result = ResolutionPreview.compute(detail.escrowAmount(), refundText());
        if (!result.valid()) {
            errorLabel.setText(result.message());
            return false;
        }
        if (reasonArea.getText() == null || reasonArea.getText().isBlank()) {
            errorLabel.setText("A reason is required");
            return false;
        }
        errorLabel.setText("");
        return true;
    }

    private ResolutionRequest buildRequest() {
        BigDecimal refund = ResolutionPreview.compute(detail.escrowAmount(), refundText()).refund();
        return new ResolutionRequest(mode, refund, reasonArea.getText().trim());
    }

    private String title() {
        return switch (mode) {
            case ACCEPT -> "Accept dispute";
            case REJECT -> "Reject dispute";
            case MANUAL -> "Manual settlement";
        };
    }

    private String confirmLabel() {
        return switch (mode) {
            case ACCEPT -> "Confirm accept";
            case REJECT -> "Confirm reject";
            case MANUAL -> "Apply settlement";
        };
    }
}
