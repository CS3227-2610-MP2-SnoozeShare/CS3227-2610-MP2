package com.snoozeshare.ui.host.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.BookingMessage;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.BookingMessagePostedEvent;
import com.snoozeshare.infra.events.events.MessagePostedEvent;
import com.snoozeshare.service.BookingConversationSummary;
import com.snoozeshare.service.ConversationSummary;
import com.snoozeshare.ui.common.messaging.ChatBubbles;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class HostMessagesController {

    @FXML private ListView<HostConversationRow> conversationList;
    @FXML private Label headerTitle;
    @FXML private Label headerSubtitle;
    @FXML private Label statusLabel;
    @FXML private Label emptyLabel;
    @FXML private VBox thread;
    @FXML private TextField replyField;
    @FXML private Button sendButton;
    @FXML private Button newTicketButton;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private AppContext context;
    private UUID hostId;
    private HostConversationRow selected;
    private Runnable onNewTicket;
    private boolean disposed;

    @FXML
    private void initialize() {
        conversationList.setCellFactory(view -> new ConversationCell());
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, old, next) -> {
            if (next != null && next != old) {
                selected = next;
                loadSelected();
            }
        });
        sendButton.setOnAction(event -> handleSend());
        newTicketButton.setOnAction(event -> handleNewTicket());
    }

    public void setContext(AppContext appContext) {
        cleanup();
        disposed = false;
        context = appContext;
        hostId = context.session().currentUser().orElseThrow().userId();
        subscriptions.add(context.eventBus().subscribe(BookingMessagePostedEvent.class,
                event -> refreshFor(event.message().bookingId(), HostConversationRow.Kind.BOOKING)));
        subscriptions.add(context.eventBus().subscribe(MessagePostedEvent.class,
                event -> refreshFor(event.message().ticketId(), HostConversationRow.Kind.TICKET)));
        refreshInbox();
    }

    public void setOnNewTicket(Runnable callback) {
        onNewTicket = callback;
    }

    @FXML
    private void handleSend() {
        if (selected == null || !selected.open()) {
            return;
        }
        String body = replyField.getText();
        if (body == null || body.isBlank()) {
            showStatus("Write a message before sending.");
            return;
        }
        try {
            if (selected.kind() == HostConversationRow.Kind.BOOKING) {
                context.bookingConversationService().post(selected.sourceId(), hostId, Role.HOST, body.trim());
            } else {
                context.messageService().post(selected.sourceId(), ThreadChannel.HOST, hostId, Role.HOST,
                        body.trim());
            }
            replyField.clear();
            statusLabel.setVisible(false);
            refreshInbox();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showStatus(exception.getMessage());
        }
    }

    @FXML
    private void handleNewTicket() {
        if (onNewTicket != null) {
            onNewTicket.run();
        }
    }

    public void cleanup() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
        disposed = true;
    }

    private void refreshInbox() {
        if (context == null || disposed) {
            return;
        }
        UUID selectedId = selected == null ? null : selected.sourceId();
        List<BookingConversationSummary> bookingRows = context.bookingConversationService()
                .conversationsFor(hostId, Role.HOST);
        List<ConversationSummary> ticketRows = context.messageService().conversationsFor(hostId, Role.HOST);
        List<HostConversationRow> rows = HostConversationRow.merge(bookingRows, ticketRows);
        conversationList.getItems().setAll(rows);
        emptyLabel.setVisible(rows.isEmpty());
        emptyLabel.setManaged(rows.isEmpty());
        if (rows.isEmpty()) {
            clearThread();
            return;
        }
        int index = selectedId == null ? 0 : findRow(rows, selectedId);
        conversationList.getSelectionModel().select(index < 0 ? 0 : index);
    }

    private void refreshFor(UUID sourceId, HostConversationRow.Kind kind) {
        if (disposed) {
            return;
        }
        Runnable refresh = () -> {
            if (selected != null && selected.kind() == kind && selected.sourceId().equals(sourceId)) {
                refreshInbox();
            } else {
                refreshInbox();
            }
        };
        if (Platform.isFxApplicationThread()) {
            refresh.run();
        } else {
            Platform.runLater(refresh);
        }
    }

    private void loadSelected() {
        if (selected == null || context == null) {
            return;
        }
        headerTitle.setText(selected.title());
        headerSubtitle.setText(selected.kind() == HostConversationRow.Kind.BOOKING
                ? "Direct message with " + selected.counterpartName() + " (Guest)"
                : selected.subtitle());
        statusLabel.setVisible(false);
        if (selected.kind() == HostConversationRow.Kind.BOOKING) {
            List<BookingMessage> messages = context.bookingConversationService()
                    .thread(selected.sourceId(), hostId, Role.HOST);
            ChatBubbles.renderBooking(thread, messages, message -> message.authorId().equals(hostId));
            context.bookingConversationService().markRead(selected.sourceId(), hostId, Role.HOST);
        } else {
            List<Message> messages = context.messageService()
                    .thread(selected.sourceId(), ThreadChannel.HOST, hostId, Role.HOST);
            ChatBubbles.render(thread, messages, message -> message.authorId().equals(hostId));
            context.messageService().markRead(selected.sourceId(), ThreadChannel.HOST, hostId, Role.HOST);
        }
        replyField.setDisable(!selected.open());
        sendButton.setDisable(!selected.open());
    }

    private void clearThread() {
        selected = null;
        headerTitle.setText("Messages");
        headerSubtitle.setText("");
        thread.getChildren().clear();
        replyField.setDisable(true);
        sendButton.setDisable(true);
    }

    private void showStatus(String message) {
        statusLabel.setText(message == null ? "Unable to send message." : message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private static int findRow(List<HostConversationRow> rows, UUID sourceId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).sourceId().equals(sourceId)) {
                return i;
            }
        }
        return -1;
    }

    private static final class ConversationCell extends ListCell<HostConversationRow> {
        @Override
        protected void updateItem(HostConversationRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label(row.title());
            title.getStyleClass().add("host-message-row-title");
            Label subtitle = new Label(row.subtitle());
            subtitle.getStyleClass().add("host-message-row-subtitle");
            VBox copy = new VBox(4, title, subtitle);
            HBox content = new HBox(copy);
            content.getStyleClass().add("host-message-row");
            if (row.statusLabel() != null) {
                Label status = new Label(row.statusLabel());
                status.getStyleClass().addAll("host-message-status", row.open()
                        ? "host-message-status-open" : "host-message-status-resolved");
                content.getChildren().add(status);
            }
            setGraphic(content);
        }
    }
}
