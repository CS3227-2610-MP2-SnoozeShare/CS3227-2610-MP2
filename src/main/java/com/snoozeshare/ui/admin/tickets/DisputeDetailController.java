package com.snoozeshare.ui.admin.tickets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.service.DisputeDetail;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class DisputeDetailController {

    @FXML private Label crumbLabel;
    @FXML private Label statusBadge;
    @FXML private Button assignButton;
    @FXML private Label listingLabel;
    @FXML private Label datesLabel;
    @FXML private Label guestLabel;
    @FXML private Label hostLabel;
    @FXML private Label escrowLabel;
    @FXML private Label phaseLabel;
    @FXML private Label guestThreadTitle;
    @FXML private Label hostThreadTitle;
    @FXML private VBox guestThread;
    @FXML private VBox hostThread;
    @FXML private TextField guestInput;
    @FXML private TextField hostInput;
    @FXML private Label notesHistory;
    @FXML private TextArea notesArea;
    @FXML private Button addNoteButton;
    @FXML private Button acceptButton;
    @FXML private Button rejectButton;
    @FXML private Button manualButton;
    @FXML private Label errorLabel;

    private AppContext context;
    private UUID ticketId;
    private DisputeDetail detail;
    private Runnable onBack = () -> { };

    public void setContext(AppContext appContext) {
        context = appContext;
    }

    public void setOnBack(Runnable callback) {
        onBack = callback == null ? () -> { } : callback;
    }

    public void load(UUID id) {
        ticketId = id;
        render();
    }

    private UUID me() {
        return context.session().currentUser().orElseThrow().userId();
    }

    private void render() {
        detail = context.disputeQueryService().detail(ticketId);
        Ticket ticket = detail.ticket();
        boolean mine = me().equals(ticket.assignedAgentId());
        boolean underReview = ticket.status() == TicketStatus.UNDER_REVIEW;
        boolean canResolve = underReview && mine && detail.escrowHeld();

        crumbLabel.setText(detail.ticketLabel() + " " + ticket.title());
        statusBadge.setText(ticket.status() == TicketStatus.OPEN ? "Unassigned" : statusText(ticket.status()));
        statusBadge.getStyleClass().setAll(ticket.status() == TicketStatus.OPEN ? "badge-danger"
                : underReview ? "badge-warning" : "badge-success");
        assignButton.setDisable(ticket.status() != TicketStatus.OPEN);
        listingLabel.setText(detail.listingTitle());
        datesLabel.setText(detail.startDate() + " to " + detail.endDate());
        guestLabel.setText(detail.guestName());
        hostLabel.setText(detail.hostName());
        escrowLabel.setText(detail.escrowHeld() ? money(detail.escrowAmount()) + " held" : "Settled");
        phaseLabel.setText(detail.phaseLabel());
        guestThreadTitle.setText("Guest messages · " + detail.guestName());
        hostThreadTitle.setText("Host messages · " + detail.hostName());
        notesHistory.setText(ticket.agentNotes() == null ? "No notes yet." : ticket.agentNotes());
        addNoteButton.setDisable(!(underReview && mine));
        acceptButton.setText("Accept — remedy " + (ticket.raisedByRole() == Role.HOST ? "host" : "guest"));
        acceptButton.setDisable(!canResolve);
        rejectButton.setDisable(!canResolve);
        manualButton.setDisable(!canResolve);
        renderThread(guestThread, ThreadChannel.GUEST);
        renderThread(hostThread, ThreadChannel.HOST);
    }

    private void renderThread(VBox box, ThreadChannel channel) {
        box.getChildren().clear();
        List<Message> messages = context.messageService().thread(ticketId, channel);
        if (messages.isEmpty()) {
            Label empty = new Label("No messages yet.");
            empty.getStyleClass().add("small");
            box.getChildren().add(empty);
        }
        for (Message message : messages) {
            Label bubble = new Label(message.body());
            bubble.setWrapText(true);
            bubble.getStyleClass().add(message.authorRole() == Role.AGENT ? "chat-bubble-agent" : "chat-bubble");
            box.getChildren().add(bubble);
        }
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }

    @FXML
    private void handleAssign() {
        run(() -> context.ticketService().assignToMe(ticketId, me()));
    }

    @FXML
    private void handleAddNote() {
        run(() -> {
            context.ticketService().addAgentNote(ticketId, notesArea.getText(), me());
            notesArea.clear();
        });
    }

    @FXML
    private void handleSendGuest() {
        send(ThreadChannel.GUEST, guestInput);
    }

    @FXML
    private void handleSendHost() {
        send(ThreadChannel.HOST, hostInput);
    }

    @FXML
    private void handleAccept() {
        resolve(ResolutionMode.ACCEPT);
    }

    @FXML
    private void handleReject() {
        resolve(ResolutionMode.REJECT);
    }

    @FXML
    private void handleManual() {
        resolve(ResolutionMode.MANUAL);
    }

    private void send(ThreadChannel channel, TextField input) {
        run(() -> {
            context.messageService().post(ticketId, channel, me(), Role.AGENT, input.getText());
            input.clear();
        });
    }

    private void resolve(ResolutionMode mode) {
        ResolutionDialogController.show(detail, mode)
                .ifPresent(request -> run(() -> context.ticketService().resolve(ticketId, request, me())));
    }

    private void run(Runnable action) {
        try {
            action.run();
            errorLabel.setText("");
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
        }
        render();
    }

    private static String statusText(TicketStatus status) {
        return DisputeQueueController.statusText(status);
    }

    private static String money(BigDecimal value) {
        return "SGD " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
