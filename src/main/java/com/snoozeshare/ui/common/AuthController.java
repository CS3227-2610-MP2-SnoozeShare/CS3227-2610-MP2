package com.snoozeshare.ui.common;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.Role;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class AuthController {

    @FXML private TextField displayNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField registrationCodeField;
    @FXML private ComboBox<Role> roleSelector;
    @FXML private Label roleLabel;
    @FXML private Label registrationCodeLabel;
    @FXML private Label displayNameLabel;
    @FXML private Label errorMessage;
    @FXML private Label successMessage;
    @FXML private Label modeLabel;
    @FXML private Button submitButton;
    @FXML private Button toggleButton;

    private AppContext context;
    private Runnable onAuthenticated = () -> { };
    private boolean registerMode;

    @FXML
    private void initialize() {
        roleSelector.getItems().setAll(Role.GUEST, Role.HOST, Role.AGENT);
        roleSelector.setValue(Role.GUEST);
        roleSelector.valueProperty().addListener((observable, oldRole, newRole) -> refreshFields());
        refreshFields();
    }

    public void setContext(AppContext appContext) {
        context = appContext;
    }

    public void setOnAuthenticated(Runnable callback) {
        onAuthenticated = callback == null ? () -> { } : callback;
    }

    @FXML
    private void toggleMode() {
        registerMode = !registerMode;
        refreshFields();
        ValidationMessage.show(errorMessage, null);
        ValidationMessage.show(successMessage, null);
    }

    @FXML
    private void submit() {
        try {
            if (registerMode) {
                context.userService().register(displayNameField.getText(), emailField.getText(),
                        roleSelector.getValue(), registrationCodeField.getText());
                registerMode = false;
                refreshFields();
                ValidationMessage.show(errorMessage, null);
                ValidationMessage.show(successMessage, "Registration successful. Please log in.");
                return;
            }
            var user = context.userService().authenticate(emailField.getText());
            context.session().loginAs(user);
            ValidationMessage.show(errorMessage, null);
            ValidationMessage.show(successMessage, null);
            onAuthenticated.run();
        } catch (RuntimeException exception) {
            ValidationMessage.show(successMessage, null);
            ValidationMessage.show(errorMessage, exception.getMessage());
        }
    }

    private void refreshFields() {
        modeLabel.setText(registerMode ? "Create account" : "Welcome back");
        submitButton.setText(registerMode ? "Register" : "Login");
        toggleButton.setText(registerMode ? "I have an account" : "Create an account");
        displayNameField.setVisible(registerMode);
        displayNameField.setManaged(registerMode);
        displayNameLabel.setVisible(registerMode);
        displayNameLabel.setManaged(registerMode);
        roleSelector.setVisible(registerMode);
        roleSelector.setManaged(registerMode);
        roleLabel.setVisible(registerMode);
        roleLabel.setManaged(registerMode);
        registrationCodeField.setVisible(registerMode && needsCode());
        registrationCodeField.setManaged(registrationCodeField.isVisible());
        registrationCodeLabel.setVisible(registrationCodeField.isVisible());
        registrationCodeLabel.setManaged(registrationCodeLabel.isVisible());
    }

    private boolean needsCode() {
        return roleSelector.getValue() == Role.HOST || roleSelector.getValue() == Role.AGENT;
    }
}
