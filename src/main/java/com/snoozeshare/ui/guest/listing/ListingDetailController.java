package com.snoozeshare.ui.guest.listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.service.PriceBreakdown;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public final class ListingDetailController {

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
    @FXML private FlowPane amenitiesPane;
    @FXML private Label hostLabel;
    @FXML private VBox priceBox;
    @FXML private Label nightlyRateLabel;
    @FXML private Label nightsLabel;
    @FXML private Label totalLabel;
    @FXML private Button bookButton;
    @FXML private Label bookingStatusLabel;

    private AppContext context;
    private Runnable onClose;
    private Consumer<Booking> onBookingComplete;
    private Property property;
    private LocalDate checkIn;
    private LocalDate checkOut;

    public void setContext(AppContext context) {
        this.context = context;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOnBookingComplete(Consumer<Booking> onBookingComplete) {
        this.onBookingComplete = onBookingComplete;
    }

    public void populate(Property property, LocalDate checkIn, LocalDate checkOut) {
        this.property = property;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        bookButton.setDisable(checkIn == null || checkOut == null);
        titleLabel.setText(property.title());
        typeLabel.setText(property.propertyType().name().replace('_', ' '));
        addressLabel.setText(String.join(", ", property.streetAddress(),
                property.city(), property.region(), property.postalCode()));
        descriptionLabel.setText(property.description());
        capacityLabel.setText(property.maxGuests() + " guests");
        bedroomsLabel.setText(property.bedrooms() + " bedrooms");
        bathroomsLabel.setText(property.bathrooms() + " bathrooms");

        if (property.checkInTime() != null) {
            checkInLabel.setText("Check-in: " + property.checkInTime().format(TIME_FORMAT));
        }
        if (property.checkOutTime() != null) {
            checkOutLabel.setText("Check-out: " + property.checkOutTime().format(TIME_FORMAT));
        }

        amenitiesPane.getChildren().clear();
        for (AmenityType amenity : property.amenities()) {
            Label chip = new Label(amenity.name().replace('_', ' '));
            chip.getStyleClass().add("amenity-chip");
            amenitiesPane.getChildren().add(chip);
        }

        User host = findHost(property);
        hostLabel.setText(host != null ? host.displayName() : "Unknown host");

        if (checkIn != null && checkOut != null) {
            PriceBreakdown breakdown = context.listingService()
                    .estimateCost(property.propertyId(), checkIn, checkOut);
            BigDecimal rate = breakdown.nightlyRate().setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = breakdown.totalAmount().setScale(2, RoundingMode.HALF_UP);
            nightlyRateLabel.setText("SGD " + rate + " x " + breakdown.nights() + " nights");
            nightsLabel.setText("");
            totalLabel.setText("Total: SGD " + total);
        } else {
            BigDecimal rate = property.baseNightlyRate().setScale(2, RoundingMode.HALF_UP);
            nightlyRateLabel.setText("SGD " + rate + " / night");
            nightsLabel.setText("Select dates to see total price");
            totalLabel.setText("");
        }
        priceBox.setVisible(true);
        priceBox.setManaged(true);
    }

    private User findHost(Property property) {
        try {
            return context.userService().findById(property.hostId());
        } catch (Exception exception) {
            return null;
        }
    }

    @FXML
    private void handleBook() {
        try {
            User currentUser = context.session().currentUser().orElse(null);
            if (currentUser == null) {
                showBookingStatus("Please log in to book.", true);
                return;
            }
            Booking booking = context.bookingService().submitRequest(
                    currentUser.userId(), property.propertyId(), checkIn, checkOut);
            showBookingStatus("Booking submitted!", false);
            bookButton.setDisable(true);
            if (onBookingComplete != null) {
                onBookingComplete.accept(booking);
            }
        } catch (Exception exception) {
            showBookingStatus(exception.getMessage(), true);
        }
    }

    private void showBookingStatus(String message, boolean isError) {
        bookingStatusLabel.setText(message);
        bookingStatusLabel.getStyleClass().removeAll("error-message", "success-message");
        bookingStatusLabel.getStyleClass().add(isError ? "error-message" : "success-message");
        bookingStatusLabel.setVisible(true);
        bookingStatusLabel.setManaged(true);
    }

    @FXML
    private void handleClose() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
