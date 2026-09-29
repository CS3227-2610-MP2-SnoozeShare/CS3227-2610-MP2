package com.snoozeshare.ui.guest.messaging;

import java.io.IOException;
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
import com.snoozeshare.infra.events.events.TicketOpenedEvent;
import com.snoozeshare.service.BookingConversationSummary;
import com.snoozeshare.service.ConversationSummary;
import com.snoozeshare.ui.common.messaging.ChatBubbles;
import com.snoozeshare.ui.guest.tickets.TicketFilingController;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class GuestMessagesController {

    @FXML private StackPane rootStack;
    @FXML private ListView<GuestConversationRow> conversationList;
    @FXML private Label headerTitle;
    @FXML private Label headerSubtitle;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;
    @FXML private Label emptyLabel;
    @FXML private VBox thread;
    @FXML private TextField replyField;
    @FXML private Button sendButton;
    @FXML private Button newTicketButton;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private AppContext context;
    private UUID guestId;
    private GuestConversationRow selected;
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
        newTicketButton.setOnAction(event -> showTicketFilingModal());
    }

    public void setContext(AppContext appContext) {
        cleanup();
        disposed = false;
        context = appContext;
        guestId = context.session().currentUser().orElseThrow().userId();
        subscriptions.add(context.eventBus().subscribe(BookingMessagePostedEvent.class,
                event -> refreshFor(event.message().bookingId(), GuestConversationRow.Kind.BOOKING)));
        subscriptions.add(context.eventBus().subscribe(MessagePostedEvent.class,
                event -> refreshFor(event.message().ticketId(), GuestConversationRow.Kind.TICKET)));
        subscriptions.add(context.eventBus().subscribe(TicketOpenedEvent.class,
                event -> runOnFx(this::refreshInbox)));
        refreshInbox();
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
            if (selected.kind() == GuestConversationRow.Kind.BOOKING) {
                context.bookingConversationService()
                        .post(selected.sourceId(), guestId, Role.GUEST, body.trim());
            } else {
                context.messageService()
                        .post(selected.sourceId(), ThreadChannel.GUEST, guestId, Role.GUEST, body.trim());
            }
            replyField.clear();
            statusLabel.setVisible(false);
            refreshInbox();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showStatus(exception.getMessage());
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
                .conversationsFor(guestId, Role.GUEST);
        List<ConversationSummary> ticketRows = context.messageService()
                .conversationsFor(guestId, Role.GUEST);
        List<GuestConversationRow> rows = GuestConversationRow.merge(bookingRows, ticketRows);
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

    private void refreshFor(UUID sourceId, GuestConversationRow.Kind kind) {
        if (disposed) {
            return;
        }
        runOnFx(this::refreshInbox);
    }

    private void loadSelected() {
        if (selected == null || context == null) {
            return;
        }
        headerTitle.setText(selected.title());
        statusLabel.setVisible(false);
        boolean isBooking = selected.kind() == GuestConversationRow.Kind.BOOKING;
        boolean isTicket = selected.kind() == GuestConversationRow.Kind.TICKET;
        newTicketButton.setVisible(isBooking);
        newTicketButton.setManaged(isBooking);
        if (isTicket) {
            String label = selected.open() ? "OPEN" : "RESOLVED";
            String styleClass = selected.open() ? "host-message-status-open" : "host-message-status-resolved";
            headerStatusLabel.setText(label);
            headerStatusLabel.getStyleClass().setAll("host-message-status", styleClass);
            headerStatusLabel.setVisible(true);
            headerStatusLabel.setManaged(true);
        } else {
            headerStatusLabel.setVisible(false);
            headerStatusLabel.setManaged(false);
        }
        if (isBooking) {
            headerSubtitle.setText("Direct message with " + selected.counterpartName()
                    + " (Host) \u00b7 Booking #" + shortId(selected.sourceId()));
            List<BookingMessage> messages = context.bookingConversationService()
                    .thread(selected.sourceId(), guestId, Role.GUEST);
            ChatBubbles.renderBooking(thread, messages,
                    message -> message.authorId().equals(guestId),
                    message -> message.authorId().equals(guestId) ? "You" : selected.counterpartName());
            context.bookingConversationService().markRead(selected.sourceId(), guestId, Role.GUEST);
        } else {
            headerSubtitle.setText(selected.subtitle());
            List<Message> messages = context.messageService()
                    .thread(selected.sourceId(), ThreadChannel.GUEST, guestId, Role.GUEST);
            ChatBubbles.render(thread, messages,
                    message -> message.authorId().equals(guestId),
                    message -> message.authorId().equals(guestId) ? "You" : selected.counterpartName());
            context.messageService().markRead(selected.sourceId(), ThreadChannel.GUEST, guestId, Role.GUEST);
        }
        replyField.setDisable(!selected.open());
        sendButton.setDisable(!selected.open());
    }

    private void showTicketFilingModal() {
        if (selected == null || selected.kind() != GuestConversationRow.Kind.BOOKING) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/tickets/ticket-filing.fxml"));
            Node dialogView = loader.load();
            TicketFilingController controller = loader.getController();

            StackPane overlay = new StackPane();
            overlay.getStyleClass().add("modal-overlay");
            overlay.getChildren().add(dialogView);
            StackPane.setAlignment(dialogView, Pos.CENTER);
            overlay.setOnMouseClicked(event -> {
                if (event.getTarget() == overlay) {
                    rootStack.getChildren().remove(overlay);
                }
            });

            rootStack.getChildren().add(overlay);
            controller.configure(context, selected.sourceId(), () -> {
                rootStack.getChildren().remove(overlay);
                refreshInbox();
            });
        } catch (IOException exception) {
            showStatus("Unable to open ticket filing form.");
        }
    }

    private void clearThread() {
        selected = null;
        headerTitle.setText("Messages");
        headerSubtitle.setText("");
        headerStatusLabel.setVisible(false);
        headerStatusLabel.setManaged(false);
        thread.getChildren().clear();
        replyField.setDisable(true);
        sendButton.setDisable(true);
        newTicketButton.setVisible(false);
        newTicketButton.setManaged(false);
    }

    private void showStatus(String message) {
        statusLabel.setText(message == null ? "Unable to send message." : message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private static void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private static int findRow(List<GuestConversationRow> rows, UUID sourceId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).sourceId().equals(sourceId)) {
                return i;
            }
        }
        return -1;
    }

    private static String shortId(UUID id) {
        String text = id.toString();
        return text.substring(text.length() - 4);
    }

    private static final class ConversationCell extends ListCell<GuestConversationRow> {
        @Override
        protected void updateItem(GuestConversationRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label(row.title());
            title.getStyleClass().add("host-message-row-title");
            title.setMinWidth(0);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setTextOverrun(OverrunStyle.ELLIPSIS);
            Label subtitle = new Label(row.subtitle());
            subtitle.getStyleClass().add("host-message-row-subtitle");
            subtitle.setMinWidth(0);
            subtitle.setMaxWidth(Double.MAX_VALUE);
            subtitle.setTextOverrun(OverrunStyle.ELLIPSIS);
            VBox copy = new VBox(4, title, subtitle);
            copy.setMinWidth(0);
            copy.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(copy, Priority.ALWAYS);
            HBox content = new HBox(copy);
            content.setMaxWidth(Double.MAX_VALUE);
            content.prefWidthProperty().bind(widthProperty().subtract(24));
            content.getStyleClass().add("host-message-row");
            setGraphic(content);
        }
    }
}
