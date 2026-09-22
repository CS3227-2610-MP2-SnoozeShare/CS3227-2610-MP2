package com.snoozeshare.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * JavaFX entry point. Bootstraps dependency wiring and shows the first scene.
 *
 * <p>This is a placeholder shell until {@code AppContext} and {@code SceneRouter}
 * are implemented (see the architecture proposal, section 3).
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane(new Label("SnoozeShare"));
        primaryStage.setTitle("SnoozeShare");
        primaryStage.setScene(new Scene(root, 900, 650));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
