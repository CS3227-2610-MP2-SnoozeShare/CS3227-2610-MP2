package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.service.AccountSummary;
import com.snoozeshare.ui.admin.accounts.SuspensionDialogController;
import com.snoozeshare.ui.admin.accounts.SuspensionDialogController.Mode;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Drives the real Suspend / Reactivate modal through its own controls. */
class SuspensionDialogFlowTest {

    private static final AccountSummary PRIYA = new AccountSummary(UUID.randomUUID(), "Priya Nair",
            "priya.nair@snoozeshare.test", Role.GUEST, Instant.parse("2026-03-05T08:00:00Z"),
            AccountStatus.ACTIVE, null);

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

    private static Button button(Stage stage, String id) {
        return (Button) stage.getScene().getRoot().lookup("#" + id);
    }

    private static String text(Stage stage, String id) {
        return ((Label) stage.getScene().getRoot().lookup("#" + id)).getText();
    }

    private static void reason(Stage stage, String value) {
        TextArea area = (TextArea) stage.getScene().getRoot().lookup("#reasonArea");
        area.setText(value);
    }

    @Test
    void suspendCardShowsNameLargeEmailBelowAndTheJoinedDateAsDdMmmYyyy() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.SUSPEND, PRIYA, reason -> { });
            stage.show();
            Region banner = (Region) stage.getScene().getRoot().lookup("#bannerBox");
            Object[] values = {text(stage, "titleLabel"), text(stage, "nameLabel"), text(stage, "emailLabel"),
                text(stage, "metaLabel").startsWith("Guest \u00b7 joined "),
                text(stage, "metaLabel").endsWith(" Mar 2026") || text(stage, "metaLabel").endsWith(" 2026"),
                banner.getStyleClass().contains("agent-banner-danger"), button(stage, "confirmButton").getText(),
                button(stage, "confirmButton").isDisabled()};
            stage.close();
            return values;
        });

        assertEquals("Suspend account", state[0]);
        assertEquals("Priya Nair", state[1]);
        assertEquals("priya.nair@snoozeshare.test", state[2]);
        assertEquals(true, state[3]);
        assertEquals(true, state[4]);
        assertEquals(true, state[5]);
        assertEquals("Confirm suspend", state[6]);
        assertEquals(true, state[7], "confirm is disabled until a reason is typed");
    }

    @Test
    void reactivateCardUsesTheSuccessGreenAndItsOwnButtonText() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.REACTIVATE, PRIYA, reason -> { });
            stage.show();
            Region banner = (Region) stage.getScene().getRoot().lookup("#bannerBox");
            Object[] values = {text(stage, "titleLabel"), banner.getStyleClass().contains("agent-banner-success"),
                banner.getBackground().getFills().get(0).getFill().toString(),
                button(stage, "confirmButton").getText()};
            stage.close();
            return values;
        });

        assertEquals("Reactivate account", state[0]);
        assertEquals(true, state[1]);
        assertEquals("0x40680cff", state[2], "#40680C from the Force Complete mock-up");
        assertEquals("Confirm reactivate", state[3]);
    }

    @Test
    void confirmPassesTheTrimmedReasonAndClosesTheCard() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        List<String> reasons = new ArrayList<>();
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.SUSPEND, PRIYA, reasons::add);
            stage.show();
            reason(stage, "   ");
            boolean blankDisabled = button(stage, "confirmButton").isDisabled();
            reason(stage, "  repeated late cancellations  ");
            boolean typedEnabled = !button(stage, "confirmButton").isDisabled();
            button(stage, "confirmButton").fire();
            return new Object[] {blankDisabled, typedEnabled, stage.isShowing()};
        });

        assertEquals(true, state[0]);
        assertEquals(true, state[1]);
        assertEquals(false, state[2]);
        assertEquals(List.of("repeated late cancellations"), reasons);
    }

    @Test
    void aServiceFailureIsShownInsideTheCardWhichStaysOpen() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.SUSPEND, PRIYA, reason -> {
                throw new IllegalStateException("Account is already suspended");
            });
            stage.show();
            reason(stage, "policy breach");
            button(stage, "confirmButton").fire();
            Object[] values = {text(stage, "errorLabel"), stage.isShowing()};
            stage.close();
            return values;
        });

        assertEquals("Account is already suspended", state[0]);
        assertTrue((Boolean) state[1]);
    }

    @Test
    void cancelClosesWithoutCallingBack() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        List<String> reasons = new ArrayList<>();
        boolean showing = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.SUSPEND, PRIYA, reasons::add);
            stage.show();
            button(stage, "cancelButton").fire();
            return stage.isShowing();
        });

        assertFalse(showing);
        assertTrue(reasons.isEmpty());
    }
}
