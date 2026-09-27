package com.snoozeshare.ui.guest.tickets;

import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.service.requests.NewTicketRequest;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public final class TicketFilingController {

    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> remedyCombo;
    @FXML private TextArea supportingArea;
    @FXML private Label statusLabel;

    private AppContext context;
    private UUID bookingId;
    private Runnable onClose;

    public void configure(AppContext context, UUID bookingId, Runnable onClose) {
        this.context = context;
        this.bookingId = bookingId;
        this.onClose = onClose;

        categoryCombo.getItems().clear();
        for (TicketCategory cat : context.ticketService().listCategories()) {
            categoryCombo.getItems().add(cat.label());
        }

        remedyCombo.getItems().clear();
        remedyCombo.getItems().addAll("Full Refund", "Partial Refund", "Other");
    }

    @FXML
    private void handleSubmit() {
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

        String remedyLabel = remedyCombo.getValue();
        if (remedyLabel == null) {
            showError("Please select a requested remedy.");
            return;
        }

        RemedyType remedy = switch (remedyLabel) {
            case "Full Refund" -> RemedyType.FULL_REFUND;
            case "Partial Refund" -> RemedyType.PARTIAL_REFUND;
            default -> RemedyType.OTHER;
        };

        String supporting = supportingArea.getText();
        if (supporting != null && supporting.isBlank()) {
            supporting = null;
        }

        try {
            UUID guestId = context.session().currentUser().orElseThrow().userId();
            NewTicketRequest request = new NewTicketRequest(bookingId, category,
                    title.trim(), description.trim(), remedy, supporting);
            context.ticketService().fileTicket(request, guestId, Role.GUEST);
            onClose.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        onClose.run();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }
}
