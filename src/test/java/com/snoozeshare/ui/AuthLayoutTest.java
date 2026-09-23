package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AuthLayoutTest {

    @Test
    void roleLabelIsWiredForConditionalRegisterModeVisibility() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/auth.fxml"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/common/AuthController.java"));

        assertTrue(fxml.contains("fx:id=\"roleLabel\""));
        assertTrue(fxml.contains("fx:id=\"successMessage\""));
        assertTrue(controller.contains("roleLabel.setVisible(registerMode)"));
        assertTrue(controller.contains("roleLabel.setManaged(registerMode)"));
        assertTrue(controller.contains("Registration successful. Please log in."));
        assertTrue(controller.contains("onAuthenticated.run()"));
    }

    @Test
    void sharedThemeDefinesContainedButtonsAndLargeApplicationTitle() throws Exception {
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/theme.css"));

        assertTrue(theme.contains(".button"));
        assertTrue(theme.contains("-fx-background-color: #263a5b"));
        assertTrue(theme.contains(".app-title"));
        assertTrue(theme.contains("-fx-font-weight: bold"));
    }

    @Test
    void authButtonsUseModeSpecificLabelsAndOutlinedToggleStyle() throws Exception {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/auth.fxml"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/common/AuthController.java"));
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/theme.css"));

        assertTrue(fxml.contains("fx:id=\"submitButton\""));
        assertTrue(fxml.contains("fx:id=\"toggleButton\""));
        assertTrue(fxml.contains("styleClass=\"outline-button\""));
        assertTrue(controller.contains("submitButton.setText(registerMode ? \"Register\" : \"Login\")"));
        assertTrue(controller.contains(
                "toggleButton.setText(registerMode ? \"I have an account\" : \"Create an account\")"));
        assertTrue(theme.contains(".outline-button"));
    }
}
