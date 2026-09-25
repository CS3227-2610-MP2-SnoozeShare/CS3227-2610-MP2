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
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;

public final class ResolutionDialogController {

    private static final String THEME = "/com/snoozeshare/ui/admin/agent-theme.css";
    private static final String FXML = "/com/snoozeshare/ui/admin/tickets/resolution-dialog.fxml";

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private VBox bannerBox;
    @FXML private Label remedyLabel;
    @FXML private VBox modeBox;
    @FXML private HBox modeChips;
    @FXML private ToggleButton fullRefundChip;
    @FXML private ToggleButton fullPayoutChip;
    @FXML private ToggleButton customChip;
    @FXML private VBox amountBox;
    @FXML private TextField amountField;
    @FXML private Label previewLabel;
    @FXML private TextArea reasonArea;
    @FXML private Label errorLabel;
    @FXML private Button confirmButton;

    private DisputeDetail detail;
    private ResolutionMode mode;
    private Runnable cancelAction = () -> { };
    private Runnable confirmAction = () -> { };

    private record Loaded(Node card, ResolutionDialogController controller) {
    }

    /** Builds the dialog card without a window, for previews and snapshot tests. */
    public static Node createCard(DisputeDetail detail, ResolutionMode mode) {
        return load(detail, mode).card();
    }

    private static Loaded load(DisputeDetail detail, ResolutionMode mode) {
        try {
            FXMLLoader loader = new FXMLLoader(ResolutionDialogController.class.getResource(FXML));
            Node card = loader.load();
            ResolutionDialogController controller = loader.getController();
            controller.configure(detail, mode);
            return new Loaded(card, controller);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load resolution dialog", exception);
        }
    }

    /** Shows the dialog modally; empty when the agent cancels. */
    public static Optional<ResolutionRequest> show(DisputeDetail detail, ResolutionMode mode) {
        Loaded loaded = load(detail, mode);
        ResolutionDialogController controller = loaded.controller();

        ButtonType confirm = new ButtonType(controller.confirmLabel(), ButtonBar.ButtonData.OK_DONE);
        Dialog<ResolutionRequest> dialog = new Dialog<>();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle(controller.title());
        DialogPane pane = new DialogPane() {
            @Override
            protected Node createButtonBar() {
                Node bar = super.createButtonBar();
                bar.setVisible(false);
                bar.setManaged(false);
                return bar;
            }
        };
        dialog.setDialogPane(pane);
        pane.setContent(loaded.card());
        pane.getButtonTypes().addAll(ButtonType.CANCEL, confirm);
        pane.getStylesheets().add(ResolutionDialogController.class.getResource(THEME).toExternalForm());
        pane.getStyleClass().addAll("agent-root", "agent-dialog-pane");
        pane.sceneProperty().addListener((observable, previous, scene) -> {
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
            }
        });
        Button confirmNative = (Button) pane.lookupButton(confirm);
        Button cancelNative = (Button) pane.lookupButton(ButtonType.CANCEL);
        confirmNative.addEventFilter(ActionEvent.ACTION, event -> {
            if (!controller.validate()) {
                event.consume();
            }
        });
        controller.cancelAction = cancelNative::fire;
        controller.confirmAction = confirmNative::fire;
        dialog.setResultConverter(button -> button == confirm ? controller.buildRequest() : null);
        return dialog.showAndWait();
    }

    @FXML
    private void handleCancel() {
        cancelAction.run();
    }

    @FXML
    private void handleConfirm() {
        confirmAction.run();
    }

    private void configure(DisputeDetail disputeDetail, ResolutionMode resolutionMode) {
        detail = disputeDetail;
        mode = resolutionMode;
        errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        titleLabel.setText(title());
        subtitleLabel.setText("Ticket " + detail.ticketLabel() + " \u00b7 " + detail.listingTitle() + " \u00b7 "
                + shortName(detail.guestName()) + " vs " + shortName(detail.hostName()));
        boolean banner = mode != ResolutionMode.MANUAL;
        bannerBox.setVisible(banner);
        bannerBox.setManaged(banner);
        bannerBox.getStyleClass().removeAll("agent-banner-success", "agent-banner-danger");
        bannerBox.getStyleClass().add(mode == ResolutionMode.REJECT ? "agent-banner-danger" : "agent-banner-success");
        remedyLabel.setText(mode == ResolutionMode.REJECT ? remedyName() + " \u2014 will not be issued"
                : remedyName() + " " + remedyTarget());
        confirmButton.setText(confirmLabel());
        confirmButton.getStyleClass().add(switch (mode) {
            case ACCEPT -> "agent-confirm-success";
            case REJECT -> "agent-confirm-danger";
            case MANUAL -> "agent-confirm-accent";
        });
        reasonArea.setPromptText(switch (mode) {
            case ACCEPT -> "e.g. confirmed the issue, issuing the requested remedy per policy";
            case REJECT -> "e.g. no evidence of a policy violation found";
            case MANUAL -> "e.g. partial refund for confirmed noise disturbance";
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
        modeBox.setVisible(manual);
        modeBox.setManaged(manual);
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
        if (mode == ResolutionMode.ACCEPT && amountIsEntered() && result.refund().signum() <= 0) {
            errorLabel.setText("Accept needs a refund above zero; use Reject or Manual adjustment for zero");
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
            case ACCEPT -> "Accept \u2014 remedy "
                    + (detail.ticket().raisedByRole() == Role.HOST ? "host" : "guest");
            case REJECT -> "Reject dispute";
            case MANUAL -> "Manual wallet adjustment";
        };
    }

    private String remedyName() {
        return switch (detail.ticket().requestedRemedy()) {
            case FULL_REFUND -> "Full refund";
            case PARTIAL_REFUND -> "Partial refund";
            case HOST_PAYOUT -> "Full payout";
            case OTHER -> "Other remedy";
        };
    }

    private String remedyTarget() {
        boolean host = detail.ticket().raisedByRole() == Role.HOST;
        return (detail.ticket().requestedRemedy() == RemedyType.OTHER ? "for " : "to ")
                + shortName(host ? detail.hostName() : detail.guestName());
    }

    /** "Jane Gomez" becomes "Jane G." (canvas style). */
    private static String shortName(String fullName) {
        int space = fullName.lastIndexOf(' ');
        if (space < 0 || space == fullName.length() - 1) {
            return fullName;
        }
        return fullName.substring(0, fullName.indexOf(' ')) + " " + fullName.charAt(space + 1) + ".";
    }

    private String confirmLabel() {
        return switch (mode) {
            case ACCEPT -> "Confirm accept";
            case REJECT -> "Confirm reject";
            case MANUAL -> "Apply AGENT_OVERRIDE";
        };
    }
}
