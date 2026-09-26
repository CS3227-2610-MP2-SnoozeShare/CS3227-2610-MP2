package com.snoozeshare.ui.admin;

import static com.snoozeshare.ui.admin.AdminUiSnapshotTest.loadShell;
import static com.snoozeshare.ui.admin.AdminUiSnapshotTest.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.testsupport.MockDbFixture;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

/** Layout facts of the agent tables and the audit filter row, measured on the FX toolkit (1280x800). */
class AdminTableLayoutTest {

    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");
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
    }

    private static Parent show(AppContext context, String tab) throws Exception {
        AdminUiSnapshotTest.Shell shell = loadShell(context);
        if (tab != null) {
            shell.show(tab);
        }
        Parent root = shell.root();
        new Scene(root, 1280, 800);
        relayout(root);
        return root;
    }

    private static void relayout(Parent root) {
        for (int pass = 0; pass < 2; pass++) {
            root.applyCss();
            root.layout();
        }
    }

    private static Bounds inScene(Node node) {
        return node.localToScene(node.getBoundsInLocal());
    }

    private static void assertHeaderFixedAndRowsScroll(Parent root, int minRows) {
        TableView<?> table = (TableView<?>) root.lookup(".agent-table");
        Node header = table.lookup(".column-header-background");
        assertTrue(table.getItems().size() >= minRows, "fixture has enough rows to need scrolling");
        assertTrue(inScene(table).getMaxY() <= 800, "the table stays inside the window: its rows scroll");
        List<ScrollBar> bars = new ArrayList<>();
        table.lookupAll(".scroll-bar").forEach(node -> bars.add((ScrollBar) node));
        ScrollBar vertical = bars.stream().filter(bar -> bar.getOrientation() == Orientation.VERTICAL)
                .filter(Node::isVisible).findFirst().orElseThrow(() -> new AssertionError("no vertical scroll bar"));
        assertTrue(vertical.getWidth() > 3, "the scroll bar is visible");
        assertTrue(inScene(vertical).getMinY() >= inScene(header).getMaxY() - 0.5,
                "the scroll bar starts below the header row");
        assertTrue(inScene(header).getMinY() <= inScene(table).getMinY() + 0.5, "header sits at the top");
    }

    @Test
    void auditTableKeepsItsHeaderFixedAndScrollsOnlyTheRows(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
            onFx(() -> {
                assertHeaderFixedAndRowsScroll(show(context, "showAuditLog"), 12);
                return null;
            });
        }
    }

    @Test
    void queueTableKeepsItsHeaderFixedWhenRowsOverflow(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
            onFx(() -> {
                Parent root = show(context, null);
                @SuppressWarnings("unchecked")
                TableView<Object> table = (TableView<Object>) root.lookup(".agent-table");
                List<Object> rows = new ArrayList<>(table.getItems());
                for (int copy = 0; copy < 4; copy++) {
                    table.getItems().addAll(rows);
                }
                relayout(root);
                assertHeaderFixedAndRowsScroll(root, 12);
                AdminUiSnapshotTest.writePng(root.getScene().snapshot(null), "agent-queue-scrolling");
                return null;
            });
        }
    }

    @Test
    void categoryListKeepsItsHeaderFixedWhenRowsOverflow(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
            onFx(() -> {
                Parent root = show(context, "showCategories");
                ScrollPane scroll = (ScrollPane) root.lookup(".agent-rows-scroll");
                VBox content = (VBox) scroll.getContent();
                for (int extra = 0; extra < 15; extra++) {
                    Region filler = new Region();
                    filler.setPrefHeight(55);
                    content.getChildren().add(filler);
                }
                relayout(root);
                Node header = root.lookup(".agent-grid-header");
                ScrollBar vertical = scroll.lookupAll(".scroll-bar").stream().map(node -> (ScrollBar) node)
                        .filter(bar -> bar.getOrientation() == Orientation.VERTICAL && bar.isVisible())
                        .findFirst().orElseThrow(() -> new AssertionError("no vertical scroll bar"));
                assertTrue(inScene(scroll).getMaxY() <= 800, "the list stays inside the window");
                assertTrue(inScene(vertical).getMinY() >= inScene(header).getMaxY() - 0.5,
                        "the scroll bar starts below the header row");
                AdminUiSnapshotTest.writePng(root.getScene().snapshot(null), "agent-categories-scrolling");
                return null;
            });
        }
    }

    @Test
    void auditFilterControlsShareOneHeightAndRowsHighlightOnHoverWithoutAHandCursor(@TempDir Path directory)
            throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
            onFx(() -> {
                Parent root = show(context, "showAuditLog");
                double search = ((Control) root.lookup("#searchField")).getHeight();
                for (String id : new String[] {"#actionSelect", "#fromPicker", "#toPicker", "#applyButton",
                    "#clearButton"}) {
                    assertEquals(search, ((Control) root.lookup(id)).getHeight(), 0.6, id + " height");
                }
                TableRow<?> row = firstFilledRow(root, ".agent-audit-table");
                assertNotEquals(Cursor.HAND, row.getCursor(), "audit rows are not clickable");
                Paint rest = fill(row);
                assertTrue(rest == null || Color.TRANSPARENT.equals(rest), "no highlight at rest: " + rest);
                TableRow<?> third = (TableRow<?>) ((TableView<?>) root.lookup(".agent-audit-table"))
                        .lookupAll(".table-row-cell").stream().filter(node -> !((TableRow<?>) node).isEmpty())
                        .skip(2).findFirst().orElseThrow();
                third.pseudoClassStateChanged(HOVER, true);
                third.applyCss();
                AdminUiSnapshotTest.writePng(root.getScene().snapshot(null), "agent-audit-row-hover");
                return null;
            });
        }
    }

    @Test
    void auditAndQueueRowsShareOneHoverHighlight(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
            Paint[] hovers = onFx(() -> new Paint[] {
                hoverFill(show(context, null), ".agent-table"),
                hoverFill(show(context, "showAuditLog"), ".agent-audit-table")});

            assertTrue(hovers[0] instanceof Color color && color.getOpacity() > 0.2 && color.getOpacity() < 0.6,
                    "queue hover is a translucent highlight: " + hovers[0]);
            assertEquals(hovers[0], hovers[1], "audit and queue rows share one hover style");
        }
    }

    @Test
    void datePickerPopupIsStyledFromTheBoardComponent() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/admin/agent-theme.css"));

        assertTrue(css.contains(".date-picker-popup"), "calendar popup is styled");
        assertTrue(css.contains(".day-cell"), "day cells are styled");
        assertTrue(css.contains(".agent-button-danger"), "Clear reuses the danger outline");
    }

    private static TableRow<?> firstFilledRow(Parent root, String selector) {
        TableView<?> table = (TableView<?>) root.lookup(selector);
        return (TableRow<?>) table.lookupAll(".table-row-cell").stream()
                .filter(node -> !((TableRow<?>) node).isEmpty()).findFirst().orElseThrow();
    }

    private static Paint hoverFill(Parent root, String selector) {
        TableRow<?> row = firstFilledRow(root, selector);
        row.pseudoClassStateChanged(HOVER, true);
        row.applyCss();
        return fill(row);
    }

    private static Paint fill(Node node) {
        Background background = ((Region) node).getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        return background.getFills().get(0).getFill();
    }
}
