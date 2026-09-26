package com.snoozeshare.ui.common.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.snoozeshare.app.AppContext;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/** Shared modal controller for wallet top-up and withdrawal actions. */
public final class WalletActionDialogController {

    public enum Mode { TOP_UP, WITHDRAW }

    @FXML private Label titleLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private TextField amountField;
    @FXML private Button actionButton;
    @FXML private Label statusLabel;

    private AppContext context;
    private Mode mode;
    private BigDecimal currentBalance;
    private Runnable onClose;

    public void configure(Mode mode, AppContext context, BigDecimal currentBalance,
                          Runnable onClose) {
        this.context = context;
        this.mode = mode;
        this.currentBalance = currentBalance.setScale(2, RoundingMode.HALF_UP);
        this.onClose = onClose;

        if (mode == Mode.TOP_UP) {
            titleLabel.setText("Top up wallet");
            actionButton.setText("Confirm top up");
            currentBalanceLabel.setText("Current balance "
                    + WalletTransactionFormatter.balanceLabel(this.currentBalance));
        } else {
            titleLabel.setText("Withdraw funds");
            actionButton.setText("Confirm withdraw");
            currentBalanceLabel.setText("Available to withdraw "
                    + WalletTransactionFormatter.balanceLabel(this.currentBalance)
                    + " — non-escrowed balance only");
        }

        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    public void setPresetAmount(BigDecimal amount) {
        amountField.setText(amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        amountField.requestFocus();
        amountField.positionCaret(amountField.getText().length());
    }

    public void setFullAvailableBalance() {
        setPresetAmount(currentBalance);
    }

    @FXML
    private void handlePreset50() {
        setPresetAmount(new BigDecimal("50"));
    }

    @FXML
    private void handlePreset100() {
        setPresetAmount(new BigDecimal("100"));
    }

    @FXML
    private void handlePreset500() {
        setPresetAmount(new BigDecimal("500"));
    }

    @FXML
    private void handlePreset1000() {
        setPresetAmount(new BigDecimal("1000"));
    }

    @FXML
    private void handleFullAvailableBalance() {
        setFullAvailableBalance();
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

        if (amount.signum() <= 0) {
            showError("Amount must be greater than zero.");
            return;
        }

        try {
            UUID userId = context.session().currentUser().orElseThrow().userId();
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
