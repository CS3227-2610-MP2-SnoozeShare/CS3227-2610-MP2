package com.snoozeshare.ui.admin;

import static com.snoozeshare.ui.admin.AdminUiSnapshotTest.loadShell;
import static com.snoozeshare.ui.admin.AdminUiSnapshotTest.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.testsupport.MockDbFixture;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** The Accounts tab on the FX toolkit against a copy of the mock DB (17 users, 16 listed). */
class AccountGovernanceUiTest {

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

    private static Parent accounts(AppContext context) throws Exception {
        AdminUiSnapshotTest.Shell shell = loadShell(context);
        shell.show("showAccounts");
        Parent root = shell.root();
        new Scene(root, 1280, 800);
        for (int pass = 0; pass < 2; pass++) {
            root.applyCss();
            root.layout();
        }
        return root;
    }

    private static List<GridPane> rows(Parent root) {
        VBox rows = (VBox) ((ScrollPane) root.lookup(".agent-rows-scroll")).getContent();
        return rows.getChildren().stream().filter(GridPane.class::isInstance).map(GridPane.class::cast).toList();
    }

    private static String cell(GridPane row, int column) {
        Node node = row.getChildren().stream()
                .filter(child -> GridPane.getColumnIndex(child) != null && GridPane.getColumnIndex(child) == column)
                .findFirst().orElseThrow();
        return node instanceof Label label ? label.getText() : node.toString();
    }

    private static AppContext agentContext(MockDbFixture db) throws Exception {
        AppContext context = AppContext.create(db.jdbcUrl());
        context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
        return context;
    }

    @Test
    void listsEveryAccountExceptTheSystemUserWithTheSpecifiedColumns(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            Object[] state = onFx(() -> {
                Parent root = accounts(context);
                GridPane header = (GridPane) root.lookup(".agent-grid-header");
                List<String> headers = header.getChildren().stream().map(child -> ((Label) child).getText()).toList();
                List<GridPane> rows = rows(root);
                TextField searchField = (TextField) root.lookup("#searchField");
                return new Object[] {headers, rows.size(), cell(rows.get(0), 0), cell(rows.get(0), 1),
                    cell(rows.get(0), 3),
                    searchField.getPromptText(),
                    root.lookup("#searchField").getStyleClass().contains("agent-input")};
            });

            assertEquals(List.of("DISPLAY NAME", "EMAIL", "ROLE", "JOINED", "STATUS", "ACTION"), state[0]);
            assertEquals(16, state[1]);
            assertEquals("Amy Tanaka", state[2], "oldest account first");
            assertEquals("amy.tanaka@snoozeshare.test", state[3]);
            assertEquals("10 Jan 2026", state[4], "Joined as DD MMM YYYY");
            assertEquals("Search...", state[5]);
        }
    }

    @Test
    void searchFiltersLiveAcrossNameEmailRoleJoinedAndStatus(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            int[] counts = onFx(() -> {
                Parent root = accounts(context);
                TextField search = (TextField) root.lookup("#searchField");
                search.setText("suspended");
                int suspended = rows(root).size();
                search.setText("SAM O");
                int name = rows(root).size();
                search.setText("support agent");
                int agents = rows(root).size();
                search.setText("no such account");
                int none = rows(root).size();
                boolean emptyShown = ((Label) root.lookup("#emptyLabel")).isVisible();
                search.setText("");
                int all = rows(root).size();
                return new int[] {suspended, name, agents, none, emptyShown ? 1 : 0, all};
            });

            assertEquals(2, counts[0], "Kai and Sam are suspended (the System user is not listed)");
            assertEquals(1, counts[1]);
            assertEquals(3, counts[2]);
            assertEquals(0, counts[3]);
            assertEquals(1, counts[4], "an empty-state message replaces the rows");
            assertEquals(16, counts[5]);
        }
    }

    @Test
    void suspendedRowsShowTheReasonAndReactivateAgentsShowNoButton(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            Object[] state = onFx(() -> {
                Parent root = accounts(context);
                TextField search = (TextField) root.lookup("#searchField");
                search.setText("Kai");
                GridPane kai = rows(root).get(0);
                Node status = kai.getChildren().stream().filter(c -> GridPane.getColumnIndex(c) == 4).findFirst()
                        .orElseThrow();
                Node action = kai.getChildren().stream().filter(c -> GridPane.getColumnIndex(c) == 5).findFirst()
                        .orElseThrow();
                search.setText("Amy");
                GridPane amy = rows(root).get(0);
                Node amyAction = amy.getChildren().stream().filter(c -> GridPane.getColumnIndex(c) == 5).findFirst()
                        .orElseThrow();
                Button actionButton = (Button) action;
                return new Object[] {status.lookupAll(".label").stream().map(n -> ((Label) n).getText()).toList(),
                    actionButton.getText(), amyAction instanceof Button};
            });

            assertEquals(List.of("SUSPENDED", "Reason: Suspended by support agent pending review"), state[0]);
            assertEquals("Reactivate", state[1]);
            assertFalse((Boolean) state[2], "agent rows have a dash, not a button");
        }
    }

    @Test
    void suspendThenReactivateThroughTheServiceRefreshesTheRowOnTheEvent(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            var wei = context.userService().authenticate("wei.zhang@snoozeshare.test");
            Object[] state = onFx(() -> {
                Parent root = accounts(context);
                TextField search = (TextField) root.lookup("#searchField");
                search.setText("Wei");
                String before = cell(rows(root).get(0), 3);
                context.accountGovernanceService().suspend(wei.userId(),
                        context.session().currentUser().orElseThrow().userId(), "policy breach");
                return new Object[] {before, root};
            });
            // The event refresh runs through Platform.runLater; let it drain, then re-read.
            Object[] after = onFx(() -> {
                Parent root = (Parent) state[1];
                GridPane row = rows(root).get(0);
                Button action = (Button) row.getChildren().stream().filter(c -> GridPane.getColumnIndex(c) == 5)
                        .findFirst().orElseThrow();
                return new Object[] {action.getText()};
            });

            assertEquals("Reactivate", after[0]);
        }
    }

    @Test
    void theTabStripKeepsAccountsSelectedAndTheHeaderStaysFixedWhileRowsScroll(@TempDir Path directory)
            throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            Object[] state = onFx(() -> {
                Parent root = accounts(context);
                ScrollPane scroll = (ScrollPane) root.lookup(".agent-rows-scroll");
                Node header = root.lookup(".agent-grid-header");
                javafx.geometry.Bounds scrollBounds = scroll.localToScene(scroll.getBoundsInLocal());
                javafx.geometry.Bounds headerBounds = header.localToScene(header.getBoundsInLocal());
                return new Object[] {root.lookup("#accountsTab").getStyleClass().contains("agent-tab-active"),
                    scrollBounds.getMaxY() <= 800, scrollBounds.getMinY() >= headerBounds.getMaxY() - 0.5};
            });

            assertTrue((Boolean) state[0], "Accounts tab is the active tab");
            assertTrue((Boolean) state[1], "the list stays inside the window so its rows scroll");
            assertTrue((Boolean) state[2], "the header sits above the scrolling rows");
        }
    }
}
