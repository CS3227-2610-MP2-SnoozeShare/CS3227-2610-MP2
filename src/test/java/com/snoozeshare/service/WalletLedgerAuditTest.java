package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.AuditCriteria;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.UserServiceImpl;
import com.snoozeshare.service.impl.WalletServiceImpl;

class WalletLedgerAuditTest {

    private static WalletService walletService(Connection connection) {
        var audit = new AuditServiceImpl(new JdbcAuditLogRepository(connection),
                new JdbcUserRepository(connection), Clock.systemUTC());
        return new WalletServiceImpl(connection, new JdbcWalletRepository(connection),
                new JdbcWalletTransactionRepository(connection), null, audit);
    }

    private static UUID guest(Connection connection) {
        var users = new JdbcUserRepository(connection);
        User user = new UserServiceImpl(connection, users, new JdbcWalletRepository(connection))
                .register("Gus Guest", "gus-" + UUID.randomUUID() + "@example.com", Role.GUEST, null);
        return user.userId();
    }

    private static List<AuditLogEntry> rows(Connection connection) {
        return new JdbcAuditLogRepository(connection).search(AuditCriteria.all(), 100, 0);
    }

    @Test
    void topUpAndWithdrawEachWriteOneSignedMoneyRow() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            UUID user = guest(connection);
            WalletService service = walletService(connection);

            var topUp = service.topUp(user, new BigDecimal("50.00"));
            service.withdraw(user, new BigDecimal("12.50"));

            List<AuditLogEntry> rows = rows(connection);
            assertEquals(2, rows.size());
            AuditLogEntry withdrawal = rows.stream().filter(r -> r.actionType().equals("WITHDRAWAL"))
                    .findFirst().orElseThrow();
            AuditLogEntry top = rows.stream().filter(r -> r.actionType().equals("TOP_UP"))
                    .findFirst().orElseThrow();
            assertEquals(0, new BigDecimal("50.00").compareTo(top.walletAdjustment()));
            assertEquals(0, new BigDecimal("-12.50").compareTo(withdrawal.walletAdjustment()));
            assertEquals("WalletTransaction", top.entityType());
            assertEquals(topUp.transactionId(), top.entityId());
            assertEquals(user, top.actorUserId());
            assertEquals(user, top.subjectUserId());
            assertNull(top.beforeState());
            assertNull(top.afterState());
        }
    }

    @Test
    void aRejectedWithdrawalLeavesNoAuditRow() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            UUID user = guest(connection);
            WalletService service = walletService(connection);

            assertThrows(IllegalArgumentException.class, () -> service.withdraw(user, new BigDecimal("5.00")));

            assertEquals(0, rows(connection).size());
        }
    }
}
