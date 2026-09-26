package com.snoozeshare.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.WalletTransactionType;

class AuditActionTest {

    @Test
    void everyWalletTransactionTypeHasAMatchingAuditAction() {
        for (WalletTransactionType type : WalletTransactionType.values()) {
            assertEquals(type.name(), AuditAction.forWallet(type).name());
        }
    }
}
