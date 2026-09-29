package com.snoozeshare.ui.common.messaging;

import javafx.scene.control.Label;

/** Styles the OPEN / RESOLVED pill shown for ticket conversations, in the header and in list rows. */
public final class ConversationStatusPill {

    private ConversationStatusPill() {
    }

    /** Shows the pill for a ticket conversation and hides it (unmanaged) for any other one. */
    public static void show(Label pill, boolean ticket, boolean open) {
        if (!ticket) {
            pill.setVisible(false);
            pill.setManaged(false);
            return;
        }
        pill.setText(open ? "OPEN" : "RESOLVED");
        pill.getStyleClass().setAll("host-message-status",
                open ? "host-message-status-open" : "host-message-status-resolved");
        pill.setVisible(true);
        pill.setManaged(true);
    }
}
