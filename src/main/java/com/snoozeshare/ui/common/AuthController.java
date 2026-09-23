package com.snoozeshare.ui.common;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.Role;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class AuthController {

    @FXML private TextField displayNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField registrationCodeField;
    @FXML private ComboBox<Role> roleSelector;
    @FXML private Label registrationCodeLabel;
    @FXML private Label displayNameLabel;
    @FXML private Label errorMessage;
    @FXML private Label modeLabel;

    private AppContext context;
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

    @FXML
    private void toggleMode() {
        registerMode = !registerMode;
        refreshFields();
        ValidationMessage.show(errorMessage, null);
    }

    @FXML
    private void submit() {
        try {
            var user = registerMode
                    ? context.userService().register(displayNameField.getText(), emailField.getText(),
                    roleSelector.getValue(), registrationCodeField.getText())
                    : context.userService().authenticate(emailField.getText());
            context.session().loginAs(user);
            ValidationMessage.show(errorMessage, null);
        } catch (RuntimeException exception) {
            ValidationMessage.show(errorMessage, exception.getMessage());
        }
    }

    private void refreshFields() {
        modeLabel.setText(registerMode ? "Create account" : "Welcome back");
        displayNameField.setVisible(registerMode);
        displayNameField.setManaged(registerMode);
        displayNameLabel.setVisible(registerMode);
        displayNameLabel.setManaged(registerMode);
        roleSelector.setVisible(registerMode);
        roleSelector.setManaged(registerMode);
        registrationCodeField.setVisible(registerMode && needsCode());
        registrationCodeField.setManaged(registrationCodeField.isVisible());
        registrationCodeLabel.setVisible(registrationCodeField.isVisible());
        registrationCodeLabel.setManaged(registrationCodeLabel.isVisible());
    }

    private boolean needsCode() {
        return roleSelector.getValue() == Role.HOST || roleSelector.getValue() == Role.AGENT;
    }
}
