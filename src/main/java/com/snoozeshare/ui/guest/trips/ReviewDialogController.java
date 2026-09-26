package com.snoozeshare.ui.guest.trips;

import java.util.UUID;

import com.snoozeshare.app.AppContext;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

public final class ReviewDialogController {

    @FXML private HBox starBar;
    @FXML private TextArea commentArea;
    @FXML private Label statusLabel;

    private AppContext context;
    private UUID bookingId;
    private Runnable onClose;
    private int selectedRating;

    public void configure(AppContext context, UUID bookingId, Runnable onClose) {
        this.context = context;
        this.bookingId = bookingId;
        this.onClose = onClose;
        this.selectedRating = 0;
        buildStarButtons();
    }

    private void buildStarButtons() {
        starBar.getChildren().clear();
        for (int i = 1; i <= 5; i++) {
            Button star = new Button("\u2605");
            star.getStyleClass().add("outline-button");
            star.setStyle("-fx-font-size: 20px; -fx-min-width: 40px;");
            final int rating = i;
            star.setOnAction(event -> selectRating(rating));
            starBar.getChildren().add(star);
        }
    }

    private void selectRating(int rating) {
        this.selectedRating = rating;
        for (int i = 0; i < starBar.getChildren().size(); i++) {
            Button star = (Button) starBar.getChildren().get(i);
            if (i < rating) {
                star.setStyle("-fx-font-size: 20px; -fx-min-width: 40px; -fx-text-fill: #d4a017;");
            } else {
                star.setStyle("-fx-font-size: 20px; -fx-min-width: 40px;");
            }
        }
    }

    @FXML
    private void handleSubmit() {
        if (selectedRating == 0) {
            showError("Please select a rating.");
            return;
        }

        String comment = commentArea.getText();
        if (comment != null && comment.isBlank()) {
            comment = null;
        }

        try {
            UUID guestId = context.session().currentUser().orElseThrow().userId();
            context.reviewService().submit(bookingId, guestId, selectedRating, comment);
            onClose.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        onClose.run();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }
}
