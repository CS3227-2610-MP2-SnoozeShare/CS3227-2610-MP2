package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;

class JdbcLedgerRepositoryTest {

    private static AuditLogEntry money(UUID owner, AuditAction action, String amount, String after, UUID booking,
                                       String at) {
        return new AuditLogEntry(UUID.randomUUID(), owner, "Owner", action.name(), "WalletTransaction",
                UUID.randomUUID(), null, null, new BigDecimal(amount), null, owner, "Owner", booking, null,
                Instant.parse(at), new BigDecimal(after));
    }

    @Test
    void entriesForAWalletAreItsOwnersMoneyRowsInInsertionOrder() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            var users = new JdbcUserRepository(connection);
            var wallets = new JdbcWalletRepository(connection);
            var audit = new JdbcAuditLogRepository(connection);
            User guest = users.save(new User(UUID.randomUUID(), Role.GUEST, "G", "g@x.test", AccountStatus.ACTIVE,
                    null, Instant.parse("2026-01-01T00:00:00Z")));
            Wallet wallet = wallets.save(new Wallet(UUID.randomUUID(), guest.userId(), BigDecimal.ZERO, "SGD",
                    Instant.parse("2026-01-01T00:00:00Z")));
            UUID booking = null;
            // Inserted out of timestamp order on purpose: the ledger is insertion order.
            audit.save(money(guest.userId(), AuditAction.TOP_UP, "100", "100", booking, "2026-09-02T00:00:00Z"));
            audit.save(money(guest.userId(), AuditAction.ESCROW_HOLD, "-40", "60", booking, "2026-09-01T00:00:00Z"));
            // A status row about the same user is not a money row.
            audit.save(new AuditLogEntry(UUID.randomUUID(), guest.userId(), "G", "ACCOUNT_SUSPENDED", "User",
                    guest.userId(), "ACTIVE", "SUSPENDED", null, "r", guest.userId(), "G", null, null,
                    Instant.parse("2026-09-03T00:00:00Z"), null));

            List<WalletTransaction> entries = new JdbcLedgerRepository(connection).entriesForWallet(wallet.walletId());

            assertEquals(2, entries.size());
            assertEquals(WalletTransactionType.TOP_UP, entries.get(0).type());
            assertEquals(wallet.walletId(), entries.get(0).walletId());
            assertEquals(0, new BigDecimal("60").compareTo(entries.get(1).balanceAfter()));
            assertEquals(0, new BigDecimal("-40").compareTo(entries.get(1).amount()));
            assertEquals(guest.userId(), entries.get(1).initiatedBy());
        }
    }
}
