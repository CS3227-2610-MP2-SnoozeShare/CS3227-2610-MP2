package com.snoozeshare.ui.guest.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.snoozeshare.app.AppContext;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Shared modal controller for wallet top-up and withdraw operations.
 * Parameterized by {@link Mode} to switch between the two actions.
 */
public final class WalletActionDialogController {

    /** The action mode for this dialog. */
    public enum Mode { TOP_UP, WITHDRAW }

    @FXML private Label titleLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private TextField amountField;
    @FXML private Button actionButton;
    @FXML private Label statusLabel;

    private AppContext context;
    private Mode mode;
    private Runnable onClose;

    /**
     * Configures the dialog for the specified mode.
     *
     * @param mode the action mode (TOP_UP or WITHDRAW)
     * @param context the application context
     * @param currentBalance the user's current wallet balance
     * @param onClose callback to invoke when the dialog should close
     */
    public void configure(Mode mode, AppContext context, BigDecimal currentBalance,
                          Runnable onClose) {
        this.context = context;
        this.mode = mode;
        this.onClose = onClose;

        BigDecimal display = currentBalance.setScale(2, RoundingMode.HALF_UP);
        currentBalanceLabel.setText("Current balance: SGD " + display);

        if (mode == Mode.TOP_UP) {
            titleLabel.setText("Top Up Wallet");
            actionButton.setText("Top Up");
        } else {
            titleLabel.setText("Withdraw Funds");
            actionButton.setText("Withdraw");
        }

        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    @FXML
    private void handleAction() {
        String input = amountField.getText().trim();
        if (input.isEmpty()) {
            showError("Please enter an amount.");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(input);
        } catch (NumberFormatException exception) {
            showError("Invalid amount. Please enter a number.");
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            showError("Amount must be greater than zero.");
            return;
        }

        try {
            java.util.UUID userId = context.session().currentUser().orElseThrow().userId();
            if (mode == Mode.TOP_UP) {
                context.walletService().topUp(userId, amount);
            } else {
                context.walletService().withdraw(userId, amount);
            }
            onClose.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        onClose.run();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }
}
