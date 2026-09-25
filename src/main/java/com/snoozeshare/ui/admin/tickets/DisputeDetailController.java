package com.snoozeshare.ui.admin.tickets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.service.DisputeDetail;
import com.snoozeshare.ui.admin.HeightGrip;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class DisputeDetailController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);
    private static final double BUBBLE_SHARE = 0.85;
    private static final double CHAT_MIN_HEIGHT = 240;
    private static final double CHAT_MAX_HEIGHT = 900;
    private static final double NOTES_MIN_HEIGHT = 56;
    private static final double NOTES_MAX_HEIGHT = 400;

    @FXML private VBox guestBox;
    @FXML private VBox hostBox;
    @FXML private StackPane guestGrip;
    @FXML private StackPane hostGrip;
    @FXML private StackPane notesGrip;
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
    @FXML private TextArea notesArea;
    @FXML private Button saveNotesButton;
    @FXML private Button acceptButton;
    @FXML private Button rejectButton;
    @FXML private Button manualButton;
    @FXML private Label errorLabel;

    private AppContext context;
    private UUID ticketId;
    private DisputeDetail detail;
    private Runnable onBack = () -> { };

    @FXML
    private void initialize() {
        // Both chat boxes follow either grip so they always keep an equal height; the notes box has its own.
        HeightGrip.attach(guestGrip, CHAT_MIN_HEIGHT, CHAT_MAX_HEIGHT, guestBox, hostBox);
        HeightGrip.attach(hostGrip, CHAT_MIN_HEIGHT, CHAT_MAX_HEIGHT, guestBox, hostBox);
        HeightGrip.attach(notesGrip, NOTES_MIN_HEIGHT, NOTES_MAX_HEIGHT, notesArea);
    }

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
        boolean open = ticket.status() == TicketStatus.OPEN;
        statusBadge.setText((open ? "Unassigned" : statusText(ticket.status())).toUpperCase(Locale.ROOT));
        statusBadge.getStyleClass().setAll(open ? "agent-pill-danger" : underReview ? "agent-pill-warning"
                : "agent-pill-success", "agent-pill");
        boolean unassignable = underReview && mine;
        assignButton.setText(unassignable ? "Unassign" : "Assign to me");
        assignButton.setDisable(!(open || unassignable));
        listingLabel.setText(detail.listingTitle());
        datesLabel.setText("Booking #" + ticket.bookingId().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                + " \u00b7 " + dayText(detail.startDate()) + " \u2013 " + dayText(detail.endDate()));
        guestLabel.setText(detail.guestName());
        hostLabel.setText(detail.hostName());
        escrowLabel.setText(detail.escrowHeld() ? money(detail.escrowAmount()) : "Settled");
        phaseLabel.setText(detail.phaseLabel().toUpperCase(Locale.ROOT));
        guestThreadTitle.setText(("Guest messages \u00b7 " + detail.guestName()).toUpperCase(Locale.ROOT));
        hostThreadTitle.setText(("Host messages \u00b7 " + detail.hostName()).toUpperCase(Locale.ROOT));
        guestInput.setPromptText("Reply to " + firstName(detail.guestName()) + "\u2026");
        hostInput.setPromptText("Reply to " + firstName(detail.hostName()) + "\u2026");
        notesArea.setText(ticket.agentNotes() == null ? "" : ticket.agentNotes());
        saveNotesButton.setDisable(!(underReview && mine));
        acceptButton.setText("Accept \u2014 remedy " + (ticket.raisedByRole() == Role.HOST ? "host" : "guest"));
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
            box.getChildren().add(bubble(box, message));
        }
    }

    private static HBox bubble(VBox thread, Message message) {
        boolean agent = message.authorRole() == Role.AGENT;
        Label body = new Label(message.body());
        body.setWrapText(true);
        body.getStyleClass().add("agent-bubble-body");
        Label time = new Label(STAMP.format(message.sentAt().atZone(ZoneId.systemDefault())));
        time.getStyleClass().add("agent-chat-time");
        VBox card = new VBox(3, body, time);
        card.getStyleClass().addAll("agent-bubble-box", agent ? "agent-bubble-out" : "agent-bubble-in");
        card.maxWidthProperty().bind(thread.widthProperty().multiply(BUBBLE_SHARE));
        HBox row = new HBox(card);
        row.setAlignment(agent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }

    @FXML
    private void handleAssign() {
        if (detail.ticket().status() == TicketStatus.UNDER_REVIEW) {
            run(() -> context.ticketService().unassign(ticketId, me()));
        } else {
            run(() -> context.ticketService().assignToMe(ticketId, me()));
        }
    }

    @FXML
    private void handleSaveNotes() {
        run(() -> context.ticketService().saveNotes(ticketId, notesArea.getText(), me()));
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
        try {
            render();
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
        }
    }

    private static String statusText(TicketStatus status) {
        return DisputeQueueController.statusText(status);
    }

    private static String dayText(LocalDate date) {
        return DAY.format(date);
    }

    private static String firstName(String fullName) {
        int space = fullName.indexOf(' ');
        return space < 0 ? fullName : fullName.substring(0, space);
    }

    private static String money(BigDecimal value) {
        return "SGD " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
