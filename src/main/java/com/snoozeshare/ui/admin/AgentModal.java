package com.snoozeshare.ui.admin;

import java.util.Objects;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Shared shell for the agent modals (resolution dialogs, category dialogs): a transparent, undecorated, application
 * modal stage centred on the owner window whose only content is the card, plus a light-grey scrim laid over the
 * owner window's content for as long as the stage is showing (C26).
 */
public final class AgentModal {

    private static final String THEME = "/com/snoozeshare/ui/admin/agent-theme.css";
    private static final String WRAPPER_KEY = "agent-modal-wrapper";

    private final Stage stage;
    private final Window owner;
    private Region scrim;

    private AgentModal(Parent card, String title) {
        owner = ownerWindow();
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(title);
        Scene scene = new Scene(card);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(themeUrl());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                stage.close();
            }
        });
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setOnShowing(event -> addScrim());
        stage.setOnShown(event -> center());
        stage.setOnHidden(event -> removeScrim());
    }

    /** Builds the modal (not yet shown) around {@code card}, owned by the focused or first showing window. */
    public static AgentModal create(Parent card, String title) {
        return new AgentModal(card, title);
    }

    public Stage stage() {
        return stage;
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    public void close() {
        stage.close();
    }

    /** Re-fits the window to the card (which grew or shrank) and keeps it centred on the owner. */
    public void refit() {
        Platform.runLater(() -> {
            stage.sizeToScene();
            center();
        });
    }

    private void center() {
        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
        }
    }

    private static Window ownerWindow() {
        return Window.getWindows().stream().filter(window -> window instanceof Stage && window.isFocused())
                .findFirst()
                .orElseGet(() -> Window.getWindows().stream()
                        .filter(window -> window instanceof Stage && window.isShowing()).findFirst().orElse(null));
    }

    private static String themeUrl() {
        return Objects.requireNonNull(AgentModal.class.getResource(THEME)).toExternalForm();
    }

    /** Wraps the owner scene root in a StackPane (once) and lays the scrim over the original content. */
    private void addScrim() {
        Scene ownerScene = owner == null ? null : owner.getScene();
        if (ownerScene == null) {
            return;
        }
        Parent root = ownerScene.getRoot();
        StackPane wrapper;
        if (root instanceof StackPane existing && existing.getProperties().containsKey(WRAPPER_KEY)) {
            wrapper = existing;
        } else {
            ownerScene.setRoot(new Group());
            wrapper = new StackPane(root);
            wrapper.getProperties().put(WRAPPER_KEY, Boolean.TRUE);
            ownerScene.setRoot(wrapper);
        }
        scrim = new Region();
        scrim.getStyleClass().add("modal-scrim");
        scrim.getStylesheets().add(themeUrl());
        wrapper.getChildren().add(scrim);
    }

    /** Removes the scrim and, when nothing else needs the wrapper, restores the original scene root. */
    private void removeScrim() {
        Scene ownerScene = owner == null ? null : owner.getScene();
        if (scrim == null || ownerScene == null) {
            return;
        }
        if (ownerScene.getRoot() instanceof StackPane wrapper && wrapper.getProperties().containsKey(WRAPPER_KEY)) {
            wrapper.getChildren().remove(scrim);
            if (wrapper.getChildren().size() == 1) {
                Node original = wrapper.getChildren().get(0);
                wrapper.getChildren().clear();
                ownerScene.setRoot(new Group());
                ownerScene.setRoot((Parent) original);
            }
        }
        scrim = null;
    }
}
