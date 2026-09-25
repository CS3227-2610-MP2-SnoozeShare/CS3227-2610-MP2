package com.snoozeshare.ui.admin.categories;

import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.TicketCategory;

import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class CategoryAdminController {

    @FXML private VBox rows;
    @FXML private Label errorLabel;

    private AppContext context;

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
        dialog.showAndWait().ifPresent(text -> {
            apply(() -> context.ticketService().renameCategory(category.categoryId(), text, me()));
        });
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
        rows.getChildren().clear();
        for (TicketCategory category : context.ticketService().listAllCategories()) {
            rows.getChildren().add(row(category));
        }
    }

    private GridPane row(TicketCategory category) {
        GridPane row = new GridPane();
        row.getStyleClass().add("agent-grid-row");
        row.getColumnConstraints().addAll(percent(50), percent(25), percent(25));
        Label label = new Label(category.label());
        label.getStyleClass().addAll("agent-cell", "agent-cell-strong");
        row.add(label, 0, 0);
        row.add(switchFor(category), 1, 0);
        Button edit = new Button("Edit");
        edit.getStyleClass().add("outline-button");
        edit.setOnAction(event -> rename(category));
        row.add(edit, 2, 0);
        GridPane.setValignment(label, VPos.CENTER);
        return row;
    }

    private ToggleButton switchFor(TicketCategory category) {
        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("agent-switch");
        Region knob = new Region();
        knob.getStyleClass().add("agent-switch-knob");
        toggle.setGraphic(knob);
        toggle.setSelected(category.active());
        toggle.setAccessibleText("Toggle active");
        toggle.setOnAction(event -> toggle(category));
        return toggle;
    }

    private static ColumnConstraints percent(double width) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPercentWidth(width);
        return constraints;
    }
}
