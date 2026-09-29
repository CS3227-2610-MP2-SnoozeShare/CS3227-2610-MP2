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
import com.snoozeshare.ui.guest.GuestVisuals;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public final class GuestSearchController {

    private static final int COLUMNS = 3;

    /** Marks unparseable budget text; compared by identity, never used as a real amount. */
    private static final BigDecimal BUDGET_INVALID = new BigDecimal(-1);

    @FXML private TextField cityField;
    @FXML private TextField budgetField;
    @FXML private DatePicker checkInPicker;
    @FXML private DatePicker checkOutPicker;
    @FXML private ComboBox<Integer> guestsCombo;
    @FXML private Label statusLabel;
    @FXML private GridPane resultsPane;

    private AppContext context;
    private Consumer<Property> onPropertySelected;

    public void setContext(AppContext context) {
        this.context = context;
        handleSearch();
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
        checkInPicker.setConverter(GuestVisuals.dateConverter());
        checkOutPicker.setConverter(GuestVisuals.dateConverter());
        GuestVisuals.blockPastDates(checkInPicker);
        GuestVisuals.blockPastDates(checkOutPicker);
        guestsCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7, 8);
        guestsCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer count) {
                return count == null ? null : count + (count == 1 ? " guest" : " guests");
            }

            @Override
            public Integer fromString(String text) {
                return null;
            }
        });
        for (int column = 0; column < COLUMNS; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / COLUMNS);
            constraints.setHgrow(Priority.ALWAYS);
            resultsPane.getColumnConstraints().add(constraints);
        }
    }

    @FXML
    private void handleSearch() {
        String city = cityField.getText();
        if (city != null && city.isBlank()) {
            city = null;
        }
        BigDecimal budget = parseBudget();
        if (budget == BUDGET_INVALID) {
            statusLabel.setText("Enter the maximum nightly budget as a number, or leave it empty.");
            return;
        }

        SearchCriteria criteria = new SearchCriteria(city, guestsCombo.getValue(),
                checkInPicker.getValue(), checkOutPicker.getValue(), budget);
        List<SearchResult> results = context.listingService().search(criteria);

        resultsPane.getChildren().clear();
        if (results.isEmpty()) {
            statusLabel.setText("No properties found matching your criteria.");
        } else {
            statusLabel.setText(results.size() + " properties found.");
            for (int index = 0; index < results.size(); index++) {
                resultsPane.add(createPropertyCard(results.get(index)),
                        index % COLUMNS, index / COLUMNS);
            }
        }
    }

    private BigDecimal parseBudget() {
        String text = budgetField.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim().replace(",", ""));
            return value.signum() < 0 ? BUDGET_INVALID : value;
        } catch (NumberFormatException exception) {
            return BUDGET_INVALID;
        }
    }

    private VBox createPropertyCard(SearchResult result) {
        Property property = result.property();

        Region banner = new Region();
        banner.getStyleClass().addAll("guest-card-banner",
                GuestVisuals.gradientClass(property.propertyId()));
        banner.setMinHeight(130);
        banner.setPrefHeight(130);

        Label title = new Label(property.title());
        title.getStyleClass().add("guest-card-title");
        title.setMinWidth(0);
        Label type = new Label(property.propertyType().name().replace('_', ' '));
        type.getStyleClass().add("guest-type-pill");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleLine = new HBox(8, title, spacer, type);

        Label detail = new Label((property.bedrooms() == 0 ? "Studio" : property.bedrooms() + " bed")
                + " · " + property.city());
        detail.getStyleClass().add("guest-card-detail");

        BigDecimal rate = property.baseNightlyRate().setScale(2, RoundingMode.HALF_UP);
        Label price = new Label("SGD " + rate + " / night");
        price.getStyleClass().add("guest-card-price");

        VBox body = new VBox(6, titleLine, detail, price);
        body.setPadding(new Insets(14, 16, 14, 16));

        VBox card = new VBox(banner, body);
        card.getStyleClass().add("guest-listing-card");
        card.setMaxWidth(Double.MAX_VALUE);

        if (!result.available()) {
            card.setOpacity(0.5);
            Label unavailable = new Label("Unavailable for selected dates");
            unavailable.getStyleClass().addAll("small", "unavailable-label");
            body.getChildren().add(unavailable);
        }

        card.setOnMouseClicked(event -> {
            if (onPropertySelected != null) {
                onPropertySelected.accept(property);
            }
        });

        return card;
    }
}
