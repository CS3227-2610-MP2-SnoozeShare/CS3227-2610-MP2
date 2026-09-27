package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

class WalletUiStructureTest {

    private static final Path WALLET_DIR = Path.of(
            "src/main/resources/com/snoozeshare/ui/common/wallet");

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Another JavaFX test owns the toolkit in this Gradle worker.
        }
    }

    @Test
    void commonDashboardUsesCommonControllerAndMockupColumns() throws Exception {
        String fxml = Files.readString(WALLET_DIR.resolve("wallet-dashboard.fxml"));
        String theme = Files.readString(Path.of(
                "src/main/resources/com/snoozeshare/ui/common/theme.css"));
        String walletCss = Files.readString(WALLET_DIR.resolve("wallet.css"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/common/wallet/WalletDashboardController.java"));

        assertTrue(fxml.contains(
                "com.snoozeshare.ui.common.wallet.WalletDashboardController"));
        for (String heading : new String[] {
            "DATE", "TYPE", "RELATED TO", "AMOUNT", "BALANCE AFTER"}) {
            assertTrue(fxml.contains(heading), heading);
        }
        assertTrue(fxml.contains("AVAILABLE BALANCE"));
        assertTrue(fxml.contains(
                "Includes $0.00 currently held in escrow for pending bookings"));
        assertTrue(!fxml.contains("Funds available for wallet actions"));
        assertTrue(!fxml.contains("No transactions yet."));
        assertTrue(fxml.contains("fx:id=\"balanceAmountLabel\""));
        assertTrue(fxml.contains("styleClass=\"wallet-balance-currency\""));
        assertTrue(fxml.contains("wallet.css"));
        assertTrue(fxml.contains("TableView"));
        assertTrue(fxml.contains("TableColumn"));
        assertTrue(!fxml.contains("transactionContainer"));
        assertTrue(walletCss.contains(".wallet-transaction-table"));
        assertTrue(walletCss.contains("-fx-cell-size: 47px"));
        assertTrue(walletCss.contains(".table-cell .transaction-type"));
        assertTrue(controller.contains("ContentDisplay.GRAPHIC_ONLY"));
        assertTrue(controller.contains("transaction-type-positive"));
        assertTrue(controller.contains("transaction-type-negative"));
        assertTrue(fxml.contains("text=\"Top up\""));
        assertTrue(fxml.contains("text=\"Withdraw\""));
        assertTrue(fxml.contains("wallet-top-up-button"));
        assertTrue(fxml.contains("wallet-withdraw-button"));
        assertTrue(!theme.contains(".agent-root .wallet-top-up-button"));
        assertTrue(!theme.contains(".agent-root .wallet-withdraw-button"));
    }

    @Test
    void commonActionDialogContainsPresetAndFullBalanceControls() throws Exception {
        String fxml = Files.readString(WALLET_DIR.resolve("wallet-action-dialog.fxml"));
        String walletCss = Files.readString(WALLET_DIR.resolve("wallet.css"));

        assertTrue(fxml.contains(
                "com.snoozeshare.ui.common.wallet.WalletActionDialogController"));
        for (String preset : new String[] {"$50", "$100", "$500", "$1000"}) {
            assertTrue(fxml.contains(preset), preset);
        }
        assertTrue(fxml.contains("Withdraw full available balance"));
        assertTrue(fxml.contains("maxHeight=\"-Infinity\""));
        assertTrue(fxml.contains("<VBox spacing=\"4\">"));
        assertTrue(fxml.contains("wallet-modal-cancel"));
        assertTrue(fxml.contains("wallet-modal-confirm"));
        assertTrue(fxml.contains("prefWidth=\"650\""));
        assertTrue(fxml.contains("fx:id=\"noticeLabel\""));
        assertTrue(walletCss.contains(".wallet-modal-cancel"));
        assertTrue(walletCss.contains(".wallet-modal-confirm"));
        assertTrue(walletCss.contains("-fx-pref-height: 76px"));
        assertTrue(walletCss.contains(".wallet-action-modal .text-field.wallet-amount-field"));
        assertTrue(walletCss.contains(".wallet-modal-scrim"));
        assertTrue(walletCss.contains("#980c1c"));
        assertTrue(walletCss.contains(".transaction-type-positive"));
        assertTrue(walletCss.contains(".transaction-type-negative"));
        assertTrue(walletCss.contains("-fx-background-color: #f0e8d8"));
        assertTrue(walletCss.contains("-fx-text-fill: #381a10"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/common/wallet/WalletActionDialogController.java"));
        assertTrue(controller.contains("dollarBalanceLabel"));
        String dashboardController = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/common/wallet/WalletDashboardController.java"));
        assertTrue(dashboardController.contains("WalletModal.create"));
        assertTrue(!dashboardController.contains("modal-overlay"));
        assertTrue(Files.exists(Path.of(
                "src/main/java/com/snoozeshare/ui/common/wallet/WalletModal.java")));
    }

    @Test
    void commonWalletFxmlLoadsThroughJavaFx() throws Exception {
        FXMLLoader.load(getClass().getResource(
                "/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml"));
        FXMLLoader.load(getClass().getResource(
                "/com/snoozeshare/ui/common/wallet/wallet-action-dialog.fxml"));
    }

    @Test
    void walletVariantsWinAgainstAgentShellButtonDefaults() throws Exception {
        StackPane wallet = (StackPane) FXMLLoader.load(getClass().getResource(
                "/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml"));
        StackPane shell = new StackPane(wallet);
        shell.getStyleClass().add("agent-root");
        Scene scene = new Scene(shell);
        scene.getStylesheets().add(getClass().getResource(
                "/com/snoozeshare/ui/admin/agent-theme.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource(
                "/com/snoozeshare/ui/common/theme.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource(
                "/com/snoozeshare/ui/common/wallet/wallet.css").toExternalForm());
        shell.applyCss();

        Button topUp = (Button) wallet.lookup(".wallet-top-up-button");
        Button withdraw = (Button) wallet.lookup(".wallet-withdraw-button");
        assertTrue(topUp.getBackground().getFills().get(0).getFill().equals(Color.web("#40680c")));
        assertTrue(withdraw.getBorder().getStrokes().get(0).getTopStroke()
                .equals(Color.web("#40680c")));
        TableView<?> table = (TableView<?>) wallet.lookup(".wallet-transaction-table");
        assertTrue(table.getColumns().size() == 5);

        Node dialog = FXMLLoader.load(getClass().getResource(
                "/com/snoozeshare/ui/common/wallet/wallet-action-dialog.fxml"));
        StackPane dialogShell = new StackPane(dialog);
        dialogShell.getStyleClass().add("agent-root");
        Scene dialogScene = new Scene(dialogShell);
        dialogScene.getStylesheets().add(getClass().getResource(
                "/com/snoozeshare/ui/admin/agent-theme.css").toExternalForm());
        dialogScene.getStylesheets().add(getClass().getResource(
                "/com/snoozeshare/ui/common/theme.css").toExternalForm());
        dialogScene.getStylesheets().add(getClass().getResource(
                "/com/snoozeshare/ui/common/wallet/wallet.css").toExternalForm());
        dialogShell.applyCss();

        Button cancel = (Button) dialog.lookup(".wallet-modal-cancel");
        Button confirm = (Button) dialog.lookup(".wallet-modal-confirm");
        assertTrue(cancel.getBorder().getStrokes().get(0).getTopStroke()
                .equals(Color.web("#40680c")));
        assertTrue(confirm.getBackground().getFills().get(0).getFill()
                .equals(Color.web("#40680c")));
    }

}
