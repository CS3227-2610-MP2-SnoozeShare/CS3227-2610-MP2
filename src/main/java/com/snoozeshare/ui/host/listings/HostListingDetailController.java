package com.snoozeshare.ui.host.listings;

import java.time.format.DateTimeFormatter;

import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.model.Property;

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
    @FXML private FlowPane amenitiesPane;

    private Runnable onBack = () -> { };

    public void setOnBack(Runnable callback) {
        onBack = callback == null ? () -> { } : callback;
    }

    public void setProperty(Property property) {
        titleLabel.setText(property.title());
        typeLabel.setText(property.propertyType().name().replace('_', ' '));
        addressLabel.setText(String.join(", ", property.streetAddress(), property.city(),
                property.region(), property.postalCode()));
        descriptionLabel.setText(property.description());
        capacityLabel.setText(property.maxGuests() + " guests");
        bedroomsLabel.setText(property.bedrooms() + " bedrooms");
        bathroomsLabel.setText(property.bathrooms() + " bathrooms");
        checkInLabel.setText("Check-in: " + property.checkInTime().format(TIME_FORMAT));
        checkOutLabel.setText("Check-out: " + property.checkOutTime().format(TIME_FORMAT));
        rateLabel.setText("SGD " + property.baseNightlyRate().toPlainString() + " / night");
        statusLabel.setText(property.status().name());

        amenitiesPane.getChildren().clear();
        for (AmenityType amenity : property.amenities()) {
            Label chip = new Label(amenity.name().replace('_', ' '));
            chip.getStyleClass().add("amenity-chip");
            amenitiesPane.getChildren().add(chip);
        }
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }
}
