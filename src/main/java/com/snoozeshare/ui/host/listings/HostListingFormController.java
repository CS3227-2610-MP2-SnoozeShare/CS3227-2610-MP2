package com.snoozeshare.ui.host.listings;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.model.Property;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

public final class HostListingFormController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private TextField streetAddressField;
    @FXML private TextField cityField;
    @FXML private TextField regionField;
    @FXML private TextField postalCodeField;
    @FXML private ComboBox<PropertyType> propertyTypeCombo;
    @FXML private ComboBox<ListingStatus> statusCombo;
    @FXML private TextField maxGuestsField;
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
    @FXML private Hyperlink listingCrumb;
    @FXML private Label editSeparator;
    @FXML private Label formBreadcrumb;
    @FXML private Label formTitle;
    @FXML private Button saveButton;

    private AppContext context;
    private Property property;
    private Runnable onBack = () -> { };
    private Runnable onListings = () -> { };
    private Runnable onListingDetail = () -> { };
    private Runnable onSaved = () -> { };

    @FXML
    private void initialize() {
        propertyTypeCombo.getItems().addAll(PropertyType.values());
        propertyTypeCombo.setConverter(enumConverter());
        propertyTypeCombo.getSelectionModel().select(PropertyType.APARTMENT);
        statusCombo.getItems().addAll(ListingStatus.values());
        statusCombo.setConverter(enumConverter());
        statusCombo.getSelectionModel().select(ListingStatus.ACTIVE);
    }

    public void setContext(AppContext appContext) {
        context = appContext;
    }

    public void setProperty(Property listing) {
        property = listing;
        if (listing == null) {
            listingCrumb.setVisible(false);
            listingCrumb.setManaged(false);
            editSeparator.setVisible(false);
            editSeparator.setManaged(false);
            formBreadcrumb.setText("Create");
            formTitle.setText("Create listing");
            saveButton.setText("Create listing");
        } else {
            listingCrumb.setText(listing.title());
            listingCrumb.setVisible(true);
            listingCrumb.setManaged(true);
            editSeparator.setVisible(true);
            editSeparator.setManaged(true);
            formBreadcrumb.setText("Edit");
            formTitle.setText("Edit listing");
            saveButton.setText("Save listing");
            populate(listing);
        }
    }

    public void setOnBack(Runnable callback) {
        onBack = callback == null ? () -> { } : callback;
    }

    public void setOnListings(Runnable callback) {
        onListings = callback == null ? () -> { } : callback;
    }

    public void setOnListingDetail(Runnable callback) {
        onListingDetail = callback == null ? () -> { } : callback;
    }

    public void setOnSaved(Runnable callback) {
        onSaved = callback == null ? () -> { } : callback;
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }

    @FXML
    private void handleListings() {
        onListings.run();
    }

    @FXML
    private void handleListingDetail() {
        onListingDetail.run();
    }

    @FXML
    private void handleSave() {
        try {
            Property draft = readForm();
            UUID hostId = context.session().currentUser().orElseThrow().userId();
            if (property == null) {
                context.listingService().create(draft, hostId);
            } else {
                context.listingService().update(new Property(property.propertyId(), null,
                        property.status(), draft.title(), draft.description(), draft.propertyType(),
                        draft.streetAddress(), draft.city(), draft.region(), draft.postalCode(),
                        draft.maxGuests(), draft.bedrooms(), draft.bathrooms(),
                        draft.baseNightlyRate(), draft.checkInTime(), draft.checkOutTime(),
                        draft.amenities(), property.createdAt()), hostId);
                if (draft.status() != property.status()) {
                    context.listingService().updateStatus(property.propertyId(), draft.status(), hostId);
                }
            }
            onSaved.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            errorLabel.getStyleClass().remove("success-message");
            if (!errorLabel.getStyleClass().contains("form-error")) {
                errorLabel.getStyleClass().add("form-error");
            }
            errorLabel.setText(exception.getMessage());
        }
    }

    private Property readForm() {
        String title = requireText(titleField.getText(), "Title");
        String description = requireText(descriptionField.getText(), "Description");
        PropertyType propertyType = requireSelected(propertyTypeCombo.getValue(), "Property Type");
        ListingStatus status = requireSelected(statusCombo.getValue(), "Status");
        int maxGuests = parseInteger(maxGuestsField.getText(), "Max Guests", 1);
        int bedrooms = parseInteger(bedroomsField.getText(), "Bedrooms", 0);
        int bathrooms = parseInteger(bathroomsField.getText(), "Bathrooms", 0);
        BigDecimal rate = parseRate(rateField.getText());
        LocalTime checkIn = parseTime(checkInField.getText(), "Check-in Time");
        LocalTime checkOut = parseTime(checkOutField.getText(), "Check-out Time");
        validateTimeRange(checkIn, checkOut);
        String streetAddress = requireText(streetAddressField.getText(), "Street Address");
        String city = requireText(cityField.getText(), "City");
        String region = requireText(regionField.getText(), "Region");
        int postalCode = parseInteger(postalCodeField.getText(), "Postal Code", 1);
        return new Property(property == null ? null : property.propertyId(), null,
                status, title, description, propertyType, streetAddress,
                city, region, postalCode, maxGuests, bedrooms, bathrooms, rate,
                checkIn, checkOut,
                selectedAmenities(), property == null ? Instant.now() : property.createdAt());
    }

    private void populate(Property listing) {
        titleField.setText(listing.title());
        descriptionField.setText(listing.description());
        streetAddressField.setText(listing.streetAddress());
        cityField.setText(listing.city());
        regionField.setText(listing.region());
        postalCodeField.setText(Integer.toString(listing.postalCode()));
        propertyTypeCombo.getSelectionModel().select(listing.propertyType());
        statusCombo.getSelectionModel().select(listing.status());
        maxGuestsField.setText(String.valueOf(listing.maxGuests()));
        bedroomsField.setText(String.valueOf(listing.bedrooms()));
        bathroomsField.setText(String.valueOf(listing.bathrooms()));
        rateField.setText(listing.baseNightlyRate().toPlainString());
        checkInField.setText(listing.checkInTime().toString());
        checkOutField.setText(listing.checkOutTime().toString());
        wifiBox.setSelected(listing.amenities().contains(AmenityType.WIFI));
        parkingBox.setSelected(listing.amenities().contains(AmenityType.PARKING));
        airConditioningBox.setSelected(listing.amenities().contains(AmenityType.AIR_CONDITIONING));
        kitchenBox.setSelected(listing.amenities().contains(AmenityType.KITCHEN));
        washerBox.setSelected(listing.amenities().contains(AmenityType.WASHER));
        workDeskBox.setSelected(listing.amenities().contains(AmenityType.WORK_DESK));
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

    private static LocalTime parseTime(String value, String fieldName) {
        try {
            if (value == null || value.isBlank()) {
                throw new DateTimeParseException("blank", value, 0);
            }
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " must use HH:mm format");
        }
    }

    private static int parseInteger(String value, String fieldName, int minimum) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum) {
                throw new IllegalArgumentException(fieldName + " must be at least " + minimum);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must be a whole number");
        }
    }

    private static BigDecimal parseRate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Rate per night must be provided");
        }
        try {
            BigDecimal rate = new BigDecimal(value);
            if (rate.signum() <= 0) {
                throw new IllegalArgumentException("Rate per night must be greater than 0");
            }
            if (rate.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Rate per night must have at most 2 decimal places");
            }
            return rate;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Rate per night must be a number");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static <E> E requireSelected(E value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be selected");
        }
        return value;
    }

    private static void validateTimeRange(LocalTime checkIn, LocalTime checkOut) {
        if (checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out time must not be after check-in time");
        }
    }

    private static <E extends Enum<E>> StringConverter<E> enumConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(E value) {
                if (value == null) {
                    return "";
                }
                String[] words = value.name().toLowerCase().split("_");
                StringBuilder display = new StringBuilder(words[0]);
                display.setCharAt(0, Character.toUpperCase(display.charAt(0)));
                for (int i = 1; i < words.length; i++) {
                    display.append(' ').append(words[i]);
                }
                return display.toString();
            }

            @Override
            public E fromString(String value) {
                return null;
            }
        };
    }
}
