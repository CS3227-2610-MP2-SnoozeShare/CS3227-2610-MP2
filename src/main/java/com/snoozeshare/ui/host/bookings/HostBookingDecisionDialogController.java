package com.snoozeshare.ui.host.bookings;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import com.snoozeshare.service.HostBookingRow;
import com.snoozeshare.ui.admin.AgentModal;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public final class HostBookingDecisionDialogController {

    @FXML private Label titleLabel;
    @FXML private Label listingLabel;
    @FXML private Label bookingLabel;
    @FXML private Label amountLabel;
    @FXML private Label amountCaptionLabel;
    @FXML private VBox summaryPane;
    @FXML private Label noticeLabel;
    @FXML private Label messageLabel;
    @FXML private TextArea messageArea;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private AgentModal modal;
    private boolean approving;
    private Consumer<String> onConfirm;

    public static void show(HostBookingRow row, boolean approving, Consumer<String> onConfirm) {
        try {
            FXMLLoader loader = new FXMLLoader(HostBookingDecisionDialogController.class
                    .getResource("host-booking-decision-dialog.fxml"));
            Parent card = loader.load();
            HostBookingDecisionDialogController controller = loader.getController();
            controller.configure(row, approving, onConfirm);
            controller.modal = AgentModal.create(card,
                    approving ? "Approve booking request?" : "Reject booking request?");
            controller.modal.showAndWait();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open booking decision dialog", exception);
        }
    }

    private void configure(HostBookingRow row, boolean approving, Consumer<String> onConfirm) {
        this.approving = approving;
        this.onConfirm = Objects.requireNonNull(onConfirm);
        if (!approving) {
            summaryPane.getStyleClass().add("booking-reject-summary");
        }
        titleLabel.setText(approving ? "Approve booking request?" : "Reject booking request?");
        listingLabel.setText(row.listingTitle());
        bookingLabel.setText(row.guestDisplayName() + " · " + row.booking().startDate()
                + "–" + row.booking().endDate() + " (" + row.nights() + " nights)");
        amountLabel.setText("$" + row.grossAmount().setScale(2).toPlainString());
        amountCaptionLabel.setText(approving ? "earning" : "to be refunded");
        String notice = approving
                ? "The booking moves to CONFIRMED and the guest is notified. Funds stay held in escrow until check-in."
                : "The guest will be notified and the held funds fully refunded (ESCROW_REFUND) — "
                        + "no fee is charged for a host rejection.";
        noticeLabel.setText(notice);
        messageLabel.setVisible(!approving);
        messageLabel.setManaged(!approving);
        messageArea.setVisible(!approving);
        messageArea.setManaged(!approving);
        confirmButton.setText(approving ? "Confirm approve" : "Confirm reject");
        confirmButton.getStyleClass().add(approving ? "booking-confirm-approve" : "booking-confirm-reject");
        confirmButton.setOnAction(event -> confirm());
        cancelButton.setOnAction(event -> modal.close());
    }

    private void confirm() {
        onConfirm.accept(approving ? null : messageArea.getText().trim());
        modal.close();
    }
}
