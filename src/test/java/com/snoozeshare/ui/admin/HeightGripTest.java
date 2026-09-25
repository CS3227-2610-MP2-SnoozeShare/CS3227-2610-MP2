package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

class HeightGripTest {

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

    private static MouseEvent mouse(EventType<MouseEvent> type, double screenY) {
        return new MouseEvent(type, 0, 0, 0, screenY, MouseButton.PRIMARY, 1, false, false, false, false, true,
                false, false, true, false, false, null);
    }

    @Test
    void resolveClampsToTheMinimumAndMaximum() {
        assertEquals(500, HeightGrip.resolve(460, 40, 240, 900));
        assertEquals(240, HeightGrip.resolve(460, -900, 240, 900));
        assertEquals(900, HeightGrip.resolve(460, 900, 240, 900));
    }

    @Test
    void draggingTheGripResizesEveryTargetTogetherWithinTheBounds() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        double[] heights = AdminUiSnapshotTest.onFx(() -> {
            Region left = new Region();
            Region right = new Region();
            left.setPrefHeight(460);
            right.setPrefHeight(460);
            StackPane grip = new StackPane();
            HeightGrip.attach(grip, 240, 900, left, right);
            Event.fireEvent(grip, mouse(MouseEvent.MOUSE_PRESSED, 100));
            Event.fireEvent(grip, mouse(MouseEvent.MOUSE_DRAGGED, 160));
            double grown = left.getPrefHeight();
            double grownRight = right.getPrefHeight();
            Event.fireEvent(grip, mouse(MouseEvent.MOUSE_DRAGGED, -5000));
            double floor = right.getPrefHeight();
            Event.fireEvent(grip, mouse(MouseEvent.MOUSE_DRAGGED, 5000));
            double ceiling = left.getPrefHeight();
            double cursor = grip.getCursor() == Cursor.S_RESIZE ? 1 : 0;
            return new double[] {grown, grownRight, floor, ceiling, cursor, left.getMinHeight(),
                left.getMaxHeight()};
        });

        assertEquals(520, heights[0], "grown by the 60px drag");
        assertEquals(520, heights[1], "both targets grow together");
        assertEquals(240, heights[2], "clamped to the minimum");
        assertEquals(900, heights[3], "clamped to the maximum");
        assertEquals(1, heights[4], "s-resize cursor");
        assertEquals(240, heights[5]);
        assertEquals(900, heights[6]);
    }
}
