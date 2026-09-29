package com.snoozeshare.ui.guest.trips;

import java.math.RoundingMode;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.service.CancellationRefund;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/** Confirmation step before a guest cancels a booking; shows the refund the cancellation policy gives. */
public final class CancelBookingDialogController {

    /** What the dialog says about the booking: "Title · dates" plus a short booking number. */
    public record BookingSummary(String title, String dates, String reference) {
    }

    @FXML private Label summaryLabel;
    @FXML private Label refundCaption;
    @FXML private Label refundAmount;
    @FXML private Label statusLabel;
    @FXML private Button confirmButton;

    private AppContext context;
    private UUID bookingId;
    private Runnable onClose;
    private Runnable onCancelled;

    public void configure(AppContext context, UUID bookingId, BookingSummary summary,
                          Runnable onCancelled, Runnable onClose) {
        this.context = context;
        this.bookingId = bookingId;
        this.onCancelled = onCancelled;
        this.onClose = onClose;
        summaryLabel.setText(summary.title() + " · " + summary.dates() + " · Booking #"
                + summary.reference());
        CancellationRefund refund = context.bookingService().previewCancellationRefund(bookingId);
        refundCaption.setText(refund.percent() == 100
                ? "REFUND DUE — 100% (more than 48h before check-in)"
                : "REFUND DUE — 50% (within 48h of check-in)");
        refundAmount.setText("SGD " + refund.amount().setScale(2, RoundingMode.HALF_UP));
    }

    @FXML
    private void handleConfirm() {
        try {
            context.bookingService().cancel(bookingId,
                    context.session().currentUser().orElseThrow().userId());
            onCancelled.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            statusLabel.setText("Cancel failed: " + exception.getMessage());
            statusLabel.setVisible(true);
            statusLabel.setManaged(true);
        }
    }

    @FXML
    private void handleKeep() {
        onClose.run();
    }
}
