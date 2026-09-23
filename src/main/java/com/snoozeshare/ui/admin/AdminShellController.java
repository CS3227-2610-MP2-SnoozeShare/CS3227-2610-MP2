package com.snoozeshare.ui.admin;

import com.snoozeshare.ui.common.NavShellController;

import javafx.fxml.FXML;

public final class AdminShellController extends NavShellController {

    @FXML
    private void showOperations() {
        displayPage("Operations", "Monitor support operations and platform activity.");
    }

    @FXML
    private void showDisputes() {
        displayPage("Disputes", "Review open disputes and resolution work.");
    }

    @FXML
    private void showAccounts() {
        displayPage("Accounts", "Manage account status and support access.");
    }
}
