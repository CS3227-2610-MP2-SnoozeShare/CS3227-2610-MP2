package com.snoozeshare.ui.host.listings;

import java.math.RoundingMode;
import java.util.List;
import java.util.function.Consumer;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.service.ListingMetrics;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class HostListingsController {

    @FXML
    private VBox listingCards;

    @FXML
    private Label feedbackLabel;

    private AppContext context;
    private Runnable onCreateListing = () -> { };
    private Consumer<Property> onEditListing = property -> { };
    private Consumer<Property> onViewListing = property -> { };
    private Consumer<Property> onOpenCalendar = property -> { };

    public void setContext(AppContext appContext) {
        context = appContext;
        reload();
    }

    public void setOnCreateListing(Runnable callback) {
        onCreateListing = callback == null ? () -> { } : callback;
    }

    public void setOnEditListing(Consumer<Property> callback) {
        onEditListing = callback == null ? property -> { } : callback;
    }

    public void setOnViewListing(Consumer<Property> callback) {
        onViewListing = callback == null ? property -> { } : callback;
    }

    public void setOnOpenCalendar(Consumer<Property> callback) {
        onOpenCalendar = callback == null ? property -> { } : callback;
    }

    @FXML
    private void handleCreateListing() {
        onCreateListing.run();
    }

    public void reload() {
        if (context == null) {
            return;
        }
        List<Property> properties = context.listingService().findByHostId(
                context.session().currentUser().orElseThrow().userId());
        listingCards.getChildren().clear();
        if (properties.isEmpty()) {
            Label emptyState = new Label("No listings yet. Create your first listing.");
            emptyState.getStyleClass().add("empty-state");
            listingCards.getChildren().add(emptyState);
            return;
        }
        properties.stream().map(this::createCard).forEach(listingCards.getChildren()::add);
    }

    private HBox createCard(Property property) {
        HBox card = new HBox(16);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("listing-card");
        javafx.scene.layout.StackPane imagePlaceholder = new javafx.scene.layout.StackPane();
        imagePlaceholder.getStyleClass().add("listing-image-placeholder");
        imagePlaceholder.setMinSize(96, 96);
        imagePlaceholder.setPrefSize(96, 96);
        imagePlaceholder.setMaxSize(96, 96);
        card.setOnMouseClicked(event -> onViewListing.accept(property));
        Label title = new Label(property.title());
        title.getStyleClass().add("card-title");
        Label details = new Label(displayPropertyType(property) + " · "
                + property.city() + ", " + property.region());
        details.getStyleClass().add("small");
        Label rate = new Label("$" + property.baseNightlyRate()
                .setScale(2, RoundingMode.HALF_UP) + "/night");
        rate.getStyleClass().add("card-price");
        VBox summary = new VBox(6, title, details, rate);
        summary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(summary, javafx.scene.layout.Priority.ALWAYS);

        ListingMetrics metrics = metricsFor(property);
        VBox bookingMetric = metric(String.valueOf(metrics.bookingCount()), "BOOKINGS");
        Label ratingValue = new Label(String.format("%.1f", metrics.averageRating()));
        ratingValue.getStyleClass().add("listing-metric-value");
        Label star = new Label("★");
        star.getStyleClass().add("listing-rating-star");
        HBox ratingLine = new HBox(4, ratingValue, star);
        ratingLine.setAlignment(javafx.geometry.Pos.CENTER);
        Label ratingCaption = new Label("RATING");
        ratingCaption.getStyleClass().add("listing-metric-caption");
        VBox ratingMetric = new VBox(2, ratingLine, ratingCaption);
        ratingMetric.setAlignment(javafx.geometry.Pos.CENTER);

        ToggleButton statusToggle = new ToggleButton();
        statusToggle.setSelected(property.status() == ListingStatus.ACTIVE);
        statusToggle.getStyleClass().add("listing-status-toggle");
        Label statusText = new Label();
        updateToggleText(statusToggle, statusText);
        statusToggle.setAccessibleText("Toggle listing status for " + property.title());
        HBox statusControl = new HBox(8, statusToggle, statusText);
        statusControl.getStyleClass().add("listing-status-control");
        statusControl.setAlignment(javafx.geometry.Pos.CENTER);
        statusToggle.setOnAction(event -> handleToggle(property, statusToggle, statusText));
        Button editButton = new Button("Edit");
        editButton.getStyleClass().add("outline-button");
        editButton.setAccessibleText("Edit listing " + property.title());
        editButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> event.consume());
        editButton.setOnAction(event -> onEditListing.accept(property));
        Button calendarButton = new Button("Open booking calendar");
        calendarButton.getStyleClass().add("outline-button");
        calendarButton.setAccessibleText("Open booking calendar for " + property.title());
        calendarButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> event.consume());
        calendarButton.setOnAction(event -> onOpenCalendar.accept(property));
        HBox actions = new HBox(12, bookingMetric, ratingMetric, statusControl,
                calendarButton, editButton);
        actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        card.getChildren().addAll(imagePlaceholder, summary, actions);
        return card;
    }

    private void handleToggle(Property property, ToggleButton toggle, Label statusText) {
        try {
            ListingStatus target = toggle.isSelected()
                    ? ListingStatus.ACTIVE : ListingStatus.INACTIVE;
            context.listingService().updateStatus(property.propertyId(), target,
                    context.session().currentUser().orElseThrow().userId());
            updateToggleText(toggle, statusText);
            reload();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            toggle.setSelected(!toggle.isSelected());
            updateToggleText(toggle, statusText);
            feedbackLabel.setText(exception.getMessage());
        }
    }

    private ListingMetrics metricsFor(Property property) {
        try {
            return context.listingMetricsService().metricsFor(property.propertyId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            feedbackLabel.setText(exception.getMessage());
            return new ListingMetrics(0, 0.0);
        }
    }

    private static VBox metric(String value, String caption) {
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("listing-metric-value");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("listing-metric-caption");
        VBox metric = new VBox(2, valueLabel, captionLabel);
        metric.setAlignment(javafx.geometry.Pos.CENTER);
        return metric;
    }

    private static void updateToggleText(ToggleButton toggle, Label statusText) {
        boolean active = toggle.isSelected();
        statusText.setText(active ? "ACTIVE" : "INACTIVE");
        statusText.getStyleClass().removeAll("status-active-text", "status-inactive-text");
        statusText.getStyleClass().add(active ? "status-active-text" : "status-inactive-text");
    }

    private static String displayPropertyType(Property property) {
        String value = property.propertyType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
