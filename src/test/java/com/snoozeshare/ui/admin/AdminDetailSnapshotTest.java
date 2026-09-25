package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.service.DisputeDetail;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;
import com.snoozeshare.ui.admin.tickets.ResolutionDialogController;

import javafx.application.Platform;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;

/**
 * Test-only snapshots of the dispute detail screen and the three resolution dialogs, written to
 * build/ui-snapshots/ so they can be compared with the design canvas by eye. It never asserts on pixels.
 */
class AdminDetailSnapshotTest {

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

    private static AppContext login(MockDbFixture db, String email) throws Exception {
        AppContext context = AppContext.create(db.jdbcUrl());
        context.session().loginAs(context.userService().authenticate(email));
        return context;
    }

    private static Path detailSnapshot(AppContext context, UUID ticketId, String name) throws Exception {
        return AdminUiSnapshotTest.onFx(() -> {
            AdminUiSnapshotTest.Shell shell = AdminUiSnapshotTest.loadShell(context);
            Method showDetail = AdminShellController.class.getDeclaredMethod("showDetail", UUID.class);
            showDetail.setAccessible(true);
            showDetail.invoke(shell.controller(), ticketId);
            return AdminUiSnapshotTest.snapshot(shell.root(), name, 1280, 1020);
        });
    }

    /**
     * Shows the REAL modal window, waits for layout and snapshots its scene over a blue background (so any
     * extent beyond the card would be visible). Writes the stage size next to the PNG name for the report.
     */
    private static Path dialogSnapshot(DisputeDetail detail, ResolutionMode mode, String name, boolean custom)
            throws Exception {
        Stage[] holder = new Stage[1];
        AdminUiSnapshotTest.onFx(() -> {
            holder[0] = ResolutionDialogController.createDialog(detail, mode);
            holder[0].show();
            if (custom) {
                ToggleButton chip = (ToggleButton) holder[0].getScene().getRoot().lookup("#customChip");
                chip.setSelected(true);
            }
            return null;
        });
        Thread.sleep(600);
        Path file = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = holder[0];
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            Path written = AdminUiSnapshotTest.writePngOver(stage.getScene().snapshot(null), name,
                    new java.awt.Color(0xb0, 0xc0, 0xff));
            System.out.println("DIALOG " + name + " stage=" + stage.getWidth() + "x" + stage.getHeight()
                    + " scene=" + stage.getScene().getWidth() + "x" + stage.getScene().getHeight());
            stage.close();
            return written;
        });
        return file;
    }

    @Test
    void writesDetailSnapshots(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext amy = login(db, "amy.tanaka@snoozeshare.test");
             AppContext ben = login(db, "ben.alvarez@snoozeshare.test")) {
            Path open = detailSnapshot(amy, MockIds.TICKET_2, "agent-detail-open");
            UUID guestId = ben.disputeQueryService().detail(MockIds.TICKET_3).ticket().raisedByUserId();
            UUID agentId = ben.session().currentUser().orElseThrow().userId();
            ben.messageService().post(MockIds.TICKET_3, ThreadChannel.GUEST, guestId, Role.GUEST,
                    "The heating has been broken for two nights and nobody has replied to my messages.");
            ben.messageService().post(MockIds.TICKET_3, ThreadChannel.GUEST, agentId, Role.AGENT,
                    "Thanks for flagging this, I am looking into it with the host now.");
            ben.messageService().post(MockIds.TICKET_3, ThreadChannel.HOST, agentId, Role.AGENT,
                    "Could you confirm when the maintenance request was received?");
            Path assigned = detailSnapshot(ben, MockIds.TICKET_3, "agent-detail-assigned");

            assertTrue(Files.size(open) > 0);
            assertTrue(Files.size(assigned) > 0);
        }
    }

    @Test
    void writesDialogSnapshots(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext ben = login(db, "ben.alvarez@snoozeshare.test")) {
            DisputeDetail detail = ben.disputeQueryService().detail(MockIds.TICKET_3);

            DisputeDetail otherRemedy = ben.disputeQueryService().detail(MockIds.TICKET_2);
            Path accept = dialogSnapshot(otherRemedy, ResolutionMode.ACCEPT, "agent-dialog-accept", false);
            Path reject = dialogSnapshot(detail, ResolutionMode.REJECT, "agent-dialog-reject", false);
            Path manual = dialogSnapshot(detail, ResolutionMode.MANUAL, "agent-dialog-manual", false);
            Path custom = dialogSnapshot(detail, ResolutionMode.MANUAL, "agent-dialog-manual-custom", true);

            assertTrue(Files.size(accept) > 0);
            assertTrue(Files.size(reject) > 0);
            assertTrue(Files.size(manual) > 0);
            assertTrue(Files.size(custom) > 0);
        }
    }
}
