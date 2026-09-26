package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdminShellInitialsTest {

    @Test
    void usesFirstAndLastWordInitials() {
        assertEquals("DK", AdminShellController.initials("Dana Kim"));
        assertEquals("AT", AdminShellController.initials("  amy   b. tanaka "));
    }

    @Test
    void singleWordUsesItsFirstTwoLetters() {
        assertEquals("DA", AdminShellController.initials("Dana"));
        assertEquals("D", AdminShellController.initials("d"));
    }

    @Test
    void fallsBackToSaWhenThereIsNoName() {
        assertEquals("SA", AdminShellController.initials(null));
        assertEquals("SA", AdminShellController.initials("   "));
    }
}
