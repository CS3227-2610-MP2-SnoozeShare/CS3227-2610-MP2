package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;

class AuditServiceTest {

    @Test
    void recordsAndQueriesAuditEntries() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            UUID actorId = UUID.randomUUID();
            new JdbcUserRepository(connection).save(new User(actorId, Role.AGENT, "Agent",
                    "agent-" + actorId + "@example.com", AccountStatus.ACTIVE,
                    "AGENT-DEMO", Instant.now()));
            AuditService service = new AuditServiceImpl(new JdbcAuditLogRepository(connection));
            UUID bookingId = UUID.randomUUID();

            service.record(actorId, "BOOKING_CONFIRMED", "BOOKING", bookingId,
                    "PENDING", "CONFIRMED");

            assertEquals(1, service.query(actorId, bookingId, "BOOKING_CONFIRMED").size());
        }
    }
}
