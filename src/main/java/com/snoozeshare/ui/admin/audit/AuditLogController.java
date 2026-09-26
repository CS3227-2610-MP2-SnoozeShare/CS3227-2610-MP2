package com.snoozeshare.ui.admin.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.service.AuditFilter;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

public final class AuditLogController {

    static final int PAGE_SIZE = 200;
    private static final String ALL_ACTIONS = "All action types";
    private static final String DASH = "\u2014";
    private static final double ROW_HEIGHT = 47;
    private static final double HEADER_HEIGHT = 34;
    private static final double TOTAL_SHARE = 9.4;
    private static final double WIDTH_FACTOR = 0.995;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> actionCombo;
    @FXML private DatePicker fromPicker;
    @FXML private DatePicker toPicker;
    @FXML private TableView<AuditLogEntry> table;
    @FXML private Label emptyLabel;
    @FXML private Label errorLabel;
    @FXML private Button loadMoreButton;

    private AppContext context;
    private AuditFilter applied = AuditFilter.none();

    @FXML
    private void initialize() {
        actionCombo.getItems().add(ALL_ACTIONS);
        for (AuditAction action : AuditAction.values()) {
            actionCombo.getItems().add(action.name());
        }
        actionCombo.getSelectionModel().selectFirst();
        searchField.setOnAction(event -> handleApply());

        table.getStyleClass().addAll("agent-table", "agent-audit-table");
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(new Label());
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.prefHeightProperty().bind(Bindings.max(1, Bindings.size(table.getItems()))
                .multiply(ROW_HEIGHT).add(HEADER_HEIGHT));
        table.minHeightProperty().bind(table.prefHeightProperty());
        table.maxHeightProperty().bind(table.prefHeightProperty());
        table.getColumns().add(textColumn("TIMESTAMP", 1.1,
                entry -> TIME.format(entry.timestamp().atZone(ZoneId.systemDefault())), null));
        table.getColumns().add(textColumn("ACTOR", 1.0,
                entry -> entry.actorName() == null ? "Unknown user" : entry.actorName(), "cell-strong"));
        table.getColumns().add(actionColumn());
        table.getColumns().add(textColumn("REF", 1.0, AuditLogController::refText, "cell-id"));
        table.getColumns().add(textColumn("STATUS", 1.5, AuditLogController::statusText, null));
        table.getColumns().add(textColumn("REASON", 2.4,
                entry -> entry.reason() == null ? DASH : entry.reason(), null));
        table.getColumns().add(textColumn("AMOUNT", 0.9, AuditLogController::amountText, "cell-strong"));
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        load(true);
    }

    @FXML
    private void handleApply() {
        if (fromPicker.getValue() != null && toPicker.getValue() != null
                && fromPicker.getValue().isAfter(toPicker.getValue())) {
            errorLabel.setText("The From date must not be after the To date.");
            return;
        }
        int selected = actionCombo.getSelectionModel().getSelectedIndex();
        AuditAction action = selected <= 0 ? null : AuditAction.values()[selected - 1];
        Set<AuditAction> actions = action == null ? Set.of() : Set.of(action);
        applied = new AuditFilter(searchField.getText(), actions, fromPicker.getValue(), toPicker.getValue());
        load(true);
    }

    @FXML
    private void handleClear() {
        searchField.clear();
        actionCombo.getSelectionModel().selectFirst();
        fromPicker.setValue(null);
        toPicker.setValue(null);
        applied = AuditFilter.none();
        load(true);
    }

    @FXML
    private void handleLoadMore() {
        load(false);
    }

    private void load(boolean reset) {
        if (context == null) {
            return;
        }
        try {
            if (reset) {
                table.getItems().clear();
            }
            List<AuditLogEntry> page = context.auditService().search(applied, PAGE_SIZE, table.getItems().size());
            table.getItems().addAll(page);
            boolean more = page.size() == PAGE_SIZE;
            loadMoreButton.setVisible(more);
            loadMoreButton.setManaged(more);
            emptyLabel.setText(table.getItems().isEmpty() ? "No audit entries match these filters." : "");
            errorLabel.setText("");
        } catch (RuntimeException failure) {
            errorLabel.setText("Unable to load the audit log: " + failure.getMessage());
        }
    }

