package com.snoozeshare.ui.common;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.app.SceneRouter;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class NavShellController {

    @FXML protected Label roleLabel;
    @FXML protected Label pageTitle;
    protected AppContext context;

    public void setContext(AppContext appContext) {
        context = appContext;
        if (context.session().currentRole() != null) {
            roleLabel.setText(SceneRouter.displayName(context.session().currentRole()));
        }
    }

    @FXML
    protected void logout() {
        context.session().logout();
        roleLabel.setText("Signed out");
    }
}
