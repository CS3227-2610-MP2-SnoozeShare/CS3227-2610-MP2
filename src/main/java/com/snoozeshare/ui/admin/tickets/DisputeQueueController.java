package com.snoozeshare.ui.admin.tickets;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.service.DisputeSummary;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

public final class DisputeQueueController {

    private static final double ROW_HEIGHT = 47;
    private static final double HEADER_HEIGHT = 34;
    private static final double TOTAL_SHARE = 5.8;
    private static final double WIDTH_FACTOR = 0.995;
    private static final String[] STATUS_LABELS = {
        "All statuses", "Open", "In review", "Approved", "Rejected"
    };
    private static final TicketStatus[] STATUS_VALUES = {
        null, TicketStatus.OPEN, TicketStatus.IN_REVIEW, TicketStatus.RESOLVED_APPROVED,
        TicketStatus.RESOLVED_REJECTED
    };

    @FXML private TableView<DisputeSummary> table;
    @FXML private ToggleButton allChip;
    @FXML private ToggleButton unassignedChip;
    @FXML private ToggleButton mineChip;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Label unassignedBadge;
    @FXML private Label emptyLabel;

    private AppContext context;
    private Consumer<UUID> onOpen = id -> { };
    private Subscription subscription;
    private AssigneeFilter filter = AssigneeFilter.ALL;

    @FXML
    private void initialize() {
        ToggleGroup group = new ToggleGroup();
        allChip.setToggleGroup(group);
        unassignedChip.setToggleGroup(group);
        mineChip.setToggleGroup(group);
        group.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true);
                return;
            }
            filter = selected == unassignedChip ? AssigneeFilter.UNASSIGNED
                    : selected == mineChip ? AssigneeFilter.MINE : AssigneeFilter.ALL;
            refresh();
        });
        statusCombo.getItems().addAll(STATUS_LABELS);
        statusCombo.getSelectionModel().selectFirst();
        statusCombo.valueProperty().addListener((observable, previous, selected) -> refresh());

        table.getStyleClass().add("agent-table");
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(new Label());
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.prefHeightProperty().bind(Bindings.max(1, Bindings.size(table.getItems()))
                .multiply(ROW_HEIGHT).add(HEADER_HEIGHT));
        table.minHeightProperty().bind(table.prefHeightProperty());
        table.maxHeightProperty().bind(table.prefHeightProperty());
        table.getColumns().add(column("TICKET", 0.6, DisputeSummary::ticketLabel, "cell-id"));
        table.getColumns().add(column("SUBJECT", 1.4, DisputeSummary::title, "cell-strong"));
        table.getColumns().add(column("BOOKING", 1, DisputeSummary::listingTitle, null));
        table.getColumns().add(column("GUEST / HOST", 1,
                summary -> summary.guestName() + " / " + summary.hostName(), null));
        table.getColumns().add(column("ASSIGNED", 1,
                summary -> summary.assignedAgentName() == null ? "\u2014" : summary.assignedAgentName(), null));
        table.getColumns().add(statusColumn());
        table.setRowFactory(view -> {
            TableRow<DisputeSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    onOpen.accept(row.getItem().ticketId());
                }
            });
            return row;
        });
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        subscription = context.eventBus().subscribe(TicketResolvedEvent.class,
                event -> Platform.runLater(this::refresh));
        refresh();
    }

    public void setOnOpen(Consumer<UUID> callback) {
        onOpen = callback == null ? id -> { } : callback;
    }

    public void dispose() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private void refresh() {
        if (context == null) {
            return;
        }
        UUID agentId = context.session().currentUser().orElseThrow().userId();
        int selected = Math.max(0, statusCombo.getSelectionModel().getSelectedIndex());
        var rows = context.disputeQueryService().queue(STATUS_VALUES[selected], filter, agentId);
        table.getItems().setAll(rows);
        emptyLabel.setText(rows.isEmpty() ? "No disputes match these filters." : "");
        int unassigned = context.disputeQueryService()
                .queue(TicketStatus.OPEN, AssigneeFilter.UNASSIGNED, agentId).size();
        unassignedBadge.setText(unassigned + " unassigned");
        unassignedBadge.setVisible(unassigned > 0);
        unassignedBadge.setManaged(unassigned > 0);
    }

    private TableColumn<DisputeSummary, String> column(String title, double share,
            Function<DisputeSummary, String> value, String cellClass) {
        TableColumn<DisputeSummary, String> column = baseColumn(title, share);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().remove(cellClass == null ? "" : cellClass);
                if (!empty && cellClass != null) {
                    getStyleClass().add(cellClass);
                }
            }
        });
        return column;
    }

    private TableColumn<DisputeSummary, DisputeSummary> statusColumn() {
        TableColumn<DisputeSummary, DisputeSummary> column = baseColumn("STATUS", 0.8);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(DisputeSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label pill = new Label(pillText(item).toUpperCase(Locale.ROOT));
                pill.getStyleClass().addAll("agent-pill", pillClass(item));
                setGraphic(pill);
            }
        });
        return column;
    }

    private <T> TableColumn<DisputeSummary, T> baseColumn(String title, double share) {
        TableColumn<DisputeSummary, T> column = new TableColumn<>(title);
        column.setResizable(false);
        column.setReorderable(false);
        column.setSortable(false);
        DoubleBinding width = table.widthProperty().multiply(share * WIDTH_FACTOR / TOTAL_SHARE);
        column.prefWidthProperty().bind(width);
        column.minWidthProperty().bind(width);
        column.maxWidthProperty().bind(width);
        return column;
    }

    static String pillText(DisputeSummary summary) {
        return switch (summary.status()) {
            case OPEN -> summary.assignedAgentName() == null ? "Unassigned" : "In review";
            case IN_REVIEW -> "In review";
            case RESOLVED_APPROVED -> "Resolved";
            case RESOLVED_REJECTED -> "Rejected";
        };
    }

    static String pillClass(DisputeSummary summary) {
        return switch (summary.status()) {
            case OPEN -> summary.assignedAgentName() == null ? "agent-pill-danger" : "agent-pill-warning";
            case IN_REVIEW -> "agent-pill-warning";
            case RESOLVED_APPROVED -> "agent-pill-success";
            case RESOLVED_REJECTED -> "agent-pill-danger";
        };
    }

    static String statusText(TicketStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_REVIEW -> "In review";
            case RESOLVED_APPROVED -> "Resolved (approved)";
            case RESOLVED_REJECTED -> "Resolved (rejected)";
        };
    }
}
