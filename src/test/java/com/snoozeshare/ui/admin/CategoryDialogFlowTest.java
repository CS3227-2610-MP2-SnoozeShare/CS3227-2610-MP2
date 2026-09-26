package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.snoozeshare.ui.admin.categories.CategoryDialogController;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/** Drives the real Add / Edit category modal through its own buttons. */
class CategoryDialogFlowTest {

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

    private static Button button(Stage stage, String id) {
        return (Button) stage.getScene().getRoot().lookup("#" + id);
    }

    private static String text(Stage stage, String id) {
        return ((Label) stage.getScene().getRoot().lookup("#" + id)).getText();
    }

    private static void type(Stage stage, String value) {
        TextField field = (TextField) stage.getScene().getRoot().lookup("#labelField");
        field.setText(value);
    }

    @Test
    void addModalHasTheSpecifiedTextAndNoDeleteButton() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = CategoryDialogController.createAddDialog(label -> { });
            stage.show();
            Parent root = stage.getScene().getRoot();
            Object[] values = {text(stage, "titleLabel"), text(stage, "subtitleLabel"),
                button(stage, "confirmButton").getText(), button(stage, "cancelButton").getText(),
                button(stage, "deleteButton").isManaged(), root.lookup("#labelField") != null};
            stage.close();
            return values;
        });

        assertEquals("Add category", state[0]);
        assertEquals("Shown to guests when filing a dispute ticket", state[1]);
        assertEquals("Add category", state[2]);
        assertEquals("Cancel", state[3]);
        assertEquals(false, state[4]);
        assertEquals(true, state[5]);
    }

    @Test
    void addCallsBackWithTheTrimmedLabelAndClosesButBlankStaysOpenWithAnError() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        List<String> added = new ArrayList<>();
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = CategoryDialogController.createAddDialog(added::add);
            stage.show();
            type(stage, "   ");
            button(stage, "confirmButton").fire();
            Object[] values = {text(stage, "errorLabel"), stage.isShowing()};
            type(stage, "  Noise  ");
            button(stage, "confirmButton").fire();
            return new Object[] {values[0], values[1], stage.isShowing()};
        });

        assertEquals("A label is required", state[0]);
        assertEquals(true, state[1]);
        assertEquals(false, state[2]);
        assertEquals(List.of("Noise"), added);
    }

    @Test
    void aServiceFailureIsShownInsideTheCardWhichStaysOpen() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = CategoryDialogController.createAddDialog(label -> {
                throw new IllegalArgumentException("Category label already exists");
            });
            stage.show();
            type(stage, "Noise");
            button(stage, "confirmButton").fire();
            Object[] values = {text(stage, "errorLabel"), stage.isShowing()};
            stage.close();
            return values;
        });

        assertEquals("Category label already exists", state[0]);
        assertEquals(true, state[1]);
    }

    @Test
    void editIsPrefilledSkipsAnUnchangedSaveAndSavesARename() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        List<String> saved = new ArrayList<>();
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage unchanged = CategoryDialogController.createEditDialog("Cleanliness", saved::add, () -> { });
            unchanged.show();
            TextField field = (TextField) unchanged.getScene().getRoot().lookup("#labelField");
            Object[] values = {text(unchanged, "titleLabel"), button(unchanged, "confirmButton").getText(),
                field.getText(), button(unchanged, "deleteButton").isManaged()};
            button(unchanged, "confirmButton").fire();
            boolean closedWithoutSaving = !unchanged.isShowing() && saved.isEmpty();
            Stage renamed = CategoryDialogController.createEditDialog("Cleanliness", saved::add, () -> { });
            renamed.show();
            type(renamed, "Hygiene");
            button(renamed, "confirmButton").fire();
            return new Object[] {values[0], values[1], values[2], values[3], closedWithoutSaving,
                renamed.isShowing()};
        });

        assertEquals("Edit category", state[0]);
        assertEquals("Save", state[1]);
        assertEquals("Cleanliness", state[2]);
        assertEquals(true, state[3]);
        assertEquals(true, state[4]);
        assertEquals(false, state[5]);
        assertEquals(List.of("Hygiene"), saved);
    }

    @Test
    void deleteRunsImmediatelyAndAnInUseFailureStaysInlineInTheCard() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        int[] deletes = new int[1];
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage ok = CategoryDialogController.createEditDialog("Noise", label -> { }, () -> deletes[0]++);
            ok.show();
            button(ok, "deleteButton").fire();
            boolean okClosed = !ok.isShowing();
            Stage used = CategoryDialogController.createEditDialog("Cleanliness", label -> { }, () -> {
                throw new IllegalStateException("Category is in use by tickets; deactivate it instead");
            });
            used.show();
            button(used, "deleteButton").fire();
            Object[] values = {okClosed, text(used, "errorLabel"), used.isShowing()};
            used.close();
            return values;
        });

        assertEquals(1, deletes[0]);
        assertTrue((boolean) state[0], "a successful delete closes the card");
        assertEquals("Category is in use by tickets; deactivate it instead", state[1]);
        assertTrue((boolean) state[2], "a refused delete keeps the card open");
        assertFalse(deletes[0] > 1);
    }
}
