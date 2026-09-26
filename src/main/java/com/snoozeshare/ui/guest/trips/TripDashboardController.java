package com.snoozeshare.ui.guest.trips;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.BookingCancelledEvent;
import com.snoozeshare.infra.events.events.BookingConfirmedEvent;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class TripDashboardController {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");

    private static final Set<BookingStatus> CANCELLED_STATUSES = Set.of(
            BookingStatus.CANCELLED_BY_GUEST, BookingStatus.CANCELLED_BY_HOST,
            BookingStatus.REJECTED, BookingStatus.FORCE_CANCELLED);

    @FXML private HBox tabBar;
    @FXML private VBox tripsContainer;
    @FXML private Label emptyLabel;

    private AppContext context;
    private String currentTab = "Pending";
    private final List<Subscription> subscriptions = new ArrayList<>();

    public void setContext(AppContext context) {
        this.context = context;
        context.runBookingCompletionSweep();
        loadTrips(currentTab);
        subscribeToEvents();
    }

    public AppContext getContext() {
        return context;
    }

    public void cleanup() {
        for (Subscription sub : subscriptions) {
            sub.unsubscribe();
        }
        subscriptions.clear();
    }

    @FXML
    private void showPending() {
        loadTrips("Pending");
    }

    @FXML
    private void showUpcoming() {
        loadTrips("Upcoming");
    }

    @FXML
    private void showActive() {
        loadTrips("Active");
    }

    @FXML
    private void showCompleted() {
        loadTrips("Completed");
    }

    @FXML
    private void showCancelled() {
        loadTrips("Cancelled");
    }

    void loadTrips(String tab) {
        this.currentTab = tab;
        updateTabStyles(tab);
        List<Booking> all = context.bookingService().tripsFor(
                context.session().currentUser().orElseThrow().userId(), null);
        LocalDate today = LocalDate.now();
        List<Booking> filtered = all.stream().filter(b -> matchesTab(b, tab, today)).toList();

        tripsContainer.getChildren().clear();
        if (filtered.isEmpty()) {
            emptyLabel.setText("No " + tab.toLowerCase() + " trips.");
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
        } else {
            emptyLabel.setVisible(false);
            emptyLabel.setManaged(false);
            for (Booking booking : filtered) {
                tripsContainer.getChildren().add(buildTripCard(booking, tab));
            }
        }
    }

    private boolean matchesTab(Booking booking, String tab, LocalDate today) {
        return switch (tab) {
            case "Pending" -> booking.status() == BookingStatus.PENDING;
            case "Upcoming" -> booking.status() == BookingStatus.CONFIRMED
                    && booking.startDate().isAfter(today);
            case "Active" -> booking.status() == BookingStatus.CONFIRMED
                    && !booking.startDate().isAfter(today)
                    && !booking.endDate().isBefore(today);
            case "Completed" -> booking.status() == BookingStatus.COMPLETED
                    || booking.status() == BookingStatus.FORCE_COMPLETED;
            case "Cancelled" -> CANCELLED_STATUSES.contains(booking.status());
            default -> false;
        };
    }

    private Node buildTripCard(Booking booking, String tab) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("trip-card");

        String propertyTitle = lookupPropertyTitle(booking.listingId());
        Label title = new Label(propertyTitle);
        title.getStyleClass().add("card-title");

        Label dates = new Label(booking.startDate().format(DATE_FORMAT) + " - "
                + booking.endDate().format(DATE_FORMAT));
        dates.getStyleClass().add("small");

        Label status = new Label(formatStatus(booking.status()));
        status.getStyleClass().addAll("trip-status", statusStyleClass(booking.status()));

        BigDecimal total = booking.totalAmount().setScale(2, RoundingMode.HALF_UP);
        Label amount = new Label("SGD " + total);
        amount.getStyleClass().add("card-price");

        card.getChildren().addAll(title, dates, status, amount);

        if (booking.hostDecisionMessage() != null && !booking.hostDecisionMessage().isBlank()) {
            Label hostMessage = new Label("Host message: " + booking.hostDecisionMessage());
            hostMessage.getStyleClass().add("small");
            card.getChildren().add(hostMessage);
        }

        if (canCancel(booking)) {
            Button cancelButton = new Button("Cancel Booking");
            cancelButton.getStyleClass().add("outline-button");
            cancelButton.setOnAction(event -> handleCancel(booking));
            card.getChildren().add(cancelButton);
        }

        return card;
    }

    private String lookupPropertyTitle(java.util.UUID propertyId) {
        try {
            Property property = context.listingService().getDetail(propertyId);
            return property.title();
        } catch (Exception exception) {
            return "Unknown Property";
        }
    }

    private static String formatStatus(BookingStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case CONFIRMED -> "Confirmed";
            case REJECTED -> "Rejected";
            case CANCELLED_BY_GUEST -> "Cancelled";
            case CANCELLED_BY_HOST -> "Cancelled by Host";
            case COMPLETED -> "Completed";
            case FORCE_CANCELLED -> "Force Cancelled";
            case FORCE_COMPLETED -> "Force Completed";
        };
    }

    private static String statusStyleClass(BookingStatus status) {
        return switch (status) {
            case PENDING -> "status-pending";
            case CONFIRMED -> "status-confirmed";
            case COMPLETED, FORCE_COMPLETED -> "status-completed";
            case REJECTED, CANCELLED_BY_GUEST, CANCELLED_BY_HOST,
                    FORCE_CANCELLED -> "status-cancelled";
        };
    }

    private static boolean canCancel(Booking booking) {
        return booking.status() == BookingStatus.PENDING
                || (booking.status() == BookingStatus.CONFIRMED
                        && booking.startDate().isAfter(LocalDate.now()));
    }

    private void handleCancel(Booking booking) {
        try {
            context.bookingService().cancel(booking.bookingId(),
                    context.session().currentUser().orElseThrow().userId());
            loadTrips(currentTab);
        } catch (Exception exception) {
            Label error = new Label("Cancel failed: " + exception.getMessage());
            error.getStyleClass().add("error-message");
            tripsContainer.getChildren().add(0, error);
        }
    }

    private void subscribeToEvents() {
        if (context.eventBus() == null) {
            return;
        }
        subscriptions.add(context.eventBus().subscribe(BookingConfirmedEvent.class,
                event -> Platform.runLater(() -> loadTrips(currentTab))));
        subscriptions.add(context.eventBus().subscribe(BookingCancelledEvent.class,
                event -> Platform.runLater(() -> loadTrips(currentTab))));
    }

    private void updateTabStyles(String activeTab) {
        for (Node node : tabBar.getChildren()) {
            if (node instanceof Label label) {
                label.getStyleClass().remove("trip-tab-active");
                if (label.getText().equals(activeTab)) {
                    label.getStyleClass().add("trip-tab-active");
                }
            }
        }
    }
}
