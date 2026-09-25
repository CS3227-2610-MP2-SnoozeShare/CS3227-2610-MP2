package com.snoozeshare.ui.admin.categories;

import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.TicketCategory;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;

public final class CategoryAdminController {

    @FXML private TableView<TicketCategory> table;
    @FXML private Label errorLabel;

    private AppContext context;

    @FXML
    private void initialize() {
        TableColumn<TicketCategory, String> label = new TableColumn<>("Label");
        label.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().label()));
        label.setPrefWidth(340);
        TableColumn<TicketCategory, String> active = new TableColumn<>("Active");
        active.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().active() ? "Yes" : "No"));
        active.setPrefWidth(120);
        TableColumn<TicketCategory, Void> actions = new TableColumn<>("Action");
        actions.setPrefWidth(260);
        actions.setCellFactory(column -> new ActionCell());
        table.getColumns().add(label);
        table.getColumns().add(active);
        table.getColumns().add(actions);
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        refresh();
    }

    @FXML
    private void handleAdd() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add category");
        dialog.setHeaderText("New ticket category");
        dialog.setContentText("Label:");
        dialog.showAndWait().ifPresent(text -> apply(() -> context.ticketService().createCategory(text, me())));
    }

    private void rename(TicketCategory category) {
        TextInputDialog dialog = new TextInputDialog(category.label());
        dialog.setTitle("Rename category");
        dialog.setHeaderText("Rename \"" + category.label() + "\"");
        dialog.setContentText("Label:");
        dialog.showAndWait().ifPresent(text -> apply(
                () -> context.ticketService().renameCategory(category.categoryId(), text, me())));
    }

    private void toggle(TicketCategory category) {
        apply(() -> context.ticketService().setCategoryActive(category.categoryId(), !category.active(), me()));
    }

    private UUID me() {
        return context.session().currentUser().orElseThrow().userId();
    }

    private void apply(Runnable action) {
        try {
            action.run();
            errorLabel.setText("");
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
        }
        refresh();
    }

    private void refresh() {
        table.getItems().setAll(context.ticketService().listAllCategories());
    }

    private final class ActionCell extends TableCell<TicketCategory, Void> {
        private final Button rename = new Button("Rename");
        private final Button toggle = new Button();

        ActionCell() {
            rename.getStyleClass().add("outline-button");
            toggle.getStyleClass().add("outline-button");
            rename.setOnAction(event -> rename(category()));
            toggle.setOnAction(event -> CategoryAdminController.this.toggle(category()));
        }

        private TicketCategory category() {
            return getTableView().getItems().get(getIndex());
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                return;
            }
            toggle.setText(category().active() ? "Deactivate" : "Activate");
            setGraphic(new HBox(8, rename, toggle));
        }
    }
}
