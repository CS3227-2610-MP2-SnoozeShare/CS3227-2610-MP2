package com.snoozeshare.ui.common.wallet;

import java.util.Objects;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/** Native application-modal stage used by Guest and Host wallet actions. */
final class WalletModal {

    private static final String WALLET_CSS =
            "/com/snoozeshare/ui/common/wallet/wallet.css";
    private static final String WRAPPER_KEY = "wallet-modal-wrapper";

    private final Stage stage;
    private final Window owner;
    private Region scrim;

    private WalletModal(Parent card, String title) {
        owner = ownerWindow();
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(title);
        Scene scene = new Scene(card);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(walletCssUrl());
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

    static WalletModal create(Parent card, String title) {
        return new WalletModal(card, title);
    }

    void showAndWait() {
        stage.showAndWait();
    }

    void close() {
        stage.close();
    }

    private void center() {
        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
        }
    }

    private static Window ownerWindow() {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage && window.isFocused())
                .findFirst()
                .orElseGet(() -> Window.getWindows().stream()
                        .filter(window -> window instanceof Stage && window.isShowing())
                        .findFirst().orElse(null));
    }

    private static String walletCssUrl() {
        return Objects.requireNonNull(WalletModal.class.getResource(WALLET_CSS)).toExternalForm();
    }

    private void addScrim() {
        Scene ownerScene = owner == null ? null : owner.getScene();
        if (ownerScene == null) {
            return;
        }
        Parent root = ownerScene.getRoot();
        StackPane wrapper;
        if (root instanceof StackPane existing
                && existing.getProperties().containsKey(WRAPPER_KEY)) {
            wrapper = existing;
        } else {
            ownerScene.setRoot(new Group());
            wrapper = new StackPane(root);
            wrapper.getProperties().put(WRAPPER_KEY, Boolean.TRUE);
            ownerScene.setRoot(wrapper);
        }
        scrim = new Region();
        scrim.getStyleClass().add("wallet-modal-scrim");
        scrim.getStylesheets().add(walletCssUrl());
        wrapper.getChildren().add(scrim);
    }

    private void removeScrim() {
        Scene ownerScene = owner == null ? null : owner.getScene();
        if (scrim == null || ownerScene == null) {
            return;
        }
        if (ownerScene.getRoot() instanceof StackPane wrapper
                && wrapper.getProperties().containsKey(WRAPPER_KEY)) {
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
