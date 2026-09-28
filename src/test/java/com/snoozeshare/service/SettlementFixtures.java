package com.snoozeshare.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcLedgerRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.DisputeSettlementServiceImpl;
import com.snoozeshare.service.impl.LedgerWriter;
import com.snoozeshare.testsupport.MockDbFixture;

final class SettlementFixtures {

    static final Instant NOW = Instant.parse("2026-09-25T04:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private SettlementFixtures() {
    }

    static DisputeSettlementServiceImpl settlement(MockDbFixture db, EventBus bus, LedgerWriter ledger) {
        var connection = db.connection();
        return settlement(db, bus, ledger, new AuditServiceImpl(new JdbcAuditLogRepository(connection),
                new JdbcUserRepository(connection), CLOCK));
    }

    /**
     * {@code ledger} writes the money rows and {@code audit} the status rows; to fail a money row, build the
     * ledger on the same failing audit service.
     */
    static DisputeSettlementServiceImpl settlement(MockDbFixture db, EventBus bus, LedgerWriter ledger,
                                                    AuditService audit) {
        var connection = db.connection();
        return new DisputeSettlementServiceImpl(connection, new JdbcTicketRepository(connection),
                new JdbcBookingRepository(connection), new JdbcPropertyRepository(connection),
                new JdbcUserRepository(connection), new JdbcWalletRepository(connection),
                ledger, new JdbcLedgerRepository(connection), audit, bus, CLOCK);
    }
}
