package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;
import com.snoozeshare.ui.admin.tickets.DisputeDetailController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** The agent dispute page reads and writes chat through the persistent MessageService. */
class DisputeChatPaneTest {

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
        if (toolkitAvailable) {
            Platform.setImplicitExit(false);
        }
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }

    private static AppContext login(MockDbFixture db, String email) throws Exception {
        AppContext context = AppContext.create(db.jdbcUrl());
        User user = context.userService().authenticate(email);
        context.session().loginAs(user);
        return context;
    }

    private static int bubbles(FXMLLoader loader, String thread) {
        return ((VBox) loader.getNamespace().get(thread)).getChildren().size();
    }

    @Test
    void panesShowSeededThreadsAndSendPersistsThroughTheService(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "ben.alvarez@snoozeshare.test")) {
            int[] counts = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(context);
                controller.load(MockIds.TICKET_3);
                int guestBefore = bubbles(loader, "guestThread");
                TextField input = (TextField) loader.getNamespace().get("guestInput");
                Button send = (Button) loader.getNamespace().get("guestSendButton");
                input.setText("Following up");
                send.fire();
                controller.dispose();
                return new int[] {guestBefore, bubbles(loader, "guestThread"), bubbles(loader, "hostThread")};
            });

            assertEquals(2, counts[0], "the two seeded guest messages");
            assertEquals(3, counts[1], "the new message appears");
            assertEquals(2, counts[2], "the seeded host thread");
            assertEquals(3, context.messageService().thread(MockIds.TICKET_3, ThreadChannel.GUEST,
                    MockIds.AGENT_BEN, Role.AGENT).size(), "and it is stored");
        }
    }

    @Test
    void anOpenPageRefreshesWhenTheGuestPostsThroughTheService(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "ben.alvarez@snoozeshare.test")) {
            FXMLLoader[] holder = new FXMLLoader[1];
            DisputeDetailController[] controller = new DisputeDetailController[1];
            onFx(() -> {
                holder[0] = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                holder[0].load();
                controller[0] = holder[0].getController();
                controller[0].setContext(context);
                controller[0].load(MockIds.TICKET_3);
                return null;
            });

            context.messageService().post(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.GUEST_SOPHIA, Role.GUEST,
                    "The AC is still broken");
            int guestBubbles = onFx(() -> bubbles(holder[0], "guestThread"));

            assertEquals(3, guestBubbles);
            onFx(() -> {
                controller[0].dispose();
                return null;
            });
            context.messageService().post(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.GUEST_SOPHIA, Role.GUEST,
                    "Anyone there?");
            assertEquals(3, onFx(() -> bubbles(holder[0], "guestThread")), "a disposed page no longer refreshes");
        }
    }

    @Test
    void resolvedTicketDisablesTheChatInputs(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            boolean[] disabled = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(context);
                controller.load(MockIds.TICKET_1);
                controller.dispose();
                TextField input = (TextField) loader.getNamespace().get("guestInput");
                Button send = (Button) loader.getNamespace().get("hostSendButton");
                return new boolean[] {input.isDisabled(), send.isDisabled()};
            });

            assertTrue(disabled[0]);
            assertTrue(disabled[1]);
        }
    }

    @Test
    void openTicketKeepsTheChatInputsEnabled(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            boolean disabled = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(context);
                controller.load(MockIds.TICKET_2);
                controller.dispose();
                return ((TextField) loader.getNamespace().get("guestInput")).isDisabled();
            });

            assertFalse(disabled);
        }
    }
}
