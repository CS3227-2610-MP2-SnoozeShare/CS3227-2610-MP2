package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.snoozeshare.ui.admin.audit.MultiSelectMenu;

import javafx.application.Platform;

class MultiSelectMenuTest {

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

    private static MultiSelectMenu menu() {
        MultiSelectMenu menu = new MultiSelectMenu();
        menu.setAllLabel("All action types");
        menu.setOptions(List.of("A", "B", "C"));
        return menu;
    }

    @Test
    void startsEmptyAndShowsTheAllLabel() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        AdminUiSnapshotTest.onFx(() -> {
            MultiSelectMenu menu = menu();

            assertTrue(menu.selectedValues().isEmpty());
            assertEquals("All action types", menu.getText());
            return null;
        });
    }

    @Test
    void oneSelectionShowsItsNameAndSeveralShowACount() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        AdminUiSnapshotTest.onFx(() -> {
            MultiSelectMenu menu = menu();

            menu.setSelected(Set.of("B"));
            assertEquals("B", menu.getText());
            menu.setSelected(Set.of("A", "C"));
            assertEquals("2 selected", menu.getText());
            assertEquals(Set.of("A", "C"), menu.selectedValues());
            return null;
        });
    }

    @Test
    void clearingReturnsToAll() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        AdminUiSnapshotTest.onFx(() -> {
            MultiSelectMenu menu = menu();
            menu.setSelected(Set.of("A", "B"));

            menu.clearSelection();

            assertTrue(menu.selectedValues().isEmpty());
            assertEquals("All action types", menu.getText());
            return null;
        });
    }

    @Test
    void unknownValuesAreIgnored() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        AdminUiSnapshotTest.onFx(() -> {
            MultiSelectMenu menu = menu();

            menu.setSelected(Set.of("A", "ZZZ"));

            assertEquals(Set.of("A"), menu.selectedValues());
            return null;
        });
    }
}
