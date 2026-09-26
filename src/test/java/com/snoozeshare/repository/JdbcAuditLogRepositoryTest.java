package com.snoozeshare.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;

class JdbcAuditLogRepositoryTest {

    private static Connection open() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }

    private static UUID user(Connection connection, String name) {
        UUID id = UUID.randomUUID();
        new JdbcUserRepository(connection).save(new User(id, Role.GUEST, name,
                name.replace(' ', '.').toLowerCase() + "-" + id + "@example.com", AccountStatus.ACTIVE, null,
                Instant.parse("2026-01-01T00:00:00Z")));
        return id;
    }

    private static AuditLogEntry entry(UUID actor, String actorName, String action, UUID entityId,
                                       UUID subject, String subjectName, String at) {
        return new AuditLogEntry(UUID.randomUUID(), actor, actorName, action, "Booking", entityId,
                null, "PENDING", null, null, subject, subjectName, null, null, Instant.parse(at));
    }

    @Test
    void savedEntryRoundTripsEveryColumn() throws Exception {
        try (Connection connection = open()) {
            UUID actor = user(connection, "Ann Actor");
            UUID subject = user(connection, "Sue Subject");
            var repository = new JdbcAuditLogRepository(connection);
            UUID entity = UUID.randomUUID();
            repository.save(new AuditLogEntry(UUID.randomUUID(), actor, "Ann Actor", "AGENT_OVERRIDE",
                    "WalletTransaction", entity, null, null, new BigDecimal("-12.50"), "why", subject,
                    "Sue Subject", null, null, Instant.parse("2026-09-25T04:00:00Z")));

            AuditLogEntry read = repository.search(AuditCriteria.all(), 10, 0).get(0);

            assertEquals(actor, read.actorUserId());
            assertEquals("Ann Actor", read.actorName());
            assertEquals("AGENT_OVERRIDE", read.actionType());
            assertEquals(entity, read.entityId());
            assertNull(read.beforeState());
            assertNull(read.afterState());
            assertEquals(0, new BigDecimal("-12.50").compareTo(read.walletAdjustment()));
            assertEquals("why", read.reason());
            assertEquals(subject, read.subjectUserId());
            assertEquals("Sue Subject", read.subjectName());
            assertEquals(Instant.parse("2026-09-25T04:00:00Z"), read.timestamp());
        }
    }

    @Test
    void searchIsNewestFirstAndKeepsInsertionOrderWithinOneInstant() throws Exception {
        try (Connection connection = open()) {
            UUID actor = user(connection, "Ann Actor");
            var repository = new JdbcAuditLogRepository(connection);
            repository.save(entry(actor, "Ann Actor", "TICKET_RESOLVED", UUID.randomUUID(), null, null,
                    "2026-09-25T04:00:00Z"));
            repository.save(entry(actor, "Ann Actor", "BOOKING_COMPLETED", UUID.randomUUID(), null, null,
                    "2026-09-25T04:00:00Z"));
            repository.save(entry(actor, "Ann Actor", "TOP_UP", UUID.randomUUID(), null, null,
                    "2026-09-26T04:00:00Z"));

            List<String> actions = repository.search(AuditCriteria.all(), 10, 0).stream()
                    .map(AuditLogEntry::actionType).toList();

            assertEquals(List.of("TOP_UP", "TICKET_RESOLVED", "BOOKING_COMPLETED"), actions);
        }
    }

    @Test
    void userIdSearchMatchesActorOrSubject() throws Exception {
        try (Connection connection = open()) {
            UUID ann = user(connection, "Ann Actor");
            UUID sue = user(connection, "Sue Subject");
            UUID bob = user(connection, "Bob Other");
            var repository = new JdbcAuditLogRepository(connection);
            repository.save(entry(ann, "Ann Actor", "A", UUID.randomUUID(), sue, "Sue Subject",
                    "2026-09-25T04:00:00Z"));
            repository.save(entry(bob, "Bob Other", "B", UUID.randomUUID(), bob, "Bob Other",
                    "2026-09-25T05:00:00Z"));

            var criteria = new AuditCriteria(true, Set.of(sue), null, null, null, null);

            assertEquals(List.of("A"), repository.search(criteria, 10, 0).stream()
                    .map(AuditLogEntry::actionType).toList());
        }
    }

    @Test
    void idFragmentMatchesTheEntityIdAndTextWithNoMatchReturnsNothing() throws Exception {
        try (Connection connection = open()) {
            UUID ann = user(connection, "Ann Actor");
            var repository = new JdbcAuditLogRepository(connection);
            UUID entity = UUID.fromString("20000000-0000-0000-0000-0000feed0001");
            repository.save(entry(ann, "Ann Actor", "A", entity, null, null, "2026-09-25T04:00:00Z"));
            repository.save(entry(ann, "Ann Actor", "B", UUID.randomUUID(), null, null, "2026-09-25T05:00:00Z"));

            assertEquals(1, repository.search(
                    new AuditCriteria(true, Set.of(), "feed0001", null, null, null), 10, 0).size());
            assertTrue(repository.search(
                    new AuditCriteria(true, Set.of(), null, null, null, null), 10, 0).isEmpty());
        }
    }

    @Test
    void actionAndInclusiveDateRangeNarrowTheRows() throws Exception {
        try (Connection connection = open()) {
            UUID ann = user(connection, "Ann Actor");
            var repository = new JdbcAuditLogRepository(connection);
            repository.save(entry(ann, "Ann Actor", "A", UUID.randomUUID(), null, null, "2026-09-24T23:59:59Z"));
            repository.save(entry(ann, "Ann Actor", "A", UUID.randomUUID(), null, null, "2026-09-25T00:00:00Z"));
            repository.save(entry(ann, "Ann Actor", "B", UUID.randomUUID(), null, null, "2026-09-25T12:00:00Z"));
            repository.save(entry(ann, "Ann Actor", "A", UUID.randomUUID(), null, null, "2026-09-26T00:00:00Z"));

            var day = new AuditCriteria(false, Set.of(), null, null,
                    Instant.parse("2026-09-25T00:00:00Z"), Instant.parse("2026-09-26T00:00:00Z"));
            var dayA = new AuditCriteria(false, Set.of(), null, Set.of("A"),
                    Instant.parse("2026-09-25T00:00:00Z"), Instant.parse("2026-09-26T00:00:00Z"));

            assertEquals(2, repository.search(day, 10, 0).size());
            assertEquals(1, repository.search(dayA, 10, 0).size());
        }
    }

    @Test
    void multipleActionTypesUseAnInClauseAndEmptyMeansAll() throws Exception {
        try (Connection connection = open()) {
            UUID ann = user(connection, "Ann Actor");
            var repository = new JdbcAuditLogRepository(connection);
            for (String action : new String[] {"A", "B", "C", "A"}) {
                repository.save(entry(ann, "Ann Actor", action, UUID.randomUUID(), null, null,
                        "2026-09-25T12:00:00Z"));
            }

            assertEquals(3, repository.search(criteriaFor(Set.of("A", "B")), 10, 0).size());
            assertEquals(1, repository.search(criteriaFor(Set.of("C")), 10, 0).size());
            assertEquals(4, repository.search(criteriaFor(Set.of()), 10, 0).size());
            assertEquals(4, repository.search(criteriaFor(null), 10, 0).size());
            assertTrue(repository.search(criteriaFor(Set.of("A'; DROP TABLE audit_log; --")), 10, 0).isEmpty());
            assertEquals(4, repository.search(AuditCriteria.all(), 10, 0).size());
        }
    }

    private static AuditCriteria criteriaFor(Set<String> actions) {
        return new AuditCriteria(false, Set.of(), null, actions, null, null);
    }

    @Test
    void pagingHonoursLimitAndOffset() throws Exception {
        try (Connection connection = open()) {
            UUID ann = user(connection, "Ann Actor");
            var repository = new JdbcAuditLogRepository(connection);
            for (int i = 0; i < 5; i++) {
                repository.save(entry(ann, "Ann Actor", "A" + i, UUID.randomUUID(), null, null,
                        "2026-09-2" + i + "T04:00:00Z"));
            }

            List<String> second = repository.search(AuditCriteria.all(), 2, 2).stream()
                    .map(AuditLogEntry::actionType).toList();

            assertEquals(List.of("A2", "A1"), second);
        }
    }

    @Test
    void userIdsByNameIncludeNamesRecordedInTheLogAfterARename() throws Exception {
        try (Connection connection = open()) {
            UUID priya = user(connection, "Priya Old");
            var repository = new JdbcAuditLogRepository(connection);
            repository.save(entry(priya, "Priya Old", "A", UUID.randomUUID(), null, null,
                    "2026-09-25T04:00:00Z"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE users SET displayName = 'Priya New' WHERE userId = '"
                        + priya + "'");
            }

            assertEquals(Set.of(priya), repository.findUserIdsByName("old"));
            assertEquals(Set.of(priya), repository.findUserIdsByName("PRIYA NEW"));
            assertTrue(repository.findUserIdsByName("nobody").isEmpty());
        }
    }
}