    private TableColumn<AuditLogEntry, String> textColumn(String title, double share,
            Function<AuditLogEntry, String> value, String cellClass) {
        TableColumn<AuditLogEntry, String> column = baseColumn(title, share);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("cell-id", "cell-strong");
                if (empty || item == null) {
                    setTooltip(null);
                    return;
                }
                if (cellClass != null) {
                    getStyleClass().add(cellClass);
                }
                if (item.length() > 24) {
                    Tooltip tip = new Tooltip(item);
                    tip.setWrapText(true);
                    tip.setMaxWidth(420);
                    setTooltip(tip);
                } else {
                    setTooltip(null);
                }
            }
        });
        return column;
    }

    private TableColumn<AuditLogEntry, AuditLogEntry> actionColumn() {
        TableColumn<AuditLogEntry, AuditLogEntry> column = baseColumn("ACTION TYPE", 1.5);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(AuditLogEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label pill = new Label(item.actionType());
                pill.getStyleClass().addAll("agent-pill", pillClass(item.actionType()));
                setGraphic(pill);
            }
        });
        return column;
    }

    private <T> TableColumn<AuditLogEntry, T> baseColumn(String title, double share) {
        TableColumn<AuditLogEntry, T> column = new TableColumn<>(title);
        column.setResizable(false);
        column.setReorderable(false);
        column.setSortable(false);
        DoubleBinding width = table.widthProperty().multiply(share * WIDTH_FACTOR / TOTAL_SHARE);
        column.prefWidthProperty().bind(width);
        column.minWidthProperty().bind(width);
        column.maxWidthProperty().bind(width);
        return column;
    }

    static String statusText(AuditLogEntry entry) {
        String before = label(entry.beforeState());
        String after = label(entry.afterState());
        if (before != null && after != null) {
            return before + " \u2192 " + after;
        }
        if (after != null) {
            return after;
        }
        return before != null ? before : DASH;
    }

    static String amountText(AuditLogEntry entry) {
        BigDecimal amount = entry.walletAdjustment();
        if (amount == null) {
            return DASH;
        }
        return (amount.signum() < 0 ? "-" : "+") + "SGD "
                + amount.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String refText(AuditLogEntry entry) {
        List<String> parts = new ArrayList<>();
        if (entry.bookingId() != null) {
            parts.add("Booking #" + lastFour(entry.bookingId().toString()));
        }
        if (entry.ticketId() != null) {
            parts.add("Ticket #" + lastFour(entry.ticketId().toString()));
        }
        return parts.isEmpty() ? DASH : String.join(" \u00b7 ", parts);
    }

    static String pillClass(String actionType) {
        return switch (actionType) {
            case "BOOKING_PAYOUT", "ESCROW_REFUND", "TICKET_REMEDY", "TOP_UP", "BOOKING_COMPLETED" ->
                "agent-pill-success";
            case "AGENT_OVERRIDE", "TICKET_RESOLVED", "TICKET_OPENED", "TICKET_ASSIGNED", "TICKET_UNASSIGNED",
                "TICKET_NOTE_SAVED" -> "agent-pill-accent";
            case "ACCOUNT_SUSPENDED", "ACCOUNT_REACTIVATED", "LISTING_STATUS_CASCADE", "LISTING_STATUS_CHANGED",
                    "BOOKING_CANCELLED_BY_GUEST", "BOOKING_CANCELLED_BY_HOST", "ESCROW_HOLD", "WITHDRAWAL" ->
                "agent-pill-warning";
            case "BOOKING_REJECTED", "BOOKING_FORCE_CANCELLED", "TICKET_CATEGORY_DELETED" -> "agent-pill-danger";
            default -> "agent-pill-neutral";
        };
    }

    private static String lastFour(String id) {
        return id.substring(id.length() - 4);
    }

    /** "IN_REVIEW" becomes "In review"; null stays null. */
    private static String label(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String spaced = state.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
