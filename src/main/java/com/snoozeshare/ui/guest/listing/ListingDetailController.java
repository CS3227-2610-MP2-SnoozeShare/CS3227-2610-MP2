package com.snoozeshare.ui.guest.listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.service.ListingMetrics;
import com.snoozeshare.service.ListingReview;
import com.snoozeshare.service.PriceBreakdown;
import com.snoozeshare.ui.guest.GuestVisuals;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class ListingDetailController {

    /** Reviews shown before "Show all" is pressed. */
    static final int COLLAPSED_REVIEW_COUNT = 2;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    @FXML private VBox modalRoot;
    @FXML private Label crumbTitleLabel;
    @FXML private Region banner;
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
    @FXML private Label hostAvatar;
    @FXML private Label hostLabel;
    @FXML private Label hostMetaLabel;
    @FXML private Label reviewsTitleLabel;
    @FXML private VBox reviewsBox;
    @FXML private Label reviewsToggle;
    @FXML private Label nightlyRateLabel;
    @FXML private DatePicker checkInPicker;
    @FXML private DatePicker checkOutPicker;
    @FXML private Label nightsLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label totalLabel;
    @FXML private Button bookButton;
    @FXML private Label bookingStatusLabel;

    private AppContext context;
    private Runnable onClose;
    private Consumer<Booking> onBookingComplete;
    private Property property;
    private List<ListingReview> reviews = List.of();
    private boolean reviewsExpanded;

    public void setContext(AppContext context) {
        this.context = context;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOnBookingComplete(Consumer<Booking> onBookingComplete) {
        this.onBookingComplete = onBookingComplete;
    }

    /** The modal's root, so the shell can size it to the space available. */
    public Region root() {
        return modalRoot;
    }

    @FXML
    private void initialize() {
        checkInPicker.setConverter(GuestVisuals.dateConverter());
        checkOutPicker.setConverter(GuestVisuals.dateConverter());
        GuestVisuals.blockPastDates(checkInPicker);
        GuestVisuals.blockPastDates(checkOutPicker);
        checkInPicker.valueProperty().addListener((obs, old, next) -> refreshPrice());
        checkOutPicker.valueProperty().addListener((obs, old, next) -> refreshPrice());
        reviewsToggle.setOnMouseClicked(event -> {
            reviewsExpanded = !reviewsExpanded;
            renderReviews();
        });
    }

    public void populate(Property property, LocalDate checkIn, LocalDate checkOut) {
        this.property = property;
        crumbTitleLabel.setText(property.title());
        titleLabel.setText(property.title());
        banner.getStyleClass().add(GuestVisuals.gradientClass(property.propertyId()));
        typeLabel.setText(property.propertyType().name().replace('_', ' '));
        addressLabel.setText(String.join(", ", property.streetAddress(),
                property.city(), property.region(), Integer.toString(property.postalCode())));
        descriptionLabel.setText(property.description());
        capacityLabel.setText(Integer.toString(property.maxGuests()));
        bedroomsLabel.setText(Integer.toString(property.bedrooms()));
        bathroomsLabel.setText(Integer.toString(property.bathrooms()));
        checkInLabel.setText(property.checkInTime() == null
                ? "—" : property.checkInTime().format(TIME_FORMAT));
        checkOutLabel.setText(property.checkOutTime() == null
                ? "—" : property.checkOutTime().format(TIME_FORMAT));

        amenitiesPane.getChildren().clear();
        for (AmenityType amenity : property.amenities()) {
            Label chip = new Label(amenityLabel(amenity));
            chip.getStyleClass().add("guest-amenity-pill");
            amenitiesPane.getChildren().add(chip);
        }

        User host = findHost(property);
        String hostName = host != null ? host.displayName() : "Unknown host";
        hostAvatar.setText(GuestVisuals.initials(hostName));
        hostLabel.setText("Hosted by " + hostName);
        if (host != null && host.createdAt() != null) {
            int since = host.createdAt().atZone(ZoneId.systemDefault()).getYear();
            hostMetaLabel.setText("Hosting since " + since);
        } else {
            hostMetaLabel.setVisible(false);
            hostMetaLabel.setManaged(false);
        }

        loadReviews();

        BigDecimal rate = property.baseNightlyRate().setScale(2, RoundingMode.HALF_UP);
        nightlyRateLabel.setText("SGD " + rate);
        checkInPicker.setValue(checkIn);
        checkOutPicker.setValue(checkOut);
        refreshPrice();
    }

    private static String amenityLabel(AmenityType amenity) {
        String lower = amenity.name().replace('_', ' ').toLowerCase(Locale.ENGLISH);
        StringBuilder text = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return text.toString();
    }

    private void loadReviews() {
        ListingMetrics metrics = context.listingMetricsService().metricsFor(property.propertyId());
        reviews = metrics.reviews();
        reviewsTitleLabel.setText(reviews.isEmpty() ? "REVIEWS"
                : String.format(Locale.ENGLISH, "REVIEWS · %.1f ★ (%d)",
                        metrics.averageRating(), reviews.size()));
        reviewsExpanded = false;
        renderReviews();
    }

    private void renderReviews() {
        reviewsBox.getChildren().clear();
        if (reviews.isEmpty()) {
            Label none = new Label("No reviews yet.");
            none.getStyleClass().add("guest-review-comment");
            reviewsBox.getChildren().add(none);
            reviewsToggle.setVisible(false);
            reviewsToggle.setManaged(false);
            return;
        }
        int shown = reviewsExpanded ? reviews.size() : Math.min(COLLAPSED_REVIEW_COUNT, reviews.size());
        for (ListingReview review : reviews.subList(0, shown)) {
            Label author = new Label(review.guestName() + "  " + "★".repeat(review.rating())
                    + "☆".repeat(Math.max(0, 5 - review.rating())));
            author.getStyleClass().add("guest-review-author");
            Label comment = new Label(review.comment() == null || review.comment().isBlank()
                    ? "No comment provided." : "\"" + review.comment() + "\"");
            comment.setWrapText(true);
            comment.getStyleClass().add("guest-review-comment");
            reviewsBox.getChildren().add(new VBox(2, author, comment));
        }
        boolean collapsible = reviews.size() > COLLAPSED_REVIEW_COUNT;
        reviewsToggle.setVisible(collapsible);
        reviewsToggle.setManaged(collapsible);
        reviewsToggle.setText(reviewsExpanded ? "Show fewer reviews"
                : "Show all " + reviews.size() + " reviews");
    }

    private void refreshPrice() {
        LocalDate checkIn = checkInPicker.getValue();
        LocalDate checkOut = checkOutPicker.getValue();
        bookingStatusLabel.setVisible(false);
        bookingStatusLabel.setManaged(false);
        boolean validRange = checkIn != null && checkOut != null && checkOut.isAfter(checkIn);
        bookButton.setDisable(!validRange);
        if (!validRange) {
            nightsLabel.setText(checkIn != null && checkOut != null
                    ? "Check-out must be after check-in" : "Select dates to see the total");
            subtotalLabel.setText("");
            totalLabel.setText("—");
            return;
        }
        try {
            PriceBreakdown breakdown = context.listingService()
                    .estimateCost(property.propertyId(), checkIn, checkOut);
            BigDecimal rate = breakdown.nightlyRate().setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = breakdown.totalAmount().setScale(2, RoundingMode.HALF_UP);
            nightsLabel.setText(breakdown.nights() + (breakdown.nights() == 1 ? " night" : " nights")
                    + " × SGD " + rate);
            subtotalLabel.setText("SGD " + total);
            totalLabel.setText("SGD " + total);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            bookButton.setDisable(true);
            nightsLabel.setText(exception.getMessage());
            subtotalLabel.setText("");
            totalLabel.setText("—");
        }
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
                    currentUser.userId(), property.propertyId(),
                    checkInPicker.getValue(), checkOutPicker.getValue());
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
