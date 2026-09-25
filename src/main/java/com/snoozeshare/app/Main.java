package com.snoozeshare.app;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX entry point. Bootstraps dependency wiring and shows the first scene.
 *
 * <p>The router replaces the authentication scene with the role shell after login.
 */
public class Main extends Application {

    private AppContext context;

    @Override
    public void start(Stage primaryStage) {
        try {
            String databaseUrl = System.getenv("SNOOZESHARE_DB_URL");
            context = databaseUrl == null || databaseUrl.isBlank()
                    ? AppContext.create() : AppContext.create(databaseUrl);
            context.sceneRouter().show(primaryStage, context);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start SnoozeShare", exception);
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
