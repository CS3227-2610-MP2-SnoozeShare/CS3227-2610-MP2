package com.snoozeshare.ui.guest.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.service.SearchCriteria;
import com.snoozeshare.service.SearchResult;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public final class GuestSearchController {

    @FXML private TextField cityField;
    @FXML private DatePicker checkInPicker;
    @FXML private DatePicker checkOutPicker;
    @FXML private ComboBox<Integer> guestsCombo;
    @FXML private Label statusLabel;
    @FXML private FlowPane resultsPane;

    private AppContext context;
    private Consumer<Property> onPropertySelected;

    public void setContext(AppContext context) {
        this.context = context;
    }

    public void setOnPropertySelected(Consumer<Property> callback) {
        this.onPropertySelected = callback;
    }

    public LocalDate getCheckIn() {
        return checkInPicker.getValue();
    }

    public LocalDate getCheckOut() {
        return checkOutPicker.getValue();
    }

    @FXML
    private void initialize() {
        guestsCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @FXML
    private void handleSearch() {
        String city = cityField.getText();
        if (city != null && city.isBlank()) {
            city = null;
        }
        Integer guests = guestsCombo.getValue();
        LocalDate checkIn = checkInPicker.getValue();
        LocalDate checkOut = checkOutPicker.getValue();

        SearchCriteria criteria = new SearchCriteria(city, guests, checkIn, checkOut);
        List<SearchResult> results = context.listingService().search(criteria);

        resultsPane.getChildren().clear();
        if (results.isEmpty()) {
            statusLabel.setText("No properties found matching your criteria.");
        } else {
            statusLabel.setText(results.size() + " properties found.");
            for (SearchResult result : results) {
                resultsPane.getChildren().add(createPropertyCard(result));
            }
        }
    }

    private VBox createPropertyCard(SearchResult result) {
        Property property = result.property();
        VBox card = new VBox(6);
        card.setPadding(new Insets(16));
        card.setPrefWidth(280);
        card.getStyleClass().add("property-card");

        Label title = new Label(property.title());
        title.getStyleClass().add("card-title");

        Label location = new Label(property.city() + " · " + property.propertyType().name());
        location.getStyleClass().add("small");

        BigDecimal rate = property.baseNightlyRate().setScale(2, RoundingMode.HALF_UP);
        Label price = new Label("SGD " + rate + " / night");
        price.getStyleClass().add("card-price");

        Label capacity = new Label(property.maxGuests() + " guests · "
                + property.bedrooms() + " bed · " + property.bathrooms() + " bath");
        capacity.getStyleClass().add("small");

        card.getChildren().addAll(title, location, price, capacity);

        if (!result.available()) {
            card.setOpacity(0.5);
            Label unavailable = new Label("Unavailable for selected dates");
            unavailable.getStyleClass().addAll("small", "unavailable-label");
            card.getChildren().add(unavailable);
        }

        card.setOnMouseClicked(event -> {
            if (onPropertySelected != null) {
                onPropertySelected.accept(property);
            }
        });

        return card;
    }
}
