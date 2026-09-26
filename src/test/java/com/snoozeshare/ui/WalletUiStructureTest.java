package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;

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

        assertTrue(fxml.contains(
                "com.snoozeshare.ui.common.wallet.WalletDashboardController"));
        for (String heading : new String[] {
            "DATE", "TYPE", "RELATED TO", "AMOUNT", "BALANCE AFTER"}) {
            assertTrue(fxml.contains(heading), heading);
        }
        assertTrue(fxml.contains("AVAILABLE BALANCE"));
        assertTrue(fxml.contains("fx:id=\"balanceAmountLabel\""));
        assertTrue(fxml.contains("styleClass=\"wallet-balance-currency\""));
        assertTrue(fxml.contains("ColumnConstraints"));
        assertTrue(fxml.contains("text=\"Top up\""));
        assertTrue(fxml.contains("text=\"Withdraw\""));
        assertTrue(fxml.contains("wallet-top-up-button"));
        assertTrue(fxml.contains("wallet-withdraw-button"));
    }

    @Test
    void commonActionDialogContainsPresetAndFullBalanceControls() throws Exception {
        String fxml = Files.readString(WALLET_DIR.resolve("wallet-action-dialog.fxml"));

        assertTrue(fxml.contains(
                "com.snoozeshare.ui.common.wallet.WalletActionDialogController"));
        for (String preset : new String[] {"$50", "$100", "$500", "$1000"}) {
            assertTrue(fxml.contains(preset), preset);
        }
        assertTrue(fxml.contains("Withdraw full available balance"));
        assertTrue(fxml.contains("wallet-modal-cancel"));
        assertTrue(fxml.contains("wallet-modal-confirm"));
    }

    @Test
    void commonWalletFxmlLoadsThroughJavaFx() throws Exception {
        FXMLLoader.load(getClass().getResource(
                "/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml"));
        FXMLLoader.load(getClass().getResource(
                "/com/snoozeshare/ui/common/wallet/wallet-action-dialog.fxml"));
    }

}
