package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;

class AuditServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC);

    private static Connection open() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }

    private static UUID user(Connection connection, Role role, String name) {
        UUID id = UUID.randomUUID();
        new JdbcUserRepository(connection).save(new User(id, role, name, id + "@example.com",
                AccountStatus.ACTIVE, role == Role.GUEST ? null : "CODE", Instant.parse("2026-01-01T00:00:00Z")));
        return id;
    }

    private static AuditService service(Connection connection) {
        return new AuditServiceImpl(new JdbcAuditLogRepository(connection), new JdbcUserRepository(connection),
                CLOCK);
    }

    @Test
    void recordSnapshotsNamesAndStoresStatusOnly() throws Exception {
        try (Connection connection = open()) {
            UUID agent = user(connection, Role.AGENT, "Amy Agent");
            UUID guest = user(connection, Role.GUEST, "Gus Guest");
            UUID ticket = UUID.randomUUID();
            AuditService service = service(connection);

            service.record(AuditRecord.builder(agent, AuditAction.TICKET_ASSIGNED, "Ticket", ticket)
                    .status(TicketStatus.OPEN, TicketStatus.IN_REVIEW).subject(guest).build());

            AuditLogEntry row = service.search(AuditFilter.none(), 10, 0).get(0);
            assertEquals("Amy Agent", row.actorName());
            assertEquals("Gus Guest", row.subjectName());
            assertEquals("TICKET_ASSIGNED", row.actionType());
            assertEquals("OPEN", row.beforeState());
            assertEquals("IN_REVIEW", row.afterState());
            assertNull(row.walletAdjustment());
            assertEquals(Instant.parse("2026-09-25T04:00:00Z"), row.timestamp());
        }
    }

    @Test
    void searchByNameFindsAllOfAUsersRowsEvenAfterARename() throws Exception {
        try (Connection connection = open()) {
            UUID agent = user(connection, Role.AGENT, "Amy Agent");
            UUID host = user(connection, Role.HOST, "Priya Old");
            AuditService service = service(connection);
            service.record(AuditRecord.builder(host, AuditAction.LISTING_CREATED, "Property", UUID.randomUUID())
                    .subject(host).build());
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE users SET displayName = 'Priya New' WHERE userId = '" + host + "'");
            }
            service.record(AuditRecord.builder(agent, AuditAction.ACCOUNT_SUSPENDED, "User", host)
                    .subject(host).build());

            List<AuditLogEntry> byOld = service.search(new AuditFilter("priya old", null, null, null), 10, 0);
            List<AuditLogEntry> byNew = service.search(new AuditFilter("priya new", null, null, null), 10, 0);

            assertEquals(2, byOld.size());
            assertEquals(2, byNew.size());
        }
    }

    @Test
    void idFragmentAndFullUuidMatchAndUnmatchedTextReturnsNothing() throws Exception {
        try (Connection connection = open()) {
            UUID agent = user(connection, Role.AGENT, "Amy Agent");
            UUID entity = UUID.fromString("20000000-0000-0000-0000-0000feed0001");
            AuditService service = service(connection);
            service.record(AuditRecord.builder(agent, AuditAction.LISTING_UPDATED, "Property", entity).build());

            assertEquals(1, service.search(new AuditFilter("feed0001", null, null, null), 10, 0).size());
            assertEquals(1, service.search(new AuditFilter(entity.toString(), null, null, null), 10, 0).size());
            assertEquals(1, service.search(new AuditFilter(agent.toString(), null, null, null), 10, 0).size());
            assertTrue(service.search(new AuditFilter("zzzz nobody", null, null, null), 10, 0).isEmpty());
        }
    }

    @Test
    void actionAndInclusiveDatesFilterInTheClockZone() throws Exception {
        try (Connection connection = open()) {
            UUID agent = user(connection, Role.AGENT, "Amy Agent");
            AuditService service = service(connection);
            for (String at : new String[] {"2026-09-24T23:59:59Z", "2026-09-25T10:00:00Z",
                "2026-09-26T00:00:00Z"}) {
                service.record(AuditRecord.builder(agent, AuditAction.LISTING_UPDATED, "Property",
                        UUID.randomUUID()).at(Instant.parse(at)).build());
            }
            service.record(AuditRecord.builder(agent, AuditAction.LISTING_CREATED, "Property", UUID.randomUUID())
                    .at(Instant.parse("2026-09-25T11:00:00Z")).build());
            LocalDate day = LocalDate.of(2026, 9, 25);

            assertEquals(2, service.search(new AuditFilter(null, null, day, day), 10, 0).size());
            assertEquals(1, service.search(
                    new AuditFilter(null, AuditAction.LISTING_CREATED, day, day), 10, 0).size());
            assertEquals(3, service.search(new AuditFilter(null, null, day, null), 10, 0).size());
        }
    }
}
