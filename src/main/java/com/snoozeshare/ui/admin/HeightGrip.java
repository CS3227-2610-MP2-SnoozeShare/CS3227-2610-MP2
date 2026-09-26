package com.snoozeshare.ui.admin;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

/** Turns a thin strip into a vertical drag handle that sets the height of one or more regions together. */
public final class HeightGrip {

    private HeightGrip() {
    }

    /** The new height for a drag of {@code deltaY} pixels from {@code startHeight}, kept within [min, max]. */
    public static double resolve(double startHeight, double deltaY, double min, double max) {
        return Math.max(min, Math.min(max, startHeight + deltaY));
    }

    /**
     * Makes {@code grip} an s-resize handle: dragging it changes the preferred height of every target equally.
     * The targets' own minimum and maximum heights are set to the same bounds so layout cannot fight the drag.
     */
    public static void attach(Node grip, double min, double max, Region... targets) {
        grip.setCursor(Cursor.S_RESIZE);
        for (Region target : targets) {
            target.setMinHeight(min);
            target.setMaxHeight(max);
        }
        double[] start = new double[2];
        grip.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            start[0] = event.getScreenY();
            start[1] = targets[0].getHeight() > 0 ? targets[0].getHeight() : targets[0].getPrefHeight();
            event.consume();
        });
        grip.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            double height = resolve(start[1], event.getScreenY() - start[0], min, max);
            for (Region target : targets) {
                target.setPrefHeight(height);
            }
            event.consume();
        });
    }
}
