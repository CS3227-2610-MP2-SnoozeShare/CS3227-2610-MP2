package com.snoozeshare.ui.common.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.BookingMessage;

import javafx.application.Platform;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

class ChatBubblesTest {

    private static boolean toolkitAvailable;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
            toolkitAvailable = true;
        } catch (IllegalStateException alreadyStarted) {
            toolkitAvailable = true;
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            toolkitAvailable = false;
        }
    }

    @Test
    void rendersBookingMessagesWithHostOutgoingAlignment() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            VBox thread = new VBox();
            UUID bookingId = UUID.randomUUID();
            List<BookingMessage> messages = List.of(
                    new BookingMessage(UUID.randomUUID(), bookingId, UUID.randomUUID(), Role.GUEST,
                            "Hello", Instant.parse("2026-09-24T10:00:00Z")),
                    new BookingMessage(UUID.randomUUID(), bookingId, UUID.randomUUID(), Role.HOST,
                            "Welcome", Instant.parse("2026-09-24T11:00:00Z")));

            ChatBubbles.renderBooking(thread, messages, message -> message.authorRole() == Role.HOST);

            HBox incoming = (HBox) thread.getChildren().get(0);
            HBox outgoing = (HBox) thread.getChildren().get(1);
            result.complete(thread.getChildren().size() == 2
                    && incoming.getChildren().get(0).getStyleClass().contains("agent-bubble-in")
                    && outgoing.getChildren().get(0).getStyleClass().contains("agent-bubble-out"));
        });
        assertEquals(true, result.get(20, TimeUnit.SECONDS));
    }
}
