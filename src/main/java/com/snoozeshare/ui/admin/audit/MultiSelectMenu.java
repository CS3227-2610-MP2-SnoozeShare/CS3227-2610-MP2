package com.snoozeshare.ui.admin.audit;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.scene.control.CheckBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * A drop-down of check boxes: click options to add or remove them, click outside to close. An empty selection
 * means "everything" and shows {@link #getAllLabel()}; one selection shows its name; several show a count.
 */
public final class MultiSelectMenu extends MenuButton {

    private static final double LIST_HEIGHT = 280;
    private static final double LIST_WIDTH_PADDING = 6;
    private static final double MIN_LIST_WIDTH = 260;

    private final Map<String, CheckBox> boxes = new LinkedHashMap<>();
    private final CheckBox allBox = new CheckBox();
    private final VBox list = new VBox();
    private String allLabel = "All";

    public MultiSelectMenu() {
        getStyleClass().add("agent-multi-select");
        list.getStyleClass().add("agent-multi-list");
        allBox.getStyleClass().add("agent-multi-option");
        allBox.setMnemonicParsing(false);
        allBox.setMaxWidth(Double.MAX_VALUE);
        allBox.setOnAction(event -> {
            clearSelection();
            allBox.setSelected(true);
        });
        ScrollPane scroll = new ScrollPane(list);
        scroll.getStyleClass().add("agent-multi-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefHeight(LIST_HEIGHT);
        scroll.setMaxHeight(LIST_HEIGHT);
        scroll.prefWidthProperty().bind(widthProperty().map(width -> Math.max(width.doubleValue() - LIST_WIDTH_PADDING,
                MIN_LIST_WIDTH)));
        CustomMenuItem holder = new CustomMenuItem(scroll, false);
        holder.getStyleClass().add("agent-multi-item");
        getItems().add(holder);
        setMnemonicParsing(false);
        refresh();
    }

    public String getAllLabel() {
        return allLabel;
    }

    public void setAllLabel(String label) {
        allLabel = label;
        allBox.setText(label);
        refresh();
    }

    /** Replaces the options; the selection is emptied. */
    public void setOptions(List<String> options) {
        boxes.clear();
        list.getChildren().clear();
        list.getChildren().add(allBox);
        for (String option : options) {
            CheckBox box = new CheckBox(option);
            box.getStyleClass().add("agent-multi-option");
            box.setMnemonicParsing(false);
            box.setMaxWidth(Double.MAX_VALUE);
            box.setOnAction(event -> refresh());
            boxes.put(option, box);
            list.getChildren().add(box);
        }
        refresh();
    }

    /** The checked options, in option order; empty means all. */
    public Set<String> selectedValues() {
        Set<String> selected = new LinkedHashSet<>();
        boxes.forEach((option, box) -> {
            if (box.isSelected()) {
                selected.add(option);
            }
        });
        return selected;
    }

    /** Checks exactly the given options; values that are not options are ignored. */
    public void setSelected(Set<String> values) {
        boxes.forEach((option, box) -> box.setSelected(values.contains(option)));
        refresh();
    }

    public void clearSelection() {
        boxes.values().forEach(box -> box.setSelected(false));
        refresh();
    }

    private void refresh() {
        Set<String> selected = selectedValues();
        allBox.setSelected(selected.isEmpty());
        if (selected.isEmpty()) {
            setText(allLabel);
        } else if (selected.size() == 1) {
            setText(selected.iterator().next());
        } else {
            setText(selected.size() + " selected");
        }
    }
}
