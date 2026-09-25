package com.snoozeshare.ui.admin.categories;

import java.io.IOException;
import java.util.function.Consumer;

import com.snoozeshare.ui.admin.AgentModal;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * The Add / Edit category modal card. The service calls are passed in as callbacks that may throw; their message
 * is shown inline in the card (and the card stays open) instead of closing it.
 */
public final class CategoryDialogController {

    private static final String FXML = "/com/snoozeshare/ui/admin/categories/category-dialog.fxml";

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private TextField labelField;
    @FXML private Label errorLabel;
    @FXML private Button deleteButton;
    @FXML private Button confirmButton;

    private String originalLabel;
    private Consumer<String> saveAction = label -> { };
    private Runnable deleteAction = () -> { };
    private Runnable closeAction = () -> { };
    private Runnable resizeAction = () -> { };

    /** Builds the Add modal (not yet shown); {@code onAdd} receives the trimmed label. */
    public static Stage createAddDialog(Consumer<String> onAdd) {
        return build(null, onAdd, null);
    }

    /** Builds the Edit modal (not yet shown); Save is skipped when the label is unchanged. */
    public static Stage createEditDialog(String currentLabel, Consumer<String> onSave, Runnable onDelete) {
        return build(currentLabel, onSave, onDelete);
    }

    public static void showAdd(Consumer<String> onAdd) {
        createAddDialog(onAdd).showAndWait();
    }

    public static void showEdit(String currentLabel, Consumer<String> onSave, Runnable onDelete) {
        createEditDialog(currentLabel, onSave, onDelete).showAndWait();
    }

    private static Stage build(String currentLabel, Consumer<String> onSave, Runnable onDelete) {
        try {
            FXMLLoader loader = new FXMLLoader(CategoryDialogController.class.getResource(FXML));
            Parent card = loader.load();
            CategoryDialogController controller = loader.getController();
            boolean edit = currentLabel != null;
            controller.configure(currentLabel, onSave, onDelete);
            AgentModal modal = AgentModal.create(card, edit ? "Edit category" : "Add category");
            controller.closeAction = modal::close;
            controller.resizeAction = modal::refit;
            return modal.stage();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load category dialog", exception);
        }
    }

    private void configure(String currentLabel, Consumer<String> onSave, Runnable onDelete) {
        originalLabel = currentLabel;
        saveAction = onSave;
        deleteAction = onDelete == null ? () -> { } : onDelete;
        boolean edit = currentLabel != null;
        errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.textProperty().addListener((observable, previous, text) -> resizeAction.run());
        titleLabel.setText(edit ? "Edit category" : "Add category");
        subtitleLabel.setText("Shown to guests when filing a dispute ticket");
        labelField.setText(edit ? currentLabel : "");
        confirmButton.setText(edit ? "Save" : "Add category");
        deleteButton.setVisible(edit);
        deleteButton.setManaged(edit);
    }

    @FXML
    private void handleCancel() {
        closeAction.run();
    }

    @FXML
    private void handleConfirm() {
        String label = labelField.getText() == null ? "" : labelField.getText().trim();
        if (label.isEmpty()) {
            errorLabel.setText("A label is required");
            return;
        }
        if (label.equals(originalLabel)) {
            closeAction.run();
            return;
        }
        attempt(() -> saveAction.accept(label));
    }

    @FXML
    private void handleDelete() {
        attempt(deleteAction);
    }

    private void attempt(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
            return;
        }
        closeAction.run();
    }
}
