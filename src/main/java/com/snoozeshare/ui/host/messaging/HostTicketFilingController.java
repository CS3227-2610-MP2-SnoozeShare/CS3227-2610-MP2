package com.snoozeshare.ui.host.messaging;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.service.HostTicketBookingOption;
import com.snoozeshare.service.requests.NewTicketRequest;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public final class HostTicketFilingController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d");

    @FXML private ComboBox<String> bookingCombo;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> remedyCombo;
    @FXML private TextArea supportingArea;
    @FXML private Label statusLabel;

    private final Map<String, UUID> bookingIds = new LinkedHashMap<>();
    private AppContext context;
    private Runnable onClose;
    private Runnable onFiled;

    public void configure(AppContext appContext, UUID preselectedBookingId,
                          Runnable closeCallback, Runnable filedCallback) {
        context = appContext;
        onClose = closeCallback;
        onFiled = filedCallback;
        bookingIds.clear();
        bookingCombo.getItems().clear();
        for (HostTicketBookingOption option : context.ticketService()
                .hostTicketBookingOptions(context.session().currentUser().orElseThrow().userId())) {
            Booking booking = option.booking();
            String label = booking.startDate().format(DATE) + "–" + booking.endDate().format(DATE)
                    + " · Booking #" + shortId(booking.bookingId());
            bookingIds.put(label, booking.bookingId());
            bookingCombo.getItems().add(label);
            if (booking.bookingId().equals(preselectedBookingId)) {
                bookingCombo.getSelectionModel().select(label);
            }
        }
        if (bookingCombo.getValue() == null && bookingCombo.getItems().size() == 1) {
            bookingCombo.getSelectionModel().selectFirst();
        }
        categoryCombo.getItems().clear();
        for (TicketCategory category : context.ticketService().listCategories()) {
            categoryCombo.getItems().add(category.label());
        }
        remedyCombo.getItems().setAll("Full Refund", "Partial Refund", "Other");
    }

    @FXML
    private void handleSubmit() {
        String bookingLabel = bookingCombo.getValue();
        if (bookingLabel == null || !bookingIds.containsKey(bookingLabel)) {
            showError("Please select a booking.");
            return;
        }
        String category = categoryCombo.getValue();
        if (category == null || category.isBlank()) {
            showError("Please select a category.");
            return;
        }
        String title = titleField.getText();
        if (title == null || title.isBlank()) {
            showError("Please enter a title.");
            return;
        }
        String description = descriptionArea.getText();
        if (description == null || description.isBlank()) {
            showError("Please enter a description.");
            return;
        }
        RemedyType remedy = remedy(remedyCombo.getValue());
        if (remedy == null) {
            showError("Please select a requested remedy.");
            return;
        }
        String supporting = supportingArea.getText();
        if (supporting != null && supporting.isBlank()) {
            supporting = null;
        }
        UUID hostId = context.session().currentUser().orElseThrow().userId();
        try {
            context.ticketService().fileTicket(new NewTicketRequest(bookingIds.get(bookingLabel), category,
                    title.trim(), description.trim(), remedy, supporting), hostId, Role.HOST);
            if (onFiled != null) {
                onFiled.run();
            }
            if (onClose != null) {
                onClose.run();
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        if (onClose != null) {
            onClose.run();
        }
    }

    private static RemedyType remedy(String label) {
        if (label == null) {
            return null;
        }
        return switch (label) {
            case "Full Refund" -> RemedyType.FULL_REFUND;
            case "Partial Refund" -> RemedyType.PARTIAL_REFUND;
            case "Other" -> RemedyType.OTHER;
            default -> null;
        };
    }

    private static String shortId(UUID id) {
        String text = id.toString();
        return text.substring(text.length() - 4);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }
}
