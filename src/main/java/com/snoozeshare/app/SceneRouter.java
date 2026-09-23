package com.snoozeshare.app;

import java.io.IOException;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.ui.common.AuthController;
import com.snoozeshare.ui.common.NavShellController;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SceneRouter {

    public static final String AUTH_SCENE = "/com/snoozeshare/ui/common/auth.fxml";
    public static final String GUEST_SCENE = "/com/snoozeshare/ui/guest/guest-shell.fxml";
    public static final String HOST_SCENE = "/com/snoozeshare/ui/host/host-shell.fxml";
    public static final String ADMIN_SCENE = "/com/snoozeshare/ui/admin/admin-shell.fxml";

    public String routeFor(Role role) {
        if (role == null) {
            return AUTH_SCENE;
        }
        return switch (role) {
            case GUEST -> GUEST_SCENE;
            case HOST -> HOST_SCENE;
            case AGENT -> ADMIN_SCENE;
        };
    }

    public static String displayName(Role role) {
        return role == Role.AGENT ? "Support Agent" : role.name().charAt(0)
                + role.name().substring(1).toLowerCase();
    }

    public Parent load(AppContext context) throws IOException {
        return load(context, () -> { }, () -> { });
    }

    private Parent load(AppContext context, Runnable onAuthenticated, Runnable onLoggedOut)
            throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(routeFor(
                context.session().currentRole())));
        Parent root = loader.load();
        Object controller = loader.getController();
        if (controller instanceof AuthController authController) {
            authController.setContext(context);
            authController.setOnAuthenticated(onAuthenticated);
        } else if (controller instanceof NavShellController shellController) {
            shellController.setContext(context);
            shellController.setOnLoggedOut(onLoggedOut);
        }
        return root;
    }

    public void show(Stage stage, AppContext context) throws IOException {
        Parent root = load(context, () -> {
            try {
                show(stage, context);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load authenticated page", exception);
            }
        }, () -> {
            try {
                show(stage, context);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load login page", exception);
            }
        });
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root, 1280, 800);
        } else {
            scene.setRoot(root);
        }
        String stylesheet = getClass().getResource(
                "/com/snoozeshare/ui/common/theme.css").toExternalForm();
        if (!scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
        stage.setTitle("SnoozeShare");
        stage.setMinWidth(1280);
        stage.setMinHeight(800);
        stage.setScene(scene);
        if (!stage.isShowing()) {
            stage.show();
        }
    }

}
