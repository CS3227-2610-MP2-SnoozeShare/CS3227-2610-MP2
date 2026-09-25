package com.snoozeshare.ui.host.listings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.model.Property;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class HostListingsController {

    @FXML
    private Label statusLabel;

    @FXML
    private FlowPane listingCards;

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private TextField streetAddressField;
    @FXML private TextField cityField;
    @FXML private TextField regionField;
    @FXML private TextField postalCodeField;
    @FXML private ComboBox<PropertyType> propertyTypeCombo;
    @FXML private Spinner<Integer> maxGuestsSpinner;
    @FXML private TextField bedroomsField;
    @FXML private TextField bathroomsField;
    @FXML private TextField rateField;
    @FXML private TextField checkInField;
    @FXML private TextField checkOutField;
    @FXML private CheckBox wifiBox;
    @FXML private CheckBox parkingBox;
    @FXML private CheckBox airConditioningBox;
    @FXML private CheckBox kitchenBox;
    @FXML private CheckBox washerBox;
    @FXML private CheckBox workDeskBox;
    @FXML private Label errorLabel;

    private AppContext context;

    public void setContext(AppContext appContext) {
        context = appContext;
        reload();
    }

    @FXML
    private void initialize() {
        propertyTypeCombo.getItems().addAll(PropertyType.values());
        propertyTypeCombo.getSelectionModel().select(PropertyType.APARTMENT);
        maxGuestsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 1));
    }

    @FXML
    private void handleCreate() {
        try {
            Property draft = new Property(null, null, ListingStatus.ACTIVE,
                    titleField.getText(), descriptionField.getText(), propertyTypeCombo.getValue(),
                    streetAddressField.getText(), cityField.getText(), regionField.getText(),
                    postalCodeField.getText(), maxGuestsSpinner.getValue(),
                    Integer.parseInt(bedroomsField.getText()), Double.parseDouble(bathroomsField.getText()),
                    new BigDecimal(rateField.getText()), parseTime(checkInField.getText()),
                    parseTime(checkOutField.getText()), selectedAmenities(), Instant.now());
            context.listingService().create(draft,
                    context.session().currentUser().orElseThrow().userId());
            errorLabel.setText("Listing created successfully.");
            reload();
            clearForm();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            errorLabel.setText(exception.getMessage());
        }
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
                + (properties.size() == 1 ? "" : "s"));
        properties.stream().map(this::createCard).forEach(listingCards.getChildren()::add);
    }

    private VBox createCard(Property property) {
        VBox card = new VBox(6);
        card.getStyleClass().add("listing-card");
        Label title = new Label(property.title());
        title.getStyleClass().add("card-title");
        Label details = new Label(property.city() + " · " + property.propertyType().name());
        details.getStyleClass().add("small");
        Label rate = new Label("SGD " + property.baseNightlyRate()
                .setScale(2, RoundingMode.HALF_UP) + " / night");
        rate.getStyleClass().add("card-price");
        Label status = new Label(property.status().name());
        status.getStyleClass().addAll("listing-status",
                property.status() == ListingStatus.ACTIVE ? "status-active" : "status-inactive");
        Button toggle = new Button(property.status() == ListingStatus.ACTIVE
                ? "Deactivate" : "Activate");
        toggle.getStyleClass().add("status-toggle");
        toggle.setAccessibleText("Change listing status for " + property.title());
        toggle.setOnAction(event -> handleToggle(property, toggle));
        HBox statusRow = new HBox(8, status, toggle);
        card.getChildren().addAll(title, details, rate, statusRow);
        return card;
    }

    private void handleToggle(Property property, Button toggle) {
        toggle.setDisable(true);
        try {
            ListingStatus target = property.status() == ListingStatus.ACTIVE
                    ? ListingStatus.INACTIVE : ListingStatus.ACTIVE;
            context.listingService().updateStatus(property.propertyId(), target,
                    context.session().currentUser().orElseThrow().userId());
            errorLabel.setText("Listing status updated successfully.");
            reload();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            errorLabel.setText(exception.getMessage());
            toggle.setDisable(false);
        }
    }

    private Set<AmenityType> selectedAmenities() {
        EnumSet<AmenityType> amenities = EnumSet.noneOf(AmenityType.class);
        if (wifiBox.isSelected()) {
            amenities.add(AmenityType.WIFI);
        }
        if (parkingBox.isSelected()) {
            amenities.add(AmenityType.PARKING);
        }
        if (airConditioningBox.isSelected()) {
            amenities.add(AmenityType.AIR_CONDITIONING);
        }
        if (kitchenBox.isSelected()) {
            amenities.add(AmenityType.KITCHEN);
        }
        if (washerBox.isSelected()) {
            amenities.add(AmenityType.WASHER);
        }
        if (workDeskBox.isSelected()) {
            amenities.add(AmenityType.WORK_DESK);
        }
        return Set.copyOf(amenities);
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Time must use HH:mm format");
        }
    }

    private void clearForm() {
        titleField.clear();
        descriptionField.clear();
        streetAddressField.clear();
        cityField.clear();
        regionField.clear();
        postalCodeField.clear();
        bedroomsField.clear();
        bathroomsField.clear();
        rateField.clear();
        checkInField.clear();
        checkOutField.clear();
        wifiBox.setSelected(false);
        parkingBox.setSelected(false);
        airConditioningBox.setSelected(false);
        kitchenBox.setSelected(false);
        washerBox.setSelected(false);
        workDeskBox.setSelected(false);
    }
}
