package com.snoozeshare.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX entry point. Bootstraps dependency wiring and shows the first scene.
 *
 * <p>This is a placeholder shell until {@code AppContext} and {@code SceneRouter}
 * are implemented (see the architecture proposal, section 3).
 */
public class Main extends Application {

    private AppContext context;

    @Override
    public void start(Stage primaryStage) {
        try {
            context = AppContext.create();
            SceneRouter router = context.sceneRouter();
            Scene scene = new Scene(router.load(context), 1280, 800);
            scene.getStylesheets().add(getClass().getResource(
                    "/com/snoozeshare/ui/common/theme.css").toExternalForm());
            primaryStage.setTitle("SnoozeShare");
            primaryStage.setMinWidth(1280);
            primaryStage.setMinHeight(800);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception exception) {
            throw new IllegalStateException("unable to start SnoozeShare", exception);
        }
    }

    @Override
    public void stop() throws Exception {
        if (context != null) {
            context.close();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
