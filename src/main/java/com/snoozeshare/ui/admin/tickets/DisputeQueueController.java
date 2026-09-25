package com.snoozeshare.ui.admin.tickets;

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
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

public final class DisputeQueueController {

    private static final String[] STATUS_LABELS = {
        "All statuses", "Open", "Under review", "Resolved (approved)", "Resolved (rejected)"
    };
    private static final TicketStatus[] STATUS_VALUES = {
        null, TicketStatus.OPEN, TicketStatus.UNDER_REVIEW, TicketStatus.RESOLVED_APPROVED,
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

        table.getColumns().add(column("Ticket", 90, DisputeSummary::ticketLabel));
        table.getColumns().add(column("Subject", 260, DisputeSummary::title));
        table.getColumns().add(column("Booking", 190, DisputeSummary::listingTitle));
        table.getColumns().add(column("Guest / Host", 210,
                summary -> summary.guestName() + " / " + summary.hostName()));
        table.getColumns().add(column("Assigned", 130,
                summary -> summary.assignedAgentName() == null ? "—" : summary.assignedAgentName()));
        table.getColumns().add(column("Status", 150, summary -> statusText(summary.status())));
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

    private static TableColumn<DisputeSummary, String> column(String title, double width,
            Function<DisputeSummary, String> value) {
        TableColumn<DisputeSummary, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    static String statusText(TicketStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case UNDER_REVIEW -> "Under review";
            case RESOLVED_APPROVED -> "Resolved (approved)";
            case RESOLVED_REJECTED -> "Resolved (rejected)";
        };
    }
}
