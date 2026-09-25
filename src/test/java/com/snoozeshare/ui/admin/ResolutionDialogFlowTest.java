package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.service.DisputeDetail;
import com.snoozeshare.service.requests.ResolutionRequest;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;
import com.snoozeshare.ui.admin.tickets.ResolutionDialogController;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Drives the real resolution dialog window through the card's own Confirm and Cancel buttons. */
class ResolutionDialogFlowTest {

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

    private static Optional<ResolutionRequest> showAndDrive(DisputeDetail detail, ResolutionMode mode,
            Consumer<Parent> driver) throws Exception {
        return AdminUiSnapshotTest.onFx(() -> {
            Platform.runLater(() -> {
                Window window = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElseThrow();
                driver.accept(window.getScene().getRoot());
            });
            return ResolutionDialogController.show(detail, mode);
        });
    }

    private static void press(Parent root, String id) {
        Node button = root.lookup("#" + id);
        assertTrue(button instanceof Button, id);
        Button target = (Button) button;
        target.fire();
    }

    @Test
    void cancelButtonOnTheCardClosesTheDialogWithoutAResult(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            DisputeDetail detail = context.disputeQueryService().detail(MockIds.TICKET_3);

            Optional<ResolutionRequest> result = showAndDrive(detail, ResolutionMode.REJECT,
                    root -> press(root, "cancelButton"));

            assertTrue(result.isEmpty());
        }
    }

    @Test
    void confirmButtonNeedsAReasonThenReturnsTheRequest(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            DisputeDetail detail = context.disputeQueryService().detail(MockIds.TICKET_3);
            String[] errorAfterEmptyConfirm = new String[1];

            Optional<ResolutionRequest> result = showAndDrive(detail, ResolutionMode.REJECT, root -> {
                press(root, "confirmButton");
                Label error = (Label) root.lookup("#errorLabel");
                errorAfterEmptyConfirm[0] = error.getText();
                TextArea reason = (TextArea) root.lookup("#reasonArea");
                reason.setText("  no evidence found  ");
                press(root, "confirmButton");
            });

            assertEquals("A reason is required", errorAfterEmptyConfirm[0]);
            assertTrue(result.isPresent());
            assertEquals(ResolutionMode.REJECT, result.get().mode());
            assertEquals("no evidence found", result.get().reason());
        }
    }
}
