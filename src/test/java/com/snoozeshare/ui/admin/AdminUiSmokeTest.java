package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.service.DisputeSummary;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;
import com.snoozeshare.ui.admin.audit.AuditLogController;
import com.snoozeshare.ui.admin.audit.MultiSelectMenu;
import com.snoozeshare.ui.admin.tickets.DisputeDetailController;
import com.snoozeshare.ui.admin.tickets.DisputeQueueController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

class AdminUiSmokeTest {

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

    /** Runs work on the FX thread; any failure (including an AssertionError) is rethrown here. */
    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }

    private static AppContext login(MockDbFixture db, String email) throws Exception {
        AppContext context = AppContext.create(db.jdbcUrl());
        User agent = context.userService().authenticate(email);
        context.session().loginAs(agent);
        return context;
    }

    @Test
    void queueShowsAllSixMockTicketsOldestFirst(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            var rows = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-queue.fxml"));
                loader.load();
                DisputeQueueController controller = loader.getController();
                controller.setContext(context);
                @SuppressWarnings("unchecked")
                TableView<DisputeSummary> table =
                        (TableView<DisputeSummary>) loader.getNamespace().get("table");
                return table.getItems().stream().map(DisputeSummary::ticketId).toList();
            });

            assertEquals(List.of(MockIds.TICKET_4, MockIds.TICKET_2, MockIds.TICKET_1,
                    MockIds.TICKET_6, MockIds.TICKET_3, MockIds.TICKET_5), rows);
        }
    }

    @Test
    void detailEnablesResolutionOnlyForTheAssignedAgentAndOffersNoForceActions(@TempDir Path directory)
            throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "ben.alvarez@snoozeshare.test")) {
            boolean[] state = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                Node root = loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(context);
                controller.load(MockIds.TICKET_3);
                Button assign = (Button) loader.getNamespace().get("assignButton");
                Button accept = (Button) loader.getNamespace().get("acceptButton");
                Button reject = (Button) loader.getNamespace().get("rejectButton");
                Button manual = (Button) loader.getNamespace().get("manualButton");
                assertEquals("Accept \u2014 remedy guest", accept.getText());
                assertEquals("Unassign", assign.getText());
                return new boolean[] {assign.isDisabled(), accept.isDisabled(), reject.isDisabled(),
                    manual.isDisabled(), containsForce(root)};
            });

            assertFalse(state[0], "the assign button becomes an enabled Unassign for my ticket");
            assertFalse(state[1]);
            assertFalse(state[2]);
            assertFalse(state[3]);
            assertFalse(state[4], "no control mentions a force action");
        }
    }

    @Test
    void assigningAnOpenTicketEnablesTheResolutionButtonsAndUnassignReturnsItToOpen(@TempDir Path directory)
            throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            Object[] state = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(context);
                controller.load(MockIds.TICKET_2);
                Button assign = (Button) loader.getNamespace().get("assignButton");
                Button accept = (Button) loader.getNamespace().get("acceptButton");
                Button save = (Button) loader.getNamespace().get("saveNotesButton");
                boolean acceptBefore = accept.isDisabled();
                boolean saveBefore = save.isDisabled();
                String textBefore = assign.getText();
                assign.fire();
                boolean acceptAssigned = accept.isDisabled();
                boolean saveAssigned = save.isDisabled();
                String textAssigned = assign.getText();
                boolean assignEnabledAssigned = !assign.isDisabled();
                assign.fire();
                return new Object[] {acceptBefore, saveBefore, textBefore, acceptAssigned, saveAssigned,
                    textAssigned, assignEnabledAssigned, accept.isDisabled(), save.isDisabled(),
                    assign.getText(), assign.isDisabled()};
            });

            assertEquals(true, state[0], "accept disabled before assigning");
            assertEquals(true, state[1], "save disabled before assigning");
            assertEquals("Assign to me", state[2]);
            assertEquals(false, state[3], "accept enabled after assigning");
            assertEquals(false, state[4], "save enabled after assigning");
            assertEquals("Unassign", state[5]);
            assertEquals(true, state[6], "unassign is enabled");
            assertEquals(true, state[7], "accept disabled again after unassigning");
            assertEquals(true, state[8], "save disabled again after unassigning");
            assertEquals("Assign to me", state[9]);
            assertEquals(false, state[10]);
            assertEquals("OPEN", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_2));
        }
    }

    @Test
    void assignIsDisabledWhenAnotherAgentHoldsTheTicketAndNotesLoadFromTheTicket(@TempDir Path directory)
            throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext ben = login(db, "ben.alvarez@snoozeshare.test");
             AppContext amy = login(db, "amy.tanaka@snoozeshare.test")) {
            String saved = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(ben);
                controller.load(MockIds.TICKET_3);
                TextArea notes = (TextArea) loader.getNamespace().get("notesArea");
                notes.setText("first draft");
                Button saveButton = (Button) loader.getNamespace().get("saveNotesButton");
                saveButton.fire();
                return notes.getText();
            });
            Object[] other = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(amy);
                controller.load(MockIds.TICKET_3);
                Button assign = (Button) loader.getNamespace().get("assignButton");
                Button save = (Button) loader.getNamespace().get("saveNotesButton");
                TextArea notes = (TextArea) loader.getNamespace().get("notesArea");
                return new Object[] {assign.getText(), assign.isDisabled(), save.isDisabled(), notes.getText()};
            });

            assertEquals("first draft", saved);
            assertEquals("Assign to me", other[0]);
            assertEquals(true, other[1]);
            assertEquals(true, other[2]);
            assertEquals("first draft", other[3], "notes autopopulate from the ticket");
            assertEquals("first draft", db.scalarString(
                    "SELECT agentNotes FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
        }
    }

    private static boolean containsForce(Node node) {
        if (node instanceof Labeled labeled && labeled.getText() != null
                && labeled.getText().toLowerCase().contains("force")) {
            return true;
        }
        if (node instanceof ScrollPane scroll && scroll.getContent() != null
                && containsForce(scroll.getContent())) {
            return true;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsForce(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object[] auditScreen(AppContext context) throws Exception {
        FXMLLoader loader = new FXMLLoader(AdminUiSmokeTest.class.getResource(
                "/com/snoozeshare/ui/admin/audit/audit-log.fxml"));
        loader.load();
        AuditLogController controller = loader.getController();
        controller.setContext(context);
        return new Object[] {loader.getNamespace(), controller};
    }

    @SuppressWarnings("unchecked")
    private static <T> T node(java.util.Map<String, Object> namespace, String id) {
        return (T) namespace.get(id);
    }

    @SuppressWarnings("unchecked")
    private static TableView<AuditLogEntry> auditTable(java.util.Map<String, Object> namespace) {
        return (TableView<AuditLogEntry>) namespace.get("table");
    }

    @Test
    void auditLogListsSeededRowsNewestFirstAndTheFiltersNarrowThem(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            long seeded = db.scalarLong("SELECT COUNT(*) FROM audit_log");

            Object[] result = onFx(() -> {
                @SuppressWarnings("unchecked")
                var ns = (java.util.Map<String, Object>) auditScreen(context)[0];
                var table = auditTable(ns);
                int all = table.getItems().size();
                boolean newestFirst = table.getItems().get(0).timestamp()
                        .compareTo(table.getItems().get(all - 1).timestamp()) >= 0;

                AdminUiSmokeTest.<TextField>node(ns, "searchField").setText("Aria");
                AdminUiSmokeTest.<Button>node(ns, "applyButton").fire();
                var byName = List.copyOf(table.getItems());

                AdminUiSmokeTest.<Button>node(ns, "clearButton").fire();
                int afterClear = table.getItems().size();

                MultiSelectMenu select = node(ns, "actionSelect");
                select.setSelected(Set.of("AGENT_OVERRIDE"));
                AdminUiSmokeTest.<Button>node(ns, "applyButton").fire();
                var byAction = List.copyOf(table.getItems());

                select.setSelected(Set.of("AGENT_OVERRIDE", "ESCROW_HOLD"));
                AdminUiSmokeTest.<Button>node(ns, "applyButton").fire();
                var byTwoActions = List.copyOf(table.getItems());

                AdminUiSmokeTest.<Button>node(ns, "clearButton").fire();
                boolean selectCleared = select.selectedValues().isEmpty();
                AdminUiSmokeTest.<DatePicker>node(ns, "fromPicker").setValue(LocalDate.of(2026, 8, 27));
                AdminUiSmokeTest.<DatePicker>node(ns, "toPicker").setValue(LocalDate.of(2026, 8, 29));
                AdminUiSmokeTest.<Button>node(ns, "applyButton").fire();
                var byDate = List.copyOf(table.getItems());
                return new Object[] {all, newestFirst, byName, afterClear, byAction, byDate, byTwoActions,
                    selectCleared};
            });

            assertEquals((int) seeded, result[0]);
            assertTrue((Boolean) result[1], "newest row first");
            @SuppressWarnings("unchecked")
            List<AuditLogEntry> byName = (List<AuditLogEntry>) result[2];
            assertFalse(byName.isEmpty());
            assertTrue(byName.stream().allMatch(row -> MockIds.GUEST_ARIA.equals(row.actorUserId())
                    || MockIds.GUEST_ARIA.equals(row.subjectUserId())));
            assertEquals(result[0], result[3], "Clear restores every row");
            @SuppressWarnings("unchecked")
            List<AuditLogEntry> byAction = (List<AuditLogEntry>) result[4];
            assertFalse(byAction.isEmpty());
            assertTrue(byAction.stream().allMatch(row -> row.actionType().equals("AGENT_OVERRIDE")));
            @SuppressWarnings("unchecked")
            List<AuditLogEntry> byTwo = (List<AuditLogEntry>) result[6];
            assertTrue(byTwo.size() > byAction.size(), "a second action widens the rows");
            assertTrue(byTwo.stream().anyMatch(row -> row.actionType().equals("AGENT_OVERRIDE")));
            assertTrue(byTwo.stream().anyMatch(row -> row.actionType().equals("ESCROW_HOLD")));
            assertTrue(byTwo.stream().allMatch(row -> Set.of("AGENT_OVERRIDE", "ESCROW_HOLD")
                    .contains(row.actionType())));
            assertTrue((Boolean) result[7], "Clear empties the multi-select");
            @SuppressWarnings("unchecked")
            List<AuditLogEntry> byDate = (List<AuditLogEntry>) result[5];
            assertFalse(byDate.isEmpty());
            var zone = ZoneId.systemDefault();
            assertTrue(byDate.stream().allMatch(row -> {
                LocalDate day = row.timestamp().atZone(zone).toLocalDate();
                return !day.isBefore(LocalDate.of(2026, 8, 27)) && !day.isAfter(LocalDate.of(2026, 8, 29));
            }));
        }
    }

    @Test
    void auditLogRejectsAFromDateAfterTheToDate(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            String message = onFx(() -> {
                @SuppressWarnings("unchecked")
                var ns = (java.util.Map<String, Object>) auditScreen(context)[0];
                AdminUiSmokeTest.<DatePicker>node(ns, "fromPicker").setValue(LocalDate.of(2026, 9, 2));
                AdminUiSmokeTest.<DatePicker>node(ns, "toPicker").setValue(LocalDate.of(2026, 9, 1));
                AdminUiSmokeTest.<Button>node(ns, "applyButton").fire();
                return AdminUiSmokeTest.<Labeled>node(ns, "errorLabel").getText();
            });

            assertEquals("The From date must not be after the To date.", message);
        }
    }
}
