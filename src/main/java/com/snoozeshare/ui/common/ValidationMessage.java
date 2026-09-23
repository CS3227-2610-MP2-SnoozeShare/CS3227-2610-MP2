package com.snoozeshare.ui.common;

import javafx.scene.control.Label;

public final class ValidationMessage {

    private ValidationMessage() {
    }

    public static void show(Label label, String message) {
        label.setText(message == null ? "" : message);
        label.setVisible(message != null && !message.isBlank());
        label.setManaged(label.isVisible());
    }
}
