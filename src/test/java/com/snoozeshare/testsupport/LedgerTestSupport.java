package com.snoozeshare.testsupport;

import java.sql.Connection;
import java.time.Clock;

import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.LedgerWriter;

/** Builds a real ledger writer on a test connection. */
public final class LedgerTestSupport {

    private LedgerTestSupport() {
    }

    public static AuditService audit(Connection connection) {
        return new AuditServiceImpl(new JdbcAuditLogRepository(connection), new JdbcUserRepository(connection),
                Clock.systemUTC());
    }

    public static LedgerWriter writer(Connection connection) {
        return new LedgerWriter(new JdbcWalletRepository(connection), audit(connection));
    }

    public static LedgerWriter writer(Connection connection, AuditService audit) {
        return new LedgerWriter(new JdbcWalletRepository(connection), audit);
    }
}
