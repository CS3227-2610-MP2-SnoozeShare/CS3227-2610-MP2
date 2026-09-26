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
    @FXML private Label noticeLabel;
    @FXML private TextField amountField;
    @FXML private Button actionButton;
    @FXML private Label statusLabel;
    @FXML private Button preset50;
    @FXML private Button preset100;
    @FXML private Button preset500;
    @FXML private Button preset1000;
    @FXML private Label fullBalanceLink;

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
                    + WalletTransactionFormatter.dollarBalanceLabel(this.currentBalance) + " SGD");
            noticeLabel.setText("Mocked top-up — no real payment gateway is charged. "
                    + "This records a TOP_UP wallet transaction and credits your balance immediately.");
            setPresetVisibility(true);
        } else {
            titleLabel.setText("Withdraw funds");
            actionButton.setText("Confirm withdraw");
            currentBalanceLabel.setText("Available to withdraw "
                    + WalletTransactionFormatter.dollarBalanceLabel(this.currentBalance) + " SGD"
                    + " — non-escrowed balance only");
            noticeLabel.setText("Mocked withdrawal — no real payout rail. You can only withdraw up to "
                    + "your available balance; funds held in escrow for pending bookings are not "
                    + "withdrawable.");
            setPresetVisibility(false);
        }

        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private void setPresetVisibility(boolean topUp) {
        for (Button preset : new Button[] {preset50, preset100, preset500, preset1000}) {
            preset.setVisible(topUp);
            preset.setManaged(topUp);
        }
        fullBalanceLink.setVisible(!topUp);
        fullBalanceLink.setManaged(!topUp);
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
