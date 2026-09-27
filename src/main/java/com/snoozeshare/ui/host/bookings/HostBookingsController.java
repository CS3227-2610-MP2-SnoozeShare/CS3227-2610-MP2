package com.snoozeshare.ui.host.bookings;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.service.HostBookingRow;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

public final class HostBookingsController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");

    @FXML private Label pendingCountLabel;
    @FXML private TableView<HostBookingRow> pendingTable;
    @FXML private TableView<HostBookingRow> pastTable;
    @FXML private Label feedbackLabel;
    @FXML private TableColumn<HostBookingRow, String> pendingGuestColumn;
    @FXML private TableColumn<HostBookingRow, String> pendingListingColumn;
    @FXML private TableColumn<HostBookingRow, String> pendingDatesColumn;
    @FXML private TableColumn<HostBookingRow, String> pendingNightsColumn;
    @FXML private TableColumn<HostBookingRow, String> pendingGrossColumn;
    @FXML private TableColumn<HostBookingRow, String> pendingNetColumn;
    @FXML private TableColumn<HostBookingRow, String> pendingRatingColumn;
    @FXML private TableColumn<HostBookingRow, String> pendingActionColumn;
    @FXML private TableColumn<HostBookingRow, String> pastGuestColumn;
    @FXML private TableColumn<HostBookingRow, String> pastListingColumn;
    @FXML private TableColumn<HostBookingRow, String> pastDatesColumn;
    @FXML private TableColumn<HostBookingRow, String> pastNightsColumn;
    @FXML private TableColumn<HostBookingRow, String> pastTotalColumn;
    @FXML private TableColumn<HostBookingRow, String> pastStatusColumn;

    private AppContext context;

    @FXML
    private void initialize() {
        configureTextColumns(pendingGuestColumn, row -> row.guestDisplayName());
        configureTextColumns(pendingListingColumn, row -> row.listingTitle());
        configureTextColumns(pendingDatesColumn, HostBookingsController::dates);
        configureTextColumns(pendingNightsColumn, row -> Long.toString(row.nights()));
        configureTextColumns(pendingGrossColumn, row -> money(row.grossAmount()));
        configureTextColumns(pendingNetColumn, row -> money(row.projectedNetAmount()));
        configureTextColumns(pendingRatingColumn, HostBookingsController::rating);
        pendingActionColumn.setCellFactory(column -> new ActionCell());
        configureTextColumns(pastGuestColumn, row -> row.guestDisplayName());
        configureTextColumns(pastListingColumn, row -> row.listingTitle());
        configureTextColumns(pastDatesColumn, HostBookingsController::dates);
        configureTextColumns(pastNightsColumn, row -> Long.toString(row.nights()));
        configureTextColumns(pastTotalColumn, row -> money(row.grossAmount()));
        configureTextColumns(pastStatusColumn, row -> status(row.booking().status()));
        pastStatusColumn.setCellFactory(column -> new StatusCell());
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        reload();
    }

    @FXML
    public void reload() {
        if (context == null) {
            return;
        }
        try {
            var hostId = context.session().currentUser().orElseThrow().userId();
            List<HostBookingRow> pending = context.bookingService().pendingRequestRowsFor(hostId);
            pendingTable.getItems().setAll(pending);
            pendingCountLabel.setText(pending.size() + " pending");
            pastTable.getItems().setAll(context.bookingService().historyRowsFor(hostId).stream()
                    .filter(row -> row.booking().status() != BookingStatus.PENDING).toList());
            feedbackLabel.setText("");
        } catch (RuntimeException exception) {
            feedbackLabel.setText("Unable to load booking requests: " + exception.getMessage());
            feedbackLabel.getStyleClass().setAll("error-message");
        }
    }

    private void openDecision(HostBookingRow row, boolean approving) {
        HostBookingDecisionDialogController.show(row, approving, message -> {
            try {
                var hostId = context.session().currentUser().orElseThrow().userId();
                context.bookingService().decide(row.booking().bookingId(), approving, hostId, message);
                reload();
            } catch (RuntimeException exception) {
                feedbackLabel.setText("Unable to update booking: " + exception.getMessage());
                feedbackLabel.getStyleClass().setAll("error-message");
            }
        });
    }

    private static void configureTextColumns(TableColumn<HostBookingRow, String> column,
                                             java.util.function.Function<HostBookingRow, String> value) {
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
    }

    private static String dates(HostBookingRow row) {
        return row.booking().startDate().format(DATE_FORMAT) + "–"
                + row.booking().endDate().format(DATE_FORMAT);
    }

    private static String money(java.math.BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String rating(HostBookingRow row) {
        return row.guestAverageRating().isPresent()
                ? String.format("%.1f ★", row.guestAverageRating().getAsDouble())
                : "No ratings yet";
    }

    private static String status(BookingStatus value) {
        return switch (value) {
            case CONFIRMED, COMPLETED, FORCE_COMPLETED -> "CONFIRMED";
            case REJECTED, CANCELLED_BY_HOST, CANCELLED_BY_GUEST, FORCE_CANCELLED -> "REJECTED";
            default -> value.name();
        };
    }

    private final class StatusCell extends TableCell<HostBookingRow, String> {
        private final Label badge = new Label();

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || item.isBlank()) {
                setText(null);
                setGraphic(null);
                return;
            }
            badge.setText(item.toUpperCase(Locale.ROOT));
            badge.getStyleClass().setAll("host-bookings-status-badge",
                    "CONFIRMED".equals(item) ? "host-bookings-status-confirmed"
                            : "host-bookings-status-rejected");
            setText(null);
            setGraphic(badge);
        }
    }

    private final class ActionCell extends TableCell<HostBookingRow, String> {
        private final HBox actions = new HBox(8);

        private ActionCell() {
            actions.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            HostBookingRow row = getTableRow().getItem();
            Button approve = new Button("Approve");
            approve.getStyleClass().add("booking-approve-button");
            approve.setOnAction(event -> openDecision(row, true));
            Button reject = new Button("Reject");
            reject.getStyleClass().add("booking-reject-button");
            reject.setOnAction(event -> openDecision(row, false));
            actions.getChildren().setAll(approve, reject);
            setGraphic(actions);
        }
    }
}
