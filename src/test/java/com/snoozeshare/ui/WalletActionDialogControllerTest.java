package com.snoozeshare.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WalletActionDialogControllerTest {

    @Test
    void commonActionControllerDeclaresPresetAndFullBalanceInteractions() throws Exception {
        Path source = Path.of(
                "src/main/java/com/snoozeshare/ui/common/wallet/WalletActionDialogController.java");
        assertTrue(Files.exists(source));
        String controller = Files.readString(source);
        assertTrue(controller.contains("void setPresetAmount(BigDecimal"));
        assertTrue(controller.contains("void setFullAvailableBalance()"));
        assertTrue(controller.contains("context.walletService()"));
    }

    @Test
    void invalidAndInsufficientAmountsStayInsideTheDialogFlow() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/common/wallet/WalletActionDialogController.java"));
        assertTrue(controller.contains("Amount must be greater than zero."));
        assertTrue(controller.contains("catch (IllegalArgumentException | IllegalStateException"));
        assertTrue(controller.contains("showError(exception.getMessage())"));
    }
}
