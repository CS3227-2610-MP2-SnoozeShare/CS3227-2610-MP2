package com.snoozeshare.ui.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.app.SceneRouter;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class NavShellController {

    @FXML protected Label roleLabel;
    @FXML protected Label pageTitle;
    @FXML protected Label pageMessage;
    @FXML protected Label walletTitle;
    @FXML protected Label walletAmount;
    protected AppContext context;
    private Runnable onLoggedOut = () -> { };

    public void setContext(AppContext appContext) {
        context = appContext;
        if (context.session().currentRole() != null) {
            roleLabel.setText(SceneRouter.displayName(context.session().currentRole()) + " Portal");
        }
        if (walletAmount != null) {
            BigDecimal balance = context.walletService().balanceOf(
                    context.session().currentUser().orElseThrow().userId());
            walletAmount.setText(formatWalletAmount(balance));
        }
    }

    public void setOnLoggedOut(Runnable callback) {
        onLoggedOut = callback == null ? () -> { } : callback;
    }

    @FXML
    protected void logout() {
        context.session().logout();
        onLoggedOut.run();
    }

    protected AppContext getContext() {
        return context;
    }

    protected void displayPage(String title, String message) {
        pageTitle.setText(title);
        pageMessage.setText(message);
    }

    private static String formatWalletAmount(BigDecimal balance) {
        return "SGD " + balance.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
