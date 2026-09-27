package com.snoozeshare.ui.host;

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
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.ui.host.messaging.HostTicketFilingController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;

class HostTicketFilingControllerTest {

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
    void configuresEligibleHostBookingsAndCategories(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService()
                    .authenticate("diego.fernandez@snoozeshare.test"));
            boolean[] values = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/host/messaging/host-ticket-filing.fxml"));
                loader.load();
                HostTicketFilingController controller = loader.getController();
                controller.configure(context, null, () -> { }, () -> { });
                @SuppressWarnings("unchecked")
                ComboBox<String> bookings = (ComboBox<String>) loader.getNamespace().get("bookingCombo");
                @SuppressWarnings("unchecked")
                ComboBox<String> categories = (ComboBox<String>) loader.getNamespace().get("categoryCombo");
                return new boolean[] { !bookings.getItems().isEmpty(), !categories.getItems().isEmpty() };
            });
            assertTrue(values[0]);
            assertTrue(values[1]);
        }
    }

    @Test
    void cancelCallbackIsAvailableWithoutSubmitting(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService()
                    .authenticate("olivia.bennett@snoozeshare.test"));
            assertFalse(onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/host/messaging/host-ticket-filing.fxml"));
                loader.load();
                return loader.getController() == null;
            }));
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
}
