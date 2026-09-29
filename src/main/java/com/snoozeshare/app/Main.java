package com.snoozeshare.app;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX entry point. Bootstraps dependency wiring and shows the first scene.
 *
 * <p>The router replaces the authentication scene with the role shell after login.
 */
public class Main extends Application {

    static final Path MOCK_DB = Path.of("db", "snoozeshare-mock.db");

    private AppContext context;

    @Override
    public void start(Stage primaryStage) {
        try {
            String databaseUrl = resolveDatabaseUrl(System.getenv("SNOOZESHARE_DB_URL"), MOCK_DB);
            context = databaseUrl == null
                    ? AppContext.create() : AppContext.create(databaseUrl);
            context.sceneRouter().show(primaryStage, context);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start SnoozeShare", exception);
        }
    }

    /**
     * Picks the database: an explicit URL wins, then the mock database if it sits in the
     * {@code db} folder, otherwise {@code null} for a fresh in-memory database.
     */
    static String resolveDatabaseUrl(String configuredUrl, Path mockDb) {
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl;
        }
        return Files.isRegularFile(mockDb) ? "jdbc:sqlite:" + mockDb : null;
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
