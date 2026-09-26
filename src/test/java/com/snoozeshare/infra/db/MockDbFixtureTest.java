package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class MockDbFixtureTest {

    @Test
    void theCommittedMockDbSatisfiesTheLedgerInvariantAndForeignKeys(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.assertLedgerInvariant();
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM pragma_foreign_key_check"));
        }
    }

    @Test
    void mutatingTheCopyNeverTouchesTheCommittedFile(@TempDir Path directory) throws Exception {
        byte[] before = Files.readAllBytes(MockDbFixture.COMMITTED_DB);
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("UPDATE wallets SET balance = 1 WHERE walletId = ?", MockIds.WALLET_ARIA);
            assertNotEquals(0, db.walletBalance(MockIds.WALLET_ARIA).compareTo(new BigDecimal("525")));
        }
        assertArrayEquals(before, Files.readAllBytes(MockDbFixture.COMMITTED_DB));
    }
}
