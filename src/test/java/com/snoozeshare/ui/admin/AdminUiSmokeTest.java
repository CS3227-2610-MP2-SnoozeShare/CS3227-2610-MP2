package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.service.DisputeSummary;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;
import com.snoozeshare.ui.admin.tickets.DisputeDetailController;
import com.snoozeshare.ui.admin.tickets.DisputeQueueController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

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
}
