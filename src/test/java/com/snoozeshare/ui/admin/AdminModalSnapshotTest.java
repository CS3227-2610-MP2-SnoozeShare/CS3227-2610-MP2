package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;
import com.snoozeshare.ui.admin.categories.CategoryDialogController;
import com.snoozeshare.ui.admin.tickets.ResolutionDialogController;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Test-only snapshots of the REAL modal windows and of the OWNER window while a modal is open (scrim visible),
 * written to build/ui-snapshots/. It never asserts on pixels.
 */
class AdminModalSnapshotTest {

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

    private static Stage showOwner(AppContext context, String tab, UUID ticketId) throws Exception {
        return AdminUiSnapshotTest.onFx(() -> {
            AdminUiSnapshotTest.Shell shell = AdminUiSnapshotTest.loadShell(context);
            if (ticketId != null) {
                Method showDetail = AdminShellController.class.getDeclaredMethod("showDetail", UUID.class);
                showDetail.setAccessible(true);
                showDetail.invoke(shell.controller(), ticketId);
            } else {
                shell.show(tab);
            }
            Stage owner = new Stage();
            owner.setScene(new Scene(shell.root(), 1280, 800));
            owner.setX(20);
            owner.setY(20);
            owner.show();
            owner.toFront();
            owner.requestFocus();
            return owner;
        });
    }

    /** Opens the modal over the owner, waits, and writes the owner (with scrim) and the modal window PNGs. */
    private static Path[] snapshotBoth(Stage owner, Supplier<Stage> modalFactory, String name) throws Exception {
        Stage[] modal = new Stage[1];
        AdminUiSnapshotTest.onFx(() -> {
            modal[0] = modalFactory.get();
            modal[0].show();
            return null;
        });
        Thread.sleep(600);
        return AdminUiSnapshotTest.onFx(() -> {
            Parent card = modal[0].getScene().getRoot();
            card.applyCss();
            card.layout();
            Path ownerFile = AdminUiSnapshotTest.writePng(owner.getScene().snapshot(null), name + "-owner");
            Path modalFile = AdminUiSnapshotTest.writePngOver(modal[0].getScene().snapshot(null), name,
                    new java.awt.Color(0xb0, 0xc0, 0xff));
            modal[0].close();
            return new Path[] {ownerFile, modalFile};
        });
    }

    @Test
    void writesScrimAndCategoryModalSnapshots(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext amy = AppContext.create(db.jdbcUrl())) {
            amy.session().loginAs(amy.userService().authenticate("ben.alvarez@snoozeshare.test"));
            var detail = amy.disputeQueryService().detail(MockIds.TICKET_3);

            Stage detailOwner = showOwner(amy, null, MockIds.TICKET_3);
            Supplier<Stage> reject = () -> ResolutionDialogController.createDialog(detail, ResolutionMode.REJECT);
            Path[] resolution = snapshotBoth(detailOwner, reject, "agent-modal-scrim");
            AdminUiSnapshotTest.onFx(() -> {
                detailOwner.close();
                return null;
            });

            Stage owner = showOwner(amy, "showCategories", null);
            Path[] add = snapshotBoth(owner, () -> CategoryDialogController.createAddDialog(label -> { }),
                    "agent-category-add");
            Supplier<Stage> editModal = () -> {
                return CategoryDialogController.createEditDialog("Cleanliness", label -> { }, () -> { });
            };
            Path[] edit = snapshotBoth(owner, editModal, "agent-category-edit");
            Path[] inUse = snapshotBoth(owner, () -> {
                Stage stage = CategoryDialogController.createEditDialog("Cleanliness", label -> { }, () -> {
                    throw new IllegalStateException("Category is in use by tickets; deactivate it instead");
                });
                stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
                    Button delete = (Button) stage.getScene().getRoot().lookup("#deleteButton");
                    delete.fire();
                });
                return stage;
            }, "agent-category-edit-error");
            AdminUiSnapshotTest.onFx(() -> {
                owner.close();
                return null;
            });

            for (Path[] files : new Path[][] {resolution, add, edit, inUse}) {
                assertTrue(Files.size(files[0]) > 0);
                assertTrue(Files.size(files[1]) > 0);
            }
        }
    }
}
