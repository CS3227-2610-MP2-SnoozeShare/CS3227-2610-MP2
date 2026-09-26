package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.snoozeshare.ui.admin.categories.CategoryDialogController;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** The scrim over the owner window: added when a modal shows, removed however the modal closes. */
class AgentModalTest {

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

    private static Stage showOwner(BorderPane root) {
        Stage owner = new Stage();
        owner.setScene(new Scene(root, 400, 300));
        owner.show();
        return owner;
    }

    private static boolean scrimShown(Stage owner) {
        Parent root = owner.getScene().getRoot();
        return root instanceof StackPane && root.lookup(".modal-scrim") != null;
    }

    private enum Closer { CANCEL_BUTTON, ESCAPE, STAGE_CLOSE, OWNER_CLOSE }

    /** Returns {scrim over the untouched original root while open, original root restored afterwards}. */
    private static boolean[] openAndClose(Closer closer) throws Exception {
        return AdminUiSnapshotTest.onFx(() -> {
            BorderPane original = new BorderPane();
            Stage owner = showOwner(original);
            Stage modal = CategoryDialogController.createAddDialog(label -> { });
            modal.show();
            boolean during = scrimShown(owner)
                    && ((StackPane) owner.getScene().getRoot()).getChildren().get(0) == original;
            switch (closer) {
                case CANCEL_BUTTON -> ((Button) modal.getScene().getRoot().lookup("#cancelButton")).fire();
                case ESCAPE -> Event.fireEvent(modal.getScene(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                        KeyCode.ESCAPE, false, false, false, false));
                case STAGE_CLOSE -> modal.close();
                case OWNER_CLOSE -> owner.close();
                default -> throw new IllegalStateException();
            }
            boolean restored = owner.getScene().getRoot() == original && !modal.isShowing();
            owner.close();
            return new boolean[] {during, restored};
        });
    }

    @Test
    void scrimCoversTheOwnerWhileTheModalShowsAndTheOriginalRootIsRestoredOnCancel() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");

        boolean[] result = openAndClose(Closer.CANCEL_BUTTON);

        assertTrue(result[0], "scrim over the original root while open");
        assertTrue(result[1], "original root restored");
    }

    @Test
    void escapeAndWindowCloseAlsoRemoveTheScrim() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        for (Closer closer : new Closer[] {Closer.ESCAPE, Closer.STAGE_CLOSE, Closer.OWNER_CLOSE}) {
            boolean[] result = openAndClose(closer);

            assertTrue(result[0], closer + ": scrim while open");
            assertTrue(result[1], closer + ": scrim removed and root restored");
        }
    }

    @Test
    void scrimIsLightGreyAtHalfOpacity() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Color fill = AdminUiSnapshotTest.onFx(() -> {
            Stage owner = showOwner(new BorderPane());
            Stage modal = CategoryDialogController.createAddDialog(label -> { });
            modal.show();
            Region scrim = (Region) owner.getScene().getRoot().lookup(".modal-scrim");
            scrim.applyCss();
            Color color = (Color) scrim.getBackground().getFills().get(0).getFill();
            modal.close();
            assertFalse(scrimShown(owner));
            owner.close();
            return color;
        });

        assertEquals(0.5, fill.getOpacity(), 0.01);
        assertEquals(160 / 255.0, fill.getRed(), 0.01);
    }
}
