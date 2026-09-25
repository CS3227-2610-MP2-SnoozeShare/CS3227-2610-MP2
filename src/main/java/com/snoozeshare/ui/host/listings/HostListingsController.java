package com.snoozeshare.ui.host.listings;

import java.math.RoundingMode;
import java.util.List;
import java.util.function.Consumer;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.model.Property;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class HostListingsController {

    @FXML
    private Label statusLabel;

    @FXML
    private VBox listingCards;

    private AppContext context;
    private Runnable onCreateListing = () -> { };
    private Consumer<Property> onEditListing = property -> { };

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
            statusLabel.setText("No listings yet. Create your first listing.");
            return;
        }
        statusLabel.setText(properties.size() + " listing"
                + (properties.size() == 1 ? "" : "s"));
        properties.stream().map(this::createCard).forEach(listingCards.getChildren()::add);
    }

    private VBox createCard(Property property) {
        VBox card = new VBox(8);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("listing-card");
        Label title = new Label(property.title());
        title.getStyleClass().add("card-title");
        Label details = new Label(property.city() + " · " + property.propertyType().name()
                + " · " + property.maxGuests() + " guests");
        details.getStyleClass().add("small");
        Label rate = new Label("SGD " + property.baseNightlyRate()
                .setScale(2, RoundingMode.HALF_UP) + " / night");
        rate.getStyleClass().add("card-price");
        ToggleButton statusToggle = new ToggleButton("Active");
        statusToggle.setSelected(property.status() == ListingStatus.ACTIVE);
        updateToggleText(statusToggle);
        statusToggle.getStyleClass().add("status-toggle");
        statusToggle.setAccessibleText("Toggle listing status for " + property.title());
        statusToggle.setOnAction(event -> handleToggle(property, statusToggle));
        Button editButton = new Button("Edit");
        editButton.setAccessibleText("Edit listing " + property.title());
        editButton.setOnAction(event -> onEditListing.accept(property));
        HBox actions = new HBox(8, statusToggle, editButton);
        card.getChildren().addAll(title, details, rate, actions);
        return card;
    }

    private void handleToggle(Property property, ToggleButton toggle) {
        try {
            ListingStatus target = toggle.isSelected()
                    ? ListingStatus.ACTIVE : ListingStatus.INACTIVE;
            context.listingService().updateStatus(property.propertyId(), target,
                    context.session().currentUser().orElseThrow().userId());
            updateToggleText(toggle);
            reload();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            toggle.setSelected(!toggle.isSelected());
            updateToggleText(toggle);
            statusLabel.setText(exception.getMessage());
        }
    }

    private static void updateToggleText(ToggleButton toggle) {
        toggle.setText(toggle.isSelected() ? "Active" : "Inactive");
    }
}
