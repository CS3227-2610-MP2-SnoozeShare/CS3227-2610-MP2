package com.snoozeshare.ui.guest.messaging;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.BookingMessage;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.BookingMessagePostedEvent;
import com.snoozeshare.service.BookingConversationSummary;
import com.snoozeshare.ui.common.messaging.ChatBubbles;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class GuestMessagesController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    @FXML private ListView<BookingConversationSummary> conversationList;
    @FXML private Label headerTitle;
    @FXML private Label headerSubtitle;
    @FXML private Label statusLabel;
    @FXML private Label emptyLabel;
    @FXML private VBox thread;
    @FXML private TextField replyField;
    @FXML private Button sendButton;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private AppContext context;
    private UUID guestId;
    private BookingConversationSummary selected;
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
    }

    public void setContext(AppContext appContext) {
        cleanup();
        disposed = false;
        context = appContext;
        guestId = context.session().currentUser().orElseThrow().userId();
        subscriptions.add(context.eventBus().subscribe(BookingMessagePostedEvent.class,
                event -> refreshFor(event.message().bookingId())));
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
            context.bookingConversationService().post(selected.bookingId(), guestId, Role.GUEST, body.trim());
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
        UUID selectedId = selected == null ? null : selected.bookingId();
        List<BookingConversationSummary> rows = context.bookingConversationService()
                .conversationsFor(guestId, Role.GUEST);
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

    private void refreshFor(UUID bookingId) {
        if (disposed) {
            return;
        }
        Runnable refresh = this::refreshInbox;
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
        headerTitle.setText(selected.listingTitle());
        headerSubtitle.setText("Direct message with " + selected.counterpartName() + " (Host) · "
                + dateRange(selected.startDate(), selected.endDate()));
        statusLabel.setVisible(false);
        List<BookingMessage> messages = context.bookingConversationService()
                .thread(selected.bookingId(), guestId, Role.GUEST);
        ChatBubbles.renderBooking(thread, messages, message -> message.authorId().equals(guestId),
                message -> message.authorId().equals(guestId) ? "You" : selected.counterpartName());
        context.bookingConversationService().markRead(selected.bookingId(), guestId, Role.GUEST);
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

    private static int findRow(List<BookingConversationSummary> rows, UUID bookingId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).bookingId().equals(bookingId)) {
                return i;
            }
        }
        return -1;
    }

    private static String dateRange(LocalDate start, LocalDate end) {
        String endText = start.getMonth() == end.getMonth() && start.getYear() == end.getYear()
                ? Integer.toString(end.getDayOfMonth()) : DATE.format(end);
        return DATE.format(start) + "\u2013" + endText;
    }

    private static final class ConversationCell extends ListCell<BookingConversationSummary> {
        @Override
        protected void updateItem(BookingConversationSummary row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label(row.listingTitle() + " \u00b7 "
                    + dateRange(row.startDate(), row.endDate()));
            title.getStyleClass().add("host-message-row-title");
            title.setMinWidth(0);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setTextOverrun(OverrunStyle.ELLIPSIS);
            Label subtitle = new Label("Direct message \u00b7 " + row.counterpartName() + " (Host)");
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
