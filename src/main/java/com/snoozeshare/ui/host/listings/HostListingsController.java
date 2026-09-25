package com.snoozeshare.ui.host.listings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public final class HostListingsController {

    @FXML
    private Label statusLabel;

    @FXML
    private FlowPane listingCards;

    private AppContext context;

    public void setContext(AppContext appContext) {
        context = appContext;
        reload();
    }

    @FXML
    private void initialize() {
        // Form actions are added in the next W6 UI slice.
    }

    @FXML
    private void handleCreate() {
        // Implemented in the next W6 UI slice.
    }

    public void reload() {
        if (context == null) {
            return;
        }
        List<Property> properties = context.listingService().findByHostId(
                context.session().currentUser().orElseThrow().userId());
        listingCards.getChildren().clear();
        if (properties.isEmpty()) {
            statusLabel.setText("No listings yet. Create your first listing below.");
            return;
        }
        statusLabel.setText(properties.size() + " listing"
                + (properties.size() == 1 ? "" : "s") + "");
        properties.stream().map(this::createCard).forEach(listingCards.getChildren()::add);
    }

    private VBox createCard(Property property) {
        VBox card = new VBox(6);
        card.getStyleClass().add("listing-card");
        Label title = new Label(property.title());
        title.getStyleClass().add("card-title");
        Label details = new Label(property.city() + " · " + property.status().name());
        details.getStyleClass().add("small");
        Label rate = new Label("SGD " + property.baseNightlyRate()
                .setScale(2, RoundingMode.HALF_UP) + " / night");
        rate.getStyleClass().add("card-price");
        card.getChildren().addAll(title, details, rate);
        return card;
    }
}
