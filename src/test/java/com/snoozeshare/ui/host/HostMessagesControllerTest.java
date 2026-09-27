package com.snoozeshare.ui.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.ui.host.messaging.HostConversationRow;
import com.snoozeshare.ui.host.messaging.HostMessagesController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

class HostMessagesControllerTest {

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

    @Test
    void loadsUnifiedInboxAndRendersSelectedConversation(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db)) {
            int[] result = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/host/messaging/host-messages.fxml"));
                loader.load();
                HostMessagesController controller = loader.getController();
                controller.setContext(context);
                ListView<HostConversationRow> rows = (ListView<HostConversationRow>) loader.getNamespace()
                        .get("conversationList");
                rows.getSelectionModel().select(0);
                VBox thread = (VBox) loader.getNamespace().get("thread");
                controller.cleanup();
                return new int[] {rows.getItems().size(), thread.getChildren().size()};
            });

            assertTrue(result[0] >= 2);
            assertTrue(result[1] >= 1);
        }
    }

    @Test
    void resolvedTicketSelectionDisablesComposer(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db)) {
            boolean disabled = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/host/messaging/host-messages.fxml"));
                loader.load();
                HostMessagesController controller = loader.getController();
                controller.setContext(context);
                @SuppressWarnings("unchecked")
                ListView<HostConversationRow> rows = (ListView<HostConversationRow>) loader.getNamespace()
                        .get("conversationList");
                int resolved = -1;
                for (int i = 0; i < rows.getItems().size(); i++) {
                    if (rows.getItems().get(i).kind() == HostConversationRow.Kind.TICKET
                            && !rows.getItems().get(i).open()) {
                        resolved = i;
                        break;
                    }
                }
                assumeTrue(resolved >= 0, "mock DB needs a resolved host ticket");
                rows.getSelectionModel().select(resolved);
                boolean value = ((TextField) loader.getNamespace().get("replyField")).isDisabled()
                        && ((Button) loader.getNamespace().get("sendButton")).isDisabled();
                controller.cleanup();
                return value;
            });
            assertTrue(disabled);
        }
    }

    private static AppContext login(MockDbFixture db) throws Exception {
        AppContext context = AppContext.create(db.jdbcUrl());
        context.session().loginAs(context.userService()
                .authenticate("olivia.bennett@snoozeshare.test"));
        return context;
    }
}
