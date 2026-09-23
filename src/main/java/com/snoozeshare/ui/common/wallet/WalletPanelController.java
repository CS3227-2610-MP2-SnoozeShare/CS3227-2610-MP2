package com.snoozeshare.ui.common.wallet;

import java.math.BigDecimal;

import com.snoozeshare.app.AppContext;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class WalletPanelController {

    @FXML private Label balanceLabel;

    public void setContext(AppContext context) {
        BigDecimal balance = context.walletService().balanceOf(
                context.session().currentUser().orElseThrow().userId());
        balanceLabel.setText("SGD " + balance);
    }
}
