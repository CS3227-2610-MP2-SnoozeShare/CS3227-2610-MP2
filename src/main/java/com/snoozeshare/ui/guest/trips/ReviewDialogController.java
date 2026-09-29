package com.snoozeshare.ui.guest.trips;

import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.ui.guest.GuestVisuals;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

public final class ReviewDialogController {

    /** What the dialog shows about the stay being reviewed. */
    public record StaySummary(UUID listingId, String title, String dates, String hostName) {
    }

    private static final int STAR_COUNT = 5;

    @FXML private Region stayThumb;
    @FXML private Label stayTitle;
    @FXML private Label stayMeta;
    @FXML private HBox starBar;
    @FXML private TextArea commentArea;
    @FXML private Label statusLabel;

    private AppContext context;
    private UUID bookingId;
    private Runnable onClose;
    private int selectedRating;

    public void configure(AppContext context, UUID bookingId, StaySummary stay, Runnable onClose) {
        this.context = context;
        this.bookingId = bookingId;
        this.onClose = onClose;
        this.selectedRating = 0;
        stayThumb.getStyleClass().add(GuestVisuals.gradientClass(stay.listingId()));
        stayTitle.setText(stay.title());
        stayMeta.setText(stay.dates() + " · Host: " + stay.hostName());
        buildStars();
    }

    int selectedRating() {
        return selectedRating;
    }

    private void buildStars() {
        starBar.getChildren().clear();
        for (int i = 1; i <= STAR_COUNT; i++) {
            Label star = new Label("★");
            star.getStyleClass().add("guest-star");
            final int rating = i;
            star.setOnMouseClicked(event -> selectRating(rating));
            starBar.getChildren().add(star);
        }
    }

    private void selectRating(int rating) {
        this.selectedRating = rating;
        for (int i = 0; i < starBar.getChildren().size(); i++) {
            var styles = starBar.getChildren().get(i).getStyleClass();
            styles.remove("guest-star-on");
            if (i < rating) {
                styles.add("guest-star-on");
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
