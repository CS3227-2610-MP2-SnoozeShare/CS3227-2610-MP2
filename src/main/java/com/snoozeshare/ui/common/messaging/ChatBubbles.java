package com.snoozeshare.ui.common.messaging;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.snoozeshare.domain.model.Message;
import com.snoozeshare.domain.model.BookingMessage;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Draws a chat thread as bubbles, shared by the agent dispute page and the guest and host Messages tabs.
 * Outgoing bubbles sit on the right. Styles come from {@code agent-theme.css}; the guest and host themes
 * do not define them yet.
 */
public final class ChatBubbles {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);
    private static final double BUBBLE_SHARE = 0.85;

    private ChatBubbles() {
    }

    /** Replaces the children of {@code thread}; an empty list shows a "No messages yet." hint. */
    public static void render(VBox thread, List<Message> messages, Predicate<Message> outgoing) {
        thread.getChildren().clear();
        if (messages.isEmpty()) {
            Label empty = new Label("No messages yet.");
            empty.getStyleClass().add("small");
            thread.getChildren().add(empty);
        }
        for (Message message : messages) {
            thread.getChildren().add(bubble(thread, message, outgoing.test(message)));
        }
    }

    /** Replaces the children of {@code thread} with private booking-chat bubbles. */
    public static void renderBooking(VBox thread, List<BookingMessage> messages,
                                     Predicate<BookingMessage> outgoing) {
        thread.getChildren().clear();
        if (messages.isEmpty()) {
            Label empty = new Label("No messages yet.");
            empty.getStyleClass().add("small");
            thread.getChildren().add(empty);
        }
        for (BookingMessage message : messages) {
            thread.getChildren().add(bookingBubble(thread, message, outgoing.test(message)));
        }
    }

    private static HBox bubble(VBox thread, Message message, boolean outgoing) {
        Label body = new Label(message.body());
        body.setWrapText(true);
        body.getStyleClass().add("agent-bubble-body");
        Label time = new Label(STAMP.format(message.sentAt().atZone(ZoneId.systemDefault())));
        time.getStyleClass().add("agent-chat-time");
        VBox card = new VBox(3, body, time);
        card.getStyleClass().addAll("agent-bubble-box", outgoing ? "agent-bubble-out" : "agent-bubble-in");
        card.maxWidthProperty().bind(thread.widthProperty().multiply(BUBBLE_SHARE));
        HBox row = new HBox(card);
        row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private static HBox bookingBubble(VBox thread, BookingMessage message, boolean outgoing) {
        Label body = new Label(message.body());
        body.setWrapText(true);
        body.getStyleClass().add("agent-bubble-body");
        Label time = new Label(STAMP.format(message.sentAt().atZone(ZoneId.systemDefault())));
        time.getStyleClass().add("agent-chat-time");
        VBox card = new VBox(3, body, time);
        card.getStyleClass().addAll("agent-bubble-box", outgoing ? "agent-bubble-out" : "agent-bubble-in");
        card.maxWidthProperty().bind(thread.widthProperty().multiply(BUBBLE_SHARE));
        HBox row = new HBox(card);
        row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }
}
