package com.snoozeshare.ui.guest.tickets;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.TicketOpenedEvent;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class TicketHistoryController {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    @FXML private StackPane historyRoot;
    @FXML private VBox ticketContainer;
    @FXML private Label emptyLabel;

    private AppContext context;
    private final List<Subscription> subscriptions = new ArrayList<>();

    public void setContext(AppContext context) {
        this.context = context;
        loadTickets();
        subscribeToEvents();
    }

    public void cleanup() {
        for (Subscription sub : subscriptions) {
            sub.unsubscribe();
        }
        subscriptions.clear();
    }

    private void loadTickets() {
        var userId = context.session().currentUser().orElseThrow().userId();
        List<Ticket> tickets = context.ticketService().myTickets(userId);

        ticketContainer.getChildren().clear();
        if (tickets.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
        } else {
            emptyLabel.setVisible(false);
            emptyLabel.setManaged(false);
            for (Ticket ticket : tickets) {
                ticketContainer.getChildren().add(buildTicketCard(ticket));
            }
        }
    }

    private Node buildTicketCard(Ticket ticket) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("trip-card");
        card.setStyle("-fx-cursor: hand;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(ticket.title());
        title.getStyleClass().add("card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label status = new Label(formatStatus(ticket.status()));
        status.getStyleClass().addAll("trip-status", statusStyleClass(ticket.status()));

        topRow.getChildren().addAll(title, spacer, status);

        Label category = new Label(ticket.category());
        category.getStyleClass().add("small");

        Label date = new Label("Filed: " + ticket.createdAt()
                .atZone(ZoneId.systemDefault()).format(DATE_FORMAT));
        date.getStyleClass().add("small");

        card.getChildren().addAll(topRow, category, date);
        card.setOnMouseClicked(event -> showDetail(ticket));

        return card;
    }

    private void showDetail(Ticket ticket) {
        VBox detail = new VBox(12);
        detail.setPadding(new Insets(24));
        detail.getStyleClass().add("detail-modal");
        detail.setMaxWidth(520);

        Label title = new Label(ticket.title());
        title.getStyleClass().add("page-title");

        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label categoryLabel = new Label(ticket.category());
        categoryLabel.getStyleClass().add("small");
        Label statusLabel = new Label(formatStatus(ticket.status()));
        statusLabel.getStyleClass().addAll("trip-status", statusStyleClass(ticket.status()));
        metaRow.getChildren().addAll(categoryLabel, statusLabel);

        Label descHeader = new Label("Description");
        descHeader.getStyleClass().add("card-title");
        Label description = new Label(ticket.description());
        description.setWrapText(true);

        detail.getChildren().addAll(title, metaRow, descHeader, description);

        Label remedyHeader = new Label("Requested Remedy");
        remedyHeader.getStyleClass().add("card-title");
        Label remedy = new Label(formatRemedy(ticket.requestedRemedy()));
        detail.getChildren().addAll(remedyHeader, remedy);

        if (ticket.supportingText() != null && !ticket.supportingText().isBlank()) {
            Label supportHeader = new Label("Supporting Information");
            supportHeader.getStyleClass().add("card-title");
            Label support = new Label(ticket.supportingText());
            support.setWrapText(true);
            detail.getChildren().addAll(supportHeader, support);
        }

        Label filedDate = new Label("Filed: " + ticket.createdAt()
                .atZone(ZoneId.systemDefault()).format(DATE_FORMAT));
        filedDate.getStyleClass().add("small");
        detail.getChildren().add(filedDate);

        if (ticket.resolutionReason() != null) {
            Label resHeader = new Label("Resolution");
            resHeader.getStyleClass().add("card-title");
            Label resolution = new Label(ticket.resolutionReason());
            resolution.setWrapText(true);
            Label resolvedDate = new Label("Resolved: " + ticket.resolvedAt()
                    .atZone(ZoneId.systemDefault()).format(DATE_FORMAT));
            resolvedDate.getStyleClass().add("small");
            detail.getChildren().addAll(resHeader, resolution, resolvedDate);
        }

        Button back = new Button("Back to Tickets");
        back.getStyleClass().add("outline-button");
        back.setOnAction(event -> {
            historyRoot.getChildren().remove(historyRoot.getChildren().size() - 1);
            loadTickets();
        });
        detail.getChildren().add(back);

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");
        overlay.getChildren().add(detail);
        StackPane.setAlignment(detail, Pos.CENTER);
        historyRoot.getChildren().add(overlay);
    }

    private static String formatStatus(TicketStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_REVIEW -> "In Review";
            case RESOLVED_APPROVED -> "Approved";
            case RESOLVED_REJECTED -> "Rejected";
        };
    }

    private static String statusStyleClass(TicketStatus status) {
        return switch (status) {
            case OPEN -> "status-pending";
            case IN_REVIEW -> "status-confirmed";
            case RESOLVED_APPROVED -> "status-completed";
            case RESOLVED_REJECTED -> "status-cancelled";
        };
    }

    private static String formatRemedy(RemedyType type) {
        return switch (type) {
            case FULL_REFUND -> "Full Refund";
            case PARTIAL_REFUND -> "Partial Refund";
            case HOST_PAYOUT -> "Host Payout";
            case OTHER -> "Other";
        };
    }

    private void subscribeToEvents() {
        if (context.eventBus() == null) {
            return;
        }
        subscriptions.add(context.eventBus().subscribe(TicketOpenedEvent.class,
                event -> Platform.runLater(this::loadTickets)));
        subscriptions.add(context.eventBus().subscribe(TicketResolvedEvent.class,
                event -> Platform.runLater(this::loadTickets)));
    }
}
