package com.snoozeshare.ui.common;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

/**
 * Replaces a table's centred "No content in table" placeholder with one row of text at the top of the table body,
 * so an empty table reads as a table with a single explanatory row.
 */
public final class EmptyTableRow {

    private EmptyTableRow() {
    }

    /** Installs the placeholder and returns its label so the caller can change the message later. */
    public static Label install(TableView<?> table, String text) {
        Label label = new Label(text);
        label.getStyleClass().add("table-empty-row");
        label.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(label, Pos.TOP_LEFT);
        table.setPlaceholder(label);
        return label;
    }
}
