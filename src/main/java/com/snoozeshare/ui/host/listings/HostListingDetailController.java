package com.snoozeshare.ui.host.listings;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.service.ListingMetrics;
import com.snoozeshare.service.ListingReview;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public final class HostListingDetailController {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a");

    @FXML private Label titleLabel;
    @FXML private Label titleCrumb;
    @FXML private Label typeLabel;
    @FXML private Label addressLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label capacityLabel;
    @FXML private Label bedroomsLabel;
    @FXML private Label bathroomsLabel;
    @FXML private Label checkInLabel;
    @FXML private Label checkOutLabel;
    @FXML private Label rateLabel;
    @FXML private Label statusLabel;
    @FXML private Label bookingCountLabel;
    @FXML private Label reviewsTitleLabel;
    @FXML private Label occupancyLabel;
    @FXML private Label earningsLabel;
    @FXML private FlowPane amenitiesPane;
    @FXML private VBox reviewsList;

    private Runnable onBack = () -> { };
    private Consumer<Property> onEdit = property -> { };
    private Consumer<Property> onOpenCalendar = property -> { };
    private Property property;

    public void setOnBack(Runnable callback) {
        onBack = callback == null ? () -> { } : callback;
    }

    public void setOnEdit(Consumer<Property> callback) {
        onEdit = callback == null ? property -> { } : callback;
    }

    public void setOnOpenCalendar(Consumer<Property> callback) {
        onOpenCalendar = callback == null ? property -> { } : callback;
    }

    public void setProperty(Property property) {
        this.property = property;
        titleCrumb.setText(property.title());
        titleLabel.setText(property.title());
        typeLabel.setText(property.propertyType().name().replace('_', ' '));
        addressLabel.setText(String.join(", ", property.streetAddress(), property.city(),
                property.region(), Integer.toString(property.postalCode())));
        descriptionLabel.setText(property.description());
        capacityLabel.setText(Integer.toString(property.maxGuests()));
        bedroomsLabel.setText(Integer.toString(property.bedrooms()));
        bathroomsLabel.setText(formatNumber(property.bathrooms()));
        checkInLabel.setText(property.checkInTime().format(TIME_FORMAT));
        checkOutLabel.setText(property.checkOutTime().format(TIME_FORMAT));
        rateLabel.setText("$" + property.baseNightlyRate().toPlainString());
        statusLabel.setText(property.status().name());
        statusLabel.getStyleClass().removeAll("listing-detail-status-active",
                "listing-detail-status-inactive");
        statusLabel.getStyleClass().add(property.status() == ListingStatus.ACTIVE
                ? "listing-detail-status-active" : "listing-detail-status-inactive");

        amenitiesPane.getChildren().clear();
        for (AmenityType amenity : property.amenities()) {
            Label chip = new Label(amenity.name().replace('_', ' '));
            chip.getStyleClass().add("amenity-chip");
            amenitiesPane.getChildren().add(chip);
        }
    }

    public void setMetrics(ListingMetrics metrics) {
        if (metrics == null) {
            bookingCountLabel.setText("—");
            occupancyLabel.setText("—");
            earningsLabel.setText("—");
            reviewsTitleLabel.setText("REVIEWS");
            renderReviews(java.util.List.of());
            return;
        }
        bookingCountLabel.setText(Integer.toString(metrics.bookingCount()));
        occupancyLabel.setText(String.format("%.0f%%", metrics.occupancyPercentage()));
        earningsLabel.setText("$" + metrics.earnings().setScale(2, RoundingMode.HALF_UP));
        reviewsTitleLabel.setText(metrics.reviews().isEmpty()
                ? "REVIEWS" : String.format("REVIEWS · %.1f ★ (%d)", metrics.averageRating(),
                        metrics.reviews().size()));
        renderReviews(metrics.reviews());
    }

    private void renderReviews(java.util.List<ListingReview> reviews) {
        reviewsList.getChildren().clear();
        if (reviews.isEmpty()) {
            reviewsList.getChildren().add(new Label("No reviews yet."));
            reviewsList.getChildren().get(0).getStyleClass().add("listing-detail-review");
            return;
        }
        for (ListingReview review : reviews) {
            VBox row = new VBox(4);
            row.getStyleClass().add("listing-detail-review-row");
            Label author = new Label(review.guestName() + " — " + "★".repeat(review.rating()));
            author.getStyleClass().add("listing-detail-review-author");
            Label comment = new Label(review.comment() == null || review.comment().isBlank()
                    ? "No comment provided." : "\"" + review.comment() + "\"");
            comment.getStyleClass().add("listing-detail-review-comment");
            row.getChildren().addAll(author, comment);
            reviewsList.getChildren().add(row);
        }
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }

    @FXML
    private void handleEdit() {
        onEdit.accept(property);
    }

    @FXML
    private void handleOpenCalendar() {
        onOpenCalendar.accept(property);
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value)
                ? Integer.toString((int) value) : Double.toString(value);
    }
}
