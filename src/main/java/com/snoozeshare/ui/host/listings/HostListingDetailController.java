package com.snoozeshare.ui.host.listings;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.service.ListingMetrics;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

public final class HostListingDetailController {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a");

    @FXML private Label titleLabel;
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
    @FXML private Label ratingLabel;
    @FXML private FlowPane amenitiesPane;

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
        titleLabel.setText(property.title());
        typeLabel.setText(property.propertyType().name().replace('_', ' '));
        addressLabel.setText(String.join(", ", property.streetAddress(), property.city(),
                property.region(), property.postalCode()));
        descriptionLabel.setText(property.description());
        capacityLabel.setText(Integer.toString(property.maxGuests()));
        bedroomsLabel.setText(Integer.toString(property.bedrooms()));
        bathroomsLabel.setText(formatNumber(property.bathrooms()));
        checkInLabel.setText(property.checkInTime().format(TIME_FORMAT));
        checkOutLabel.setText(property.checkOutTime().format(TIME_FORMAT));
        rateLabel.setText("$" + property.baseNightlyRate().toPlainString());
        statusLabel.setText(property.status().name());

        amenitiesPane.getChildren().clear();
        for (AmenityType amenity : property.amenities()) {
            Label chip = new Label(amenity.name().replace('_', ' '));
            chip.getStyleClass().add("amenity-chip");
            amenitiesPane.getChildren().add(chip);
        }
    }

    public void setMetrics(ListingMetrics metrics) {
        bookingCountLabel.setText(metrics == null ? "—" : Integer.toString(metrics.bookingCount()));
        ratingLabel.setText(metrics == null ? "—" : String.format("%.1f ★", metrics.averageRating()));
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
