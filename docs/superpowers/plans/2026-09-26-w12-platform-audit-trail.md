# W12 Platform Audit Trail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure `audit_log` into one-change-per-row typed columns, audit every booking, ticket and wallet change in the same DB transaction, and ship the Agent "Audit Log" screen.

**Architecture:** Migration `V002` adds typed columns to `audit_log` (status-only `beforeState`/`afterState`, `walletAdjustment`, `reason`, `subjectUserId`, `bookingId`, `ticketId`, name snapshots). `AuditService` takes an immutable `AuditRecord` (builder enforces "one change per row") and offers a name-resolving `search`. Services write rows inside their existing `TransactionManager` lambdas. The screen is a new FXML/controller under `ui.admin.audit`, styled with the W10 `agent-theme.css`.

**Tech Stack:** Java 25, JavaFX 25 + FXML, SQLite via plain JDBC, Gradle, JUnit 5 (FX toolkit tests use the existing `Platform.startup` pattern).

**Spec:** `docs/superpowers/specs/2026-09-26-w12-platform-audit-trail-design.md` (approved; decisions C28–C32 in `PROJECT_STATE.md`).

## Global Constraints

- **Commits:** commits should be made at logical boundaries. title should be kept to 50 char with other information wrote to the description
- Checkstyle is enforced by `./gradlew build`: max line 120, imports ordered `static` / `java.*` / `org.*` / `com.*` / `javafx.*` groups separated by a blank line (copy an existing file), no star imports, no unused imports, private fields, switch *statements* need `default`, overloads adjacent.
- `ui.*` never imports `repository.*`, `infra.db.*` or `java.sql.*`. Only `repository.jdbc.*` imports `java.sql.*`.
- Monetary values are `BigDecimal`; SQLite REAL round-trips lose scale, so tests compare with `compareTo` (D4).
- Timestamps are UTC ISO-8601 ending in `Z` (`Instant.toString()`), in seed data too. Seed IDs must be valid hex UUIDs.
- The audit log is append-only: no update/delete path is added.
- Booking rows never audit an entry before the money/state change succeeds inside the same transaction, so a failure rolls everything back.
- At the end of every task run `./gradlew checkstyleMain checkstyleTest` and wrap any line over 120 characters (test code is checked too).
- Do NOT implement account-governance emission (W11), a platform wallet or folding `wallet_transactions` (W14), or ticket filing (W4).

## Review Focus

- **One change per row:** `AuditRecord` rejects status + wallet on one row (Task 1); a resolution writes exactly 4/3 rows (Task 7).
- **Atomicity:** a failing wallet write leaves no audit row (Tasks 5, 6, 7 rollback tests).
- **Search by name survives renames** (Tasks 3, 4) and never returns everything when text matches nothing (Task 3).
- **Money rows** carry the amount *actually applied* (host payout net of fee), and always `subjectUserId` = wallet owner (Tasks 5–7).
- **Mock DB parity:** `schema.sql`, `V001`+`V002` and the seeded `.db` agree (Tasks 2, 8).
- **UI is a W10 look-alike:** same classes, tab wiring, table pattern (Tasks 9–10).

## File Structure

| File | Responsibility |
|---|---|
| `domain/enums/AuditAction.java` (new) | Every audit action name; `forWallet(type)` |
| `domain/model/AuditLogEntry.java` (modify) | Persisted row, new fields |
| `service/AuditRecord.java` (new) | Write-side value + builder + one-change rule |
| `service/AuditFilter.java` (new) | Read-side filter (text, action, from, to) |
| `service/AuditService.java` (modify) | `record`, `recordWalletTransaction`, `search`, `SYSTEM_ACTOR_ID` |
| `service/impl/AuditServiceImpl.java` (modify) | Name snapshots, text → criteria |
| `service/impl/NoOpAuditService.java` (new) | For tests that do not assert audit rows |
| `repository/AuditCriteria.java` (new) | Repository-level query value |
| `repository/AuditLogRepository.java`, `repository/jdbc/JdbcAuditLogRepository.java` (modify) | save / search / findUserIdsByName |
| `repository/jdbc/support/RowMappers.java` (modify) | Map new columns |
| `resources/db/migration/V002__audit_trail.sql` (new), `infra/db/migration/MigrationRunner.java` (modify) | Schema + System user |
| `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db` (modify) | Reference DB |
| `service/impl/{Listing,Ticket,Booking,DisputeSettlement,WalletLedgerWriter,WalletService,TransactionService}Impl` (modify) | Instrumentation |
| `app/AppContext.java` (modify) | Wiring |
| `ui/admin/audit/AuditLogController.java`, `resources/.../ui/admin/audit/audit-log.fxml` (new); `AdminShellController.java`, `agent-theme.css` (modify) | Screen |

---

### Task 1: Domain vocabulary and write/read value types

**Files:**
- Create: `src/main/java/com/snoozeshare/domain/enums/AuditAction.java`
- Create: `src/main/java/com/snoozeshare/service/AuditRecord.java`
- Create: `src/main/java/com/snoozeshare/service/AuditFilter.java`
- Modify: `src/main/java/com/snoozeshare/domain/model/AuditLogEntry.java`
- Test: `src/test/java/com/snoozeshare/domain/AuditActionTest.java`, `src/test/java/com/snoozeshare/service/AuditRecordTest.java`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/snoozeshare/domain/AuditActionTest.java`:

```java
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
```

`src/test/java/com/snoozeshare/service/AuditRecordTest.java`:

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.TicketStatus;

class AuditRecordTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID ENTITY = UUID.randomUUID();

    @Test
    void aRowCannotCarryBothAStatusChangeAndAWalletAdjustment() {
        var builder = AuditRecord.builder(ACTOR, AuditAction.TICKET_RESOLVED, "Ticket", ENTITY)
                .status(TicketStatus.IN_REVIEW, TicketStatus.RESOLVED_APPROVED)
                .wallet(new BigDecimal("5.00"));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void statusEnumsAreStoredAsPlainNamesAndNullMeansNone() {
        AuditRecord record = AuditRecord.builder(ACTOR, AuditAction.BOOKING_REQUESTED, "Booking", ENTITY)
                .status(null, "PENDING").build();

        assertNull(record.beforeState());
        assertEquals("PENDING", record.afterState());
        AuditRecord fromEnum = AuditRecord.builder(ACTOR, AuditAction.TICKET_ASSIGNED, "Ticket", ENTITY)
                .status(TicketStatus.OPEN, TicketStatus.IN_REVIEW).build();
        assertEquals("OPEN", fromEnum.beforeState());
        assertEquals("IN_REVIEW", fromEnum.afterState());
    }

    @Test
    void requiresActorActionAndEntity() {
        assertThrows(IllegalArgumentException.class,
                () -> AuditRecord.builder(null, AuditAction.TOP_UP, "WalletTransaction", ENTITY).build());
        assertThrows(IllegalArgumentException.class,
                () -> AuditRecord.builder(ACTOR, null, "WalletTransaction", ENTITY).build());
        assertThrows(IllegalArgumentException.class,
                () -> AuditRecord.builder(ACTOR, AuditAction.TOP_UP, "WalletTransaction", null).build());
    }

    @Test
    void blankReasonBecomesNull() {
        AuditRecord record = AuditRecord.builder(ACTOR, AuditAction.TICKET_NOTE_SAVED, "Ticket", ENTITY)
                .reason("   ").build();

        assertNull(record.reason());
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "com.snoozeshare.domain.AuditActionTest" --tests "com.snoozeshare.service.AuditRecordTest"`
Expected: FAIL (compilation error: `AuditAction` / `AuditRecord` not found).

- [ ] **Step 3: Implement**

`src/main/java/com/snoozeshare/domain/enums/AuditAction.java`:

```java
package com.snoozeshare.domain.enums;

/** Every kind of change the audit log records; the log column stores the constant's name. */
public enum AuditAction {
    BOOKING_REQUESTED,
    BOOKING_CONFIRMED,
    BOOKING_REJECTED,
    BOOKING_CANCELLED_BY_GUEST,
    BOOKING_CANCELLED_BY_HOST,
    BOOKING_COMPLETED,
    BOOKING_FORCE_CANCELLED,
    TICKET_OPENED,
    TICKET_ASSIGNED,
    TICKET_UNASSIGNED,
    TICKET_RESOLVED,
    TICKET_NOTE_SAVED,
    TOP_UP,
    WITHDRAWAL,
    ESCROW_HOLD,
    ESCROW_REFUND,
    BOOKING_PAYOUT,
    TICKET_REMEDY,
    AGENT_OVERRIDE,
    LISTING_CREATED,
    LISTING_UPDATED,
    LISTING_STATUS_CHANGED,
    LISTING_STATUS_CASCADE,
    TICKET_CATEGORY_CREATED,
    TICKET_CATEGORY_RENAMED,
    TICKET_CATEGORY_TOGGLED,
    TICKET_CATEGORY_DELETED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_REACTIVATED;

    /** Money rows reuse the wallet transaction type's name so the two ledgers line up. */
    public static AuditAction forWallet(WalletTransactionType type) {
        return valueOf(type.name());
    }
}
```

`src/main/java/com/snoozeshare/domain/model/AuditLogEntry.java` (replace):

```java
package com.snoozeshare.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One audited change. States hold status text only; money lives in walletAdjustment; a row is never both.
 * Name fields are snapshots taken when the row was written (null on legacy rows).
 */
public record AuditLogEntry(
        UUID logId,
        UUID actorUserId,
        String actorName,
        String actionType,
        String entityType,
        UUID entityId,
        String beforeState,
        String afterState,
        BigDecimal walletAdjustment,
        String reason,
        UUID subjectUserId,
        String subjectName,
        UUID bookingId,
        UUID ticketId,
        Instant timestamp
) {
}
```

`src/main/java/com/snoozeshare/service/AuditRecord.java`:

```java
package com.snoozeshare.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.validation.DomainValidation;

/** What a service asks the audit log to write. One record is one row is one change. */
public record AuditRecord(
        UUID actorId,
        AuditAction action,
        String entityType,
        UUID entityId,
        String beforeState,
        String afterState,
        BigDecimal walletAdjustment,
        String reason,
        UUID subjectUserId,
        UUID bookingId,
        UUID ticketId,
        Instant at
) {

    public AuditRecord {
        if (actorId == null || action == null || entityId == null) {
            throw new IllegalArgumentException("Actor, action and entity are required");
        }
        DomainValidation.requireText(entityType, "entityType");
        if (walletAdjustment != null && (beforeState != null || afterState != null)) {
            throw new IllegalArgumentException(
                    "An audit row records one change: a status change or a wallet adjustment, not both");
        }
    }

    public static Builder builder(UUID actorId, AuditAction action, String entityType, UUID entityId) {
        return new Builder(actorId, action, entityType, entityId);
    }

    public static final class Builder {
        private final UUID actorId;
        private final AuditAction action;
        private final String entityType;
        private final UUID entityId;
        private String beforeState;
        private String afterState;
        private BigDecimal walletAdjustment;
        private String reason;
        private UUID subjectUserId;
        private UUID bookingId;
        private UUID ticketId;
        private Instant at;

        private Builder(UUID actorId, AuditAction action, String entityType, UUID entityId) {
            this.actorId = actorId;
            this.action = action;
            this.entityType = entityType;
            this.entityId = entityId;
        }

        /** Status text before/after; enums are stored by name, null means "none". */
        public Builder status(Object before, Object after) {
            this.beforeState = text(before);
            this.afterState = text(after);
            return this;
        }

        public Builder wallet(BigDecimal adjustment) {
            this.walletAdjustment = adjustment;
            return this;
        }

        public Builder reason(String text) {
            this.reason = text == null || text.isBlank() ? null : text.trim();
            return this;
        }

        public Builder subject(UUID userId) {
            this.subjectUserId = userId;
            return this;
        }

        public Builder booking(UUID id) {
            this.bookingId = id;
            return this;
        }

        public Builder ticket(UUID id) {
            this.ticketId = id;
            return this;
        }

        public Builder at(Instant instant) {
            this.at = instant;
            return this;
        }

        public AuditRecord build() {
            return new AuditRecord(actorId, action, entityType, entityId, beforeState, afterState,
                    walletAdjustment, reason, subjectUserId, bookingId, ticketId, at);
        }

        private static String text(Object state) {
            if (state == null) {
                return null;
            }
            return state instanceof Enum<?> value ? value.name() : state.toString();
        }
    }
}
```

`src/main/java/com/snoozeshare/service/AuditFilter.java`:

```java
package com.snoozeshare.service;

import java.time.LocalDate;

import com.snoozeshare.domain.enums.AuditAction;

/** Audit Log screen filter. Text is a user name/email or an id fragment; dates are inclusive. */
public record AuditFilter(String text, AuditAction action, LocalDate from, LocalDate to) {

    public static AuditFilter none() {
        return new AuditFilter(null, null, null, null);
    }
}
```

- [ ] **Step 4: Verify the two new tests pass**

The rest of the project does not compile yet (callers of the old `AuditLogEntry`/`AuditService`); Tasks 1–4 form one compile unit. Run only the new tests' compilation check later in Task 4. For now confirm the syntax by compiling just these sources:
Run: `./gradlew compileJava 2>&1 | grep -c "AuditRecord\|AuditAction\|AuditFilter"`
Expected: `0` lines mention these new files as the source of an error (errors elsewhere, in `RowMappers`/`AuditServiceImpl`/callers, are expected and fixed in Tasks 3–4).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/snoozeshare/domain src/main/java/com/snoozeshare/service/AuditRecord.java src/main/java/com/snoozeshare/service/AuditFilter.java src/test/java/com/snoozeshare/domain src/test/java/com/snoozeshare/service/AuditRecordTest.java
git commit -m "feat(audit): add AuditAction, AuditRecord, AuditFilter and extend AuditLogEntry"
```

---

### Task 2: Schema migration V002 and System user

**Files:**
- Create: `src/main/resources/db/migration/V002__audit_trail.sql`
- Modify: `src/main/java/com/snoozeshare/infra/db/migration/MigrationRunner.java`
- Modify: `db/schema.sql` (audit_log block)
- Modify: `src/test/java/com/snoozeshare/infra/db/SchemaParityTest.java`, `DatabaseBootstrapTest.java`, `MigrationRunnerReferenceDbTest.java`
- Test: `src/test/java/com/snoozeshare/infra/db/AuditTrailMigrationTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/snoozeshare/infra/db/AuditTrailMigrationTest.java`:

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.impl.UserServiceImpl;

class AuditTrailMigrationTest {

    @Test
    void v002AddsTheTypedColumnsAndTheSystemUserAndIsIdempotent() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);

            Set<String> columns = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA table_info(audit_log)")) {
                while (result.next()) {
                    columns.add(result.getString("name"));
                }
            }
            for (String column : new String[] {"actorName", "walletAdjustment", "reason", "subjectUserId",
                "subjectName", "bookingId", "ticketId"}) {
                assertTrue(columns.contains(column), column);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM schema_history WHERE version = 2")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void theSystemUserExistsButCanNeverSignIn() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            var users = new JdbcUserRepository(connection);

            var system = users.findById(AuditService.SYSTEM_ACTOR_ID).orElseThrow();

            assertEquals("SnoozeShare System", system.displayName());
            assertEquals(Role.AGENT, system.role());
            assertEquals(AccountStatus.SUSPENDED, system.accountStatus());
            assertThrows(IllegalStateException.class,
                    () -> new UserServiceImpl(connection, users,
                            new com.snoozeshare.repository.jdbc.JdbcWalletRepository(connection))
                            .authenticate(system.email()));
        }
    }
}
```

The test references `AuditService.SYSTEM_ACTOR_ID`, added in Task 4, so it compiles and runs from the end of Task 4 (Tasks 1–4 are one compile unit because they change `AuditLogEntry`/`AuditService` for every caller).

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.infra.db.AuditTrailMigrationTest"`
Expected: FAIL (compile errors from Task 1's unfinished callers, then after Tasks 3–4 columns are missing). If the compile unit is still red, do Step 3 first and run this test at the end of Task 4.

- [ ] **Step 3: Implement**

`src/main/resources/db/migration/V002__audit_trail.sql` (no comments, no semicolons inside strings; `MigrationRunner` splits on `;`):

```sql
ALTER TABLE audit_log ADD COLUMN actorName TEXT;
ALTER TABLE audit_log ADD COLUMN walletAdjustment REAL;
ALTER TABLE audit_log ADD COLUMN reason TEXT;
ALTER TABLE audit_log ADD COLUMN subjectUserId TEXT REFERENCES users(userId);
ALTER TABLE audit_log ADD COLUMN subjectName TEXT;
ALTER TABLE audit_log ADD COLUMN bookingId TEXT REFERENCES bookings(bookingId);
ALTER TABLE audit_log ADD COLUMN ticketId TEXT REFERENCES tickets(ticketId);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);
CREATE INDEX idx_audit_actor ON audit_log(actorUserId);
CREATE INDEX idx_audit_subject ON audit_log(subjectUserId);
CREATE INDEX idx_audit_booking ON audit_log(bookingId);
CREATE INDEX idx_audit_ticket ON audit_log(ticketId);
INSERT OR IGNORE INTO users (userId, role, displayName, email, accountStatus, registrationCode, createdAt) VALUES ('a0000000-0000-0000-0000-0000000000ff', 'AGENT', 'SnoozeShare System', 'system@snoozeshare.invalid', 'SUSPENDED', NULL, '2026-01-01T00:00:00Z');
```

`MigrationRunner.java` — replace the `migrate` body's migration section and rename `applyFoundationMigration`:

```java
    private static final int FOUNDATION_VERSION = 1;
    private static final int AUDIT_TRAIL_VERSION = 2;
```

```java
            createHistoryTable(connection);
            if (!migrationApplied(connection, FOUNDATION_VERSION)) {
                if (!tableExists(connection, "users")) {
                    applyMigrationFile(connection, "/db/migration/V001__foundation.sql");
                }
                // else: a pre-provisioned reference database (db/snoozeshare-mock.db) already has the schema.
                recordMigration(connection, FOUNDATION_VERSION);
            }
            if (!migrationApplied(connection, AUDIT_TRAIL_VERSION)) {
                if (!columnExists(connection, "audit_log", "walletAdjustment")) {
                    applyMigrationFile(connection, "/db/migration/V002__audit_trail.sql");
                }
                // else: a reference database rebuilt from db/schema.sql already has the audit columns.
                recordMigration(connection, AUDIT_TRAIL_VERSION);
            }
            connection.commit();
```

Add `columnExists` next to `tableExists`:

```java
    private static boolean columnExists(Connection connection, String table, String column)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equals(result.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
```

Rename `applyFoundationMigration(Connection connection)` to `applyMigrationFile(Connection connection, String resource)`, replacing the hard-coded path and messages:

```java
    private static void applyMigrationFile(Connection connection, String resource) throws SQLException {
        String sql;
        try (InputStream input = MigrationRunner.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new SQLException("Migration resource is missing: " + resource);
            }
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SQLException("Unable to read migration " + resource, exception);
        }
        // ... the existing split(";") loop is unchanged
    }
```

`db/schema.sql` — replace the `audit_log` table (new columns appended after `timestamp`, in the same order as V002) and add the indexes:

```sql
CREATE TABLE audit_log (
    logId                 TEXT PRIMARY KEY,
    actorUserId           TEXT NOT NULL REFERENCES users(userId),
    actionType            TEXT NOT NULL,
    entityType            TEXT NOT NULL,
    entityId              TEXT NOT NULL,
    beforeState           TEXT,
    afterState            TEXT,
    timestamp             TEXT NOT NULL,
    actorName             TEXT,
    walletAdjustment      REAL,
    reason                TEXT,
    subjectUserId         TEXT REFERENCES users(userId),
    subjectName           TEXT,
    bookingId             TEXT REFERENCES bookings(bookingId),
    ticketId              TEXT REFERENCES tickets(ticketId)
);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);
CREATE INDEX idx_audit_actor ON audit_log(actorUserId);
CREATE INDEX idx_audit_subject ON audit_log(subjectUserId);
CREATE INDEX idx_audit_booking ON audit_log(bookingId);
CREATE INDEX idx_audit_ticket ON audit_log(ticketId);
```

`SchemaParityTest.java` — after `apply(migration, ".../V001__foundation.sql");` add:

```java
            apply(migration, "src/main/resources/db/migration/V002__audit_trail.sql");
```

`DatabaseBootstrapTest.java` — the `migrationCount(...)` assertion(s) that expect `1` become `2` (run the test; change only assertions on `schema_history` row count).

`MigrationRunnerReferenceDbTest.java` — leave the version-1 assertion; add `assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM schema_history WHERE version = 2"));` and change the users count from `16L` to `17L` (the System user joins the seed in Task 8; update in that task if this test is run before then).

- [ ] **Step 4: Verify**

Run after Task 4 compiles: `./gradlew test --tests "com.snoozeshare.infra.db.*"`
Expected: PASS (except `MigrationRunnerReferenceDbTest` users count and the mock-DB adoption, which are finished in Task 8).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration src/main/java/com/snoozeshare/infra/db db/schema.sql src/test/java/com/snoozeshare/infra/db
git commit -m "feat(audit): V002 migration adds typed audit columns and the System user"
```

---

### Task 3: Repository — save, search, name resolution

**Files:**
- Create: `src/main/java/com/snoozeshare/repository/AuditCriteria.java`
- Modify: `src/main/java/com/snoozeshare/repository/AuditLogRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcAuditLogRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java` (method `auditLogEntry`)
- Test: `src/test/java/com/snoozeshare/repository/JdbcAuditLogRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
            repository.save(entry(ann, "Ann Actor", "A", UUID.randomUUID(), sue, "Sue Subject", "2026-09-25T04:00:00Z"));
            repository.save(entry(bob, "Bob Other", "B", UUID.randomUUID(), bob, "Bob Other", "2026-09-25T05:00:00Z"));

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
            var dayA = new AuditCriteria(false, Set.of(), null, "A",
                    Instant.parse("2026-09-25T00:00:00Z"), Instant.parse("2026-09-26T00:00:00Z"));

            assertEquals(2, repository.search(day, 10, 0).size());
            assertEquals(1, repository.search(dayA, 10, 0).size());
        }
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
            repository.save(entry(priya, "Priya Old", "A", UUID.randomUUID(), null, null, "2026-09-25T04:00:00Z"));
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.repository.JdbcAuditLogRepositoryTest"`
Expected: FAIL (compile: `AuditCriteria`, `search`, `findUserIdsByName` missing).

- [ ] **Step 3: Implement**

`src/main/java/com/snoozeshare/repository/AuditCriteria.java`:

```java
package com.snoozeshare.repository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Repository-level audit query. When {@code textGiven}, a row must involve one of {@code userIds}
 * (as actor or subject) or contain {@code idFragment} in an id column; with neither, nothing matches.
 * {@code from} is inclusive, {@code toExclusive} exclusive (whole seconds).
 */
public record AuditCriteria(boolean textGiven, Set<UUID> userIds, String idFragment, String actionType,
                            Instant from, Instant toExclusive) {

    public AuditCriteria {
        userIds = userIds == null ? Set.of() : Set.copyOf(userIds);
    }

    public static AuditCriteria all() {
        return new AuditCriteria(false, Set.of(), null, null, null, null);
    }

    public static AuditCriteria forAction(String actionType) {
        return new AuditCriteria(false, Set.of(), null, actionType, null, null);
    }
}
```

`AuditLogRepository.java`:

```java
package com.snoozeshare.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;

public interface AuditLogRepository {
    AuditLogEntry save(AuditLogEntry entry);

    /** Newest first; rows written at the same instant keep insertion order. */
    List<AuditLogEntry> search(AuditCriteria criteria, int limit, int offset);

    /** Ids of users whose current name/email, or a name recorded in the log, contains the fragment. */
    Set<UUID> findUserIdsByName(String fragment);
}
```

`JdbcAuditLogRepository.java` (replace body; keep package/imports style):

```java
package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.repository.AuditCriteria;
import com.snoozeshare.repository.AuditLogRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcAuditLogRepository implements AuditLogRepository {

    private static final List<String> ID_COLUMNS = List.of("entityId", "bookingId", "ticketId",
            "actorUserId", "subjectUserId");

    private final Connection connection;

    public JdbcAuditLogRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, "
                        + "beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, "
                        + "bookingId, ticketId, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(entry.logId()));
            statement.setString(2, JdbcCodecs.uuid(entry.actorUserId()));
            statement.setString(3, entry.actorName());
            statement.setString(4, entry.actionType());
            statement.setString(5, entry.entityType());
            statement.setString(6, JdbcCodecs.uuid(entry.entityId()));
            statement.setString(7, entry.beforeState());
            statement.setString(8, entry.afterState());
            statement.setString(9, JdbcCodecs.decimal(entry.walletAdjustment()));
            statement.setString(10, entry.reason());
            statement.setString(11, JdbcCodecs.uuid(entry.subjectUserId()));
            statement.setString(12, entry.subjectName());
            statement.setString(13, JdbcCodecs.uuid(entry.bookingId()));
            statement.setString(14, JdbcCodecs.uuid(entry.ticketId()));
            statement.setString(15, JdbcCodecs.instant(entry.timestamp()));
            statement.executeUpdate();
            return entry;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save audit log entry", exception);
        }
    }

    @Override
    public List<AuditLogEntry> search(AuditCriteria criteria, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1 = 1");
        List<Object> values = new ArrayList<>();
        if (criteria.textGiven()) {
            List<String> terms = new ArrayList<>();
            if (!criteria.userIds().isEmpty()) {
                String marks = String.join(", ", Collections.nCopies(criteria.userIds().size(), "?"));
                terms.add("actorUserId IN (" + marks + ")");
                terms.add("subjectUserId IN (" + marks + ")");
                for (int pass = 0; pass < 2; pass++) {
                    criteria.userIds().forEach(id -> values.add(id.toString()));
                }
            }
            if (criteria.idFragment() != null) {
                for (String column : ID_COLUMNS) {
                    terms.add(column + " LIKE ?");
                    values.add("%" + criteria.idFragment() + "%");
                }
            }
            sql.append(terms.isEmpty() ? " AND 0" : " AND (" + String.join(" OR ", terms) + ")");
        }
        if (criteria.actionType() != null) {
            sql.append(" AND actionType = ?");
            values.add(criteria.actionType());
        }
        if (criteria.from() != null) {
            sql.append(" AND substr(timestamp, 1, 19) >= ?");
            values.add(JdbcCodecs.instant(criteria.from()).substring(0, 19));
        }
        if (criteria.toExclusive() != null) {
            sql.append(" AND substr(timestamp, 1, 19) < ?");
            values.add(JdbcCodecs.instant(criteria.toExclusive()).substring(0, 19));
        }
        sql.append(" ORDER BY timestamp DESC, rowid ASC LIMIT ? OFFSET ?");
        values.add(limit);
        values.add(offset);
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setObject(index + 1, values.get(index));
            }
            try (var result = statement.executeQuery()) {
                List<AuditLogEntry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(RowMappers.auditLogEntry(result));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search audit log", exception);
        }
    }

    @Override
    public Set<UUID> findUserIdsByName(String fragment) {
        String like = "%" + fragment.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        String sql = "SELECT userId FROM users WHERE lower(displayName) LIKE ? ESCAPE '\\' "
                + "OR lower(email) LIKE ? ESCAPE '\\' "
                + "UNION SELECT actorUserId FROM audit_log WHERE lower(actorName) LIKE ? ESCAPE '\\' "
                + "UNION SELECT subjectUserId FROM audit_log "
                + "WHERE subjectUserId IS NOT NULL AND lower(subjectName) LIKE ? ESCAPE '\\'";
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 4; index++) {
                statement.setString(index, like);
            }
            try (var result = statement.executeQuery()) {
                Set<UUID> ids = new HashSet<>();
                while (result.next()) {
                    ids.add(UUID.fromString(result.getString(1)));
                }
                return ids;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to resolve users by name", exception);
        }
    }
}
```


`RowMappers.auditLogEntry` (replace):

```java
    public static AuditLogEntry auditLogEntry(ResultSet result) throws SQLException {
        return new AuditLogEntry(
                JdbcCodecs.uuid(result.getString("logId")),
                JdbcCodecs.uuid(result.getString("actorUserId")),
                result.getString("actorName"),
                result.getString("actionType"),
                result.getString("entityType"),
                JdbcCodecs.uuid(result.getString("entityId")),
                result.getString("beforeState"),
                result.getString("afterState"),
                JdbcCodecs.decimal(result.getString("walletAdjustment")),
                result.getString("reason"),
                JdbcCodecs.uuid(result.getString("subjectUserId")),
                result.getString("subjectName"),
                JdbcCodecs.uuid(result.getString("bookingId")),
                JdbcCodecs.uuid(result.getString("ticketId")),
                JdbcCodecs.instant(result.getString("timestamp")));
    }
```

- [ ] **Step 4: Verify**

The test needs the service layer to compile; run it at the end of Task 4 (same compile unit): `./gradlew test --tests "com.snoozeshare.repository.JdbcAuditLogRepositoryTest"`
Expected: 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/snoozeshare/repository src/test/java/com/snoozeshare/repository
git commit -m "feat(audit): repository save/search/name resolution over typed columns"
```

---

### Task 4: AuditService new API, name snapshots, and migrating existing callers

**Files:**
- Modify: `service/AuditService.java`, `service/impl/AuditServiceImpl.java`
- Create: `service/impl/NoOpAuditService.java`
- Modify: `service/impl/ListingServiceImpl.java`, `service/impl/TicketServiceImpl.java`, `service/impl/DisputeSettlementServiceImpl.java`, `app/AppContext.java`
- Modify tests: `testsupport/Fakes.java` (RecordingAudit), `service/AuditServiceTest.java`, `service/ListingServiceTest.java`, `service/TicketServiceCategoryTest.java`, `service/TicketServiceIntegrationTest.java`, `service/SettlementFixtures.java`, `foundation/W1FoundationIntegrationTest.java`, `infra/db/AuditTrailMigrationTest.java`

- [ ] **Step 1: Rewrite `AuditServiceTest` (failing)**

```java
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
            for (String at : new String[] {"2026-09-24T23:59:59Z", "2026-09-25T10:00:00Z", "2026-09-26T00:00:00Z"}) {
                service.record(AuditRecord.builder(agent, AuditAction.LISTING_UPDATED, "Property", UUID.randomUUID())
                        .at(Instant.parse(at)).build());
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.service.AuditServiceTest"`
Expected: FAIL (compilation; the whole tree must compile first — do Step 3, then re-run).

- [ ] **Step 3: Implement the service**

`service/AuditService.java`:

```java
package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.WalletTransaction;

public interface AuditService {

    /** The seeded, non-loginable user that owns system-initiated rows. */
    UUID SYSTEM_ACTOR_ID = UUID.fromString("a0000000-0000-0000-0000-0000000000ff");

    /** Writes one row on the caller's connection, so it commits or rolls back with the caller's change. */
    void record(AuditRecord record);

    /**
     * Writes the money row for one wallet transaction. {@code applied} is the amount that actually moved
     * in the owner's wallet (a payout net of fee, a signed debit for a hold).
     */
    default void recordWalletTransaction(UUID actorId, UUID ownerUserId, WalletTransaction transaction,
                                         BigDecimal applied, String reason) {
        record(AuditRecord.builder(actorId, AuditAction.forWallet(transaction.type()), "WalletTransaction",
                        transaction.transactionId())
                .wallet(applied).reason(reason).subject(ownerUserId)
                .booking(transaction.relatedBookingId()).ticket(transaction.relatedTicketId())
                .at(transaction.createdAt()).build());
    }

    /** Newest first. Text is a name/email/id fragment; a name is resolved to user ids before querying. */
    List<AuditLogEntry> search(AuditFilter filter, int limit, int offset);
}
```

`service/impl/AuditServiceImpl.java`:

```java
package com.snoozeshare.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.repository.AuditCriteria;
import com.snoozeshare.repository.AuditLogRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.AuditFilter;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;

public final class AuditServiceImpl implements AuditService {

    private static final Pattern ID_FRAGMENT = Pattern.compile("[0-9a-fA-F-]{4,36}");

    private final AuditLogRepository entries;
    private final UserRepository users;
    private final Clock clock;

    public AuditServiceImpl(AuditLogRepository entries, UserRepository users, Clock clock) {
        this.entries = entries;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public void record(AuditRecord record) {
        Instant at = record.at() == null ? clock.instant() : record.at();
        entries.save(new AuditLogEntry(UUID.randomUUID(), record.actorId(), nameOf(record.actorId()),
                record.action().name(), record.entityType(), record.entityId(), record.beforeState(),
                record.afterState(), record.walletAdjustment(), record.reason(), record.subjectUserId(),
                record.subjectUserId() == null ? null : nameOf(record.subjectUserId()), record.bookingId(),
                record.ticketId(), at));
    }

    @Override
    public List<AuditLogEntry> search(AuditFilter filter, int limit, int offset) {
        String text = filter.text() == null ? "" : filter.text().trim();
        boolean textGiven = !text.isEmpty();
        Set<UUID> userIds = textGiven ? entries.findUserIdsByName(text) : Set.of();
        String fragment = textGiven && ID_FRAGMENT.matcher(text).matches() ? text.toLowerCase(Locale.ROOT) : null;
        Instant from = filter.from() == null ? null
                : filter.from().atStartOfDay(clock.getZone()).toInstant();
        Instant toExclusive = filter.to() == null ? null
                : filter.to().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        AuditCriteria criteria = new AuditCriteria(textGiven, userIds, fragment,
                filter.action() == null ? null : filter.action().name(), from, toExclusive);
        return entries.search(criteria, limit, offset);
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(User::displayName).orElse("Unknown user");
    }
}
```

`service/impl/NoOpAuditService.java`:

```java
package com.snoozeshare.service.impl;

import java.util.List;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.service.AuditFilter;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;

/** Discards every row; for tests that exercise a service without asserting on the audit log. */
public final class NoOpAuditService implements AuditService {

    @Override
    public void record(AuditRecord record) {
        // intentionally empty
    }

    @Override
    public List<AuditLogEntry> search(AuditFilter filter, int limit, int offset) {
        return List.of();
    }
}
```

- [ ] **Step 4: Migrate the existing callers**

`AppContext.java`: replace `this.auditService = new AuditServiceImpl(new JdbcAuditLogRepository(connection));` with

```java
        this.auditService = new AuditServiceImpl(new JdbcAuditLogRepository(connection), users,
                Clock.systemDefaultZone());
```

(`users` is the local `JdbcUserRepository` declared just above; `Clock` is already imported.)

`ListingServiceImpl.java` — add imports `com.snoozeshare.domain.enums.AuditAction` and `com.snoozeshare.service.AuditRecord`; replace the three `audit.record(...)` calls:

```java
        audit.record(AuditRecord.builder(host.userId(), AuditAction.LISTING_CREATED, "Property",
                        persisted.propertyId())
                .status(null, persisted.status()).subject(host.userId())
                .reason("Listing created: " + persisted.title()).build());
```
```java
        audit.record(AuditRecord.builder(host.userId(), AuditAction.LISTING_UPDATED, "Property",
                        edited.propertyId())
                .subject(host.userId()).reason("Listing details updated").build());
```
```java
        audit.record(AuditRecord.builder(host.userId(), AuditAction.LISTING_STATUS_CHANGED, "Property",
                        propertyId)
                .status(existing.status(), persisted.status()).subject(host.userId()).build());
```

`TicketServiceImpl.java` — add imports `AuditAction`, `AuditRecord`; replace calls (agent = `agentId`):

```java
        // createCategory
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_CATEGORY_CREATED, "TicketCategory",
                        created.categoryId())
                .status(null, "ACTIVE").reason("Category created: " + created.label()).build());
        // renameCategory
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_CATEGORY_RENAMED, "TicketCategory",
                        categoryId)
                .reason("Renamed \"" + existing.label() + "\" to \"" + renamed.label() + "\"").build());
        // setCategoryActive
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_CATEGORY_TOGGLED, "TicketCategory",
                        categoryId)
                .status(existing.active() ? "ACTIVE" : "INACTIVE", updated.active() ? "ACTIVE" : "INACTIVE")
                .reason("Category: " + existing.label()).build());
        // deleteCategory
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_CATEGORY_DELETED, "TicketCategory",
                        categoryId)
                .status(existing.active() ? "ACTIVE" : "INACTIVE", null)
                .reason("Category deleted: " + existing.label()).build());
        // assignToMe
        audit.record(ticketRow(agentId, AuditAction.TICKET_ASSIGNED, ticket)
                .status(ticket.status(), updated.status()).reason("Assigned for review").build());
        // saveNotes (note text is never logged)
        audit.record(ticketRow(agentId, AuditAction.TICKET_NOTE_SAVED, ticket)
                .reason("Internal note updated").build());
        // unassign
        audit.record(ticketRow(agentId, AuditAction.TICKET_UNASSIGNED, ticket)
                .status(ticket.status(), updated.status()).reason("Returned to the queue").build());
```

Add the helper at the bottom of `TicketServiceImpl`:

```java
    private static AuditRecord.Builder ticketRow(UUID agentId, AuditAction action, Ticket ticket) {
        return AuditRecord.builder(agentId, action, "Ticket", ticket.ticketId())
                .subject(ticket.raisedByUserId()).booking(ticket.bookingId()).ticket(ticket.ticketId());
    }
```

`DisputeSettlementServiceImpl.java` — add imports `AuditAction`, `AuditRecord`; replace the existing `audit.record(agentId, "TICKET_RESOLVED", ...)` block with a temporary single row (Task 7 expands it):

```java
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_RESOLVED, "Ticket", ticketId)
                .status(ticket.status(), resolved).reason(reason).subject(ticket.raisedByUserId())
                .booking(booking.bookingId()).ticket(ticketId).at(now).build());
```

Leave the private `json(...)` helper in place until Task 7 (it will be unused; checkstyle does not flag unused private methods).

- [ ] **Step 5: Migrate the tests**

`Fakes.RecordingAudit` (replace the class; keep its position):

```java
    public static final class RecordingAudit implements AuditService {
        private final List<AuditRecord> records = new ArrayList<>();

        public List<String> actions() {
            return records.stream().map(record -> record.action().name()).toList();
        }

        public List<AuditRecord> records() {
            return records;
        }

        @Override
        public void record(AuditRecord record) {
            records.add(record);
        }

        @Override
        public List<AuditLogEntry> search(AuditFilter filter, int limit, int offset) {
            return List.of();
        }
    }
```
(add imports `com.snoozeshare.service.AuditFilter`, `com.snoozeshare.service.AuditRecord`).

`SettlementFixtures.settlement(...)`: the `new AuditServiceImpl(new JdbcAuditLogRepository(connection))` argument becomes
`new AuditServiceImpl(new JdbcAuditLogRepository(connection), new JdbcUserRepository(connection), CLOCK)`.

`ListingServiceTest` line ~409: `new AuditServiceImpl(new JdbcAuditLogRepository(connection), new JdbcUserRepository(connection), Clock.systemUTC())` (add `java.time.Clock` import). The three direct repository reads become:

```java
new JdbcAuditLogRepository(connection).search(AuditCriteria.forAction("LISTING_CREATED"), 100, 0).size()
```
(same with `LISTING_STATUS_CHANGED`; import `com.snoozeshare.repository.AuditCriteria`). Tighten the state assertions: `assertEquals("ACTIVE", audit.get(0).beforeState()); assertEquals("INACTIVE", audit.get(0).afterState());`.

`TicketServiceCategoryTest` (line ~95) and `TicketServiceIntegrationTest` (line ~78): `"CATEGORY_DELETED"` becomes `"TICKET_CATEGORY_DELETED"`. `TicketServiceIntegrationTest.service()` builds `new AuditServiceImpl(new JdbcAuditLogRepository(connection))` → add `new JdbcUserRepository(connection), SettlementFixtures.CLOCK`.

`W1FoundationIntegrationTest` (lines ~40–52): replace the subscriber body and the final assertion:

```java
            context.eventBus().subscribe(WalletTransactionRecordedEvent.class, event -> {
                events.incrementAndGet();
                context.auditService().record(AuditRecord.builder(guest.userId(), AuditAction.TOP_UP,
                        "WalletTransaction", event.transactionId()).subject(guest.userId()).build());
            });
```
```java
            assertEquals(1, context.auditService().search(
                    new AuditFilter(guest.userId().toString(), AuditAction.TOP_UP, null, null), 50, 0).size());
```
(imports `AuditAction`, `AuditFilter`, `AuditRecord`; Task 5 removes the subscriber's record call because the ledger writes the row itself.)

`AuditTrailMigrationTest`: replace the literal id by `AuditService.SYSTEM_ACTOR_ID` (already written that way).

- [ ] **Step 6: Verify everything compiles and Tasks 1–4 tests pass**

Run: `./gradlew test --tests "com.snoozeshare.service.AuditServiceTest" --tests "com.snoozeshare.service.AuditRecordTest" --tests "com.snoozeshare.repository.JdbcAuditLogRepositoryTest" --tests "com.snoozeshare.infra.db.AuditTrailMigrationTest" --tests "com.snoozeshare.infra.db.SchemaParityTest" --tests "com.snoozeshare.domain.AuditActionTest"`
Expected: PASS.
Then the wider suite for regressions: `./gradlew test`
Expected: PASS except `MockDbFixtureTest`/`MigrationRunnerReferenceDbTest`/mock-DB based tests that read the old seed's `audit_log` (they still see the old 15 rows and old column shape — they must still pass because the committed `.db` is untouched until Task 8; if `MigrationRunner` adoption of the old `.db` fails on `walletAdjustment`, that is expected to be handled by V002 being applied to the adopted DB — verify `MigrationRunnerReferenceDbTest` passes).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(audit): AuditRecord-based AuditService with name snapshots and search; migrate callers"
```

---

### Task 5: Wallet movements write money rows

**Files:**
- Modify: `service/impl/WalletLedgerWriter.java`, `WalletServiceImpl.java`, `TransactionServiceImpl.java`, `app/AppContext.java`
- Modify tests: `service/WalletLedgerTest.java`, `service/WalletTransactionAtomicityTest.java`, `service/TransactionServiceTest.java`, `foundation/W1FoundationIntegrationTest.java`
- Test: `src/test/java/com/snoozeshare/service/WalletLedgerAuditTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.service.WalletLedgerAuditTest"`
Expected: FAIL (compile: `WalletServiceImpl` has no 5-arg constructor).

- [ ] **Step 3: Implement**

`WalletLedgerWriter.java` — replace the two constructors with one and add the field; import `com.snoozeshare.service.AuditService`:

```java
    private final Connection connection;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final EventBus eventBus;
    private final AuditService audit;

    public WalletLedgerWriter(Connection connection, WalletRepository wallets,
                              WalletTransactionRepository transactions, EventBus eventBus,
                              AuditService audit) {
        this.connection = connection;
        this.wallets = wallets;
        this.transactions = transactions;
        this.eventBus = eventBus;
        this.audit = audit;
    }
```

In `record(...)`, replace `return transactions.save(entry);` with:

```java
                WalletTransaction saved = transactions.save(entry);
                UUID actor = initiatedBy == null ? wallet.userId() : initiatedBy;
                String feeNote = feeAmount.signum() > 0 ? "Fee deducted: " + feeAmount.toPlainString() : null;
                audit.recordWalletTransaction(actor, wallet.userId(), saved, amount.subtract(feeAmount), feeNote);
                return saved;
```

`WalletServiceImpl.java`: replace its two constructors (3-arg and 4-arg-with-`EventBus`) with the single
`public WalletServiceImpl(Connection connection, WalletRepository wallets, WalletTransactionRepository transactions, EventBus eventBus, AuditService audit)`
that builds `new WalletLedgerWriter(connection, wallets, transactions, eventBus, audit)`.
`TransactionServiceImpl.java`: add a trailing `AuditService audit` parameter to its single constructor and pass it to the writer the same way. Import `com.snoozeshare.service.AuditService` in both.

`AppContext.java`: `new WalletServiceImpl(connection, wallets, new JdbcWalletTransactionRepository(connection), eventBus, auditService)` — note `auditService` is created *after* `walletService` today: move the `this.auditService = ...` statement above `this.walletService = ...`. `new TransactionServiceImpl(connection, bookingRepo, wallets, txnRepo, eventBus, auditService)`.

- [ ] **Step 4: Update the other constructor call sites**

- `WalletLedgerTest` (2 sites): append `, null, new NoOpAuditService()` to `new WalletServiceImpl(connection, wallets, transactions)`.
- `WalletTransactionAtomicityTest` line ~39: `new WalletLedgerWriter(connection, wallets, transactions, null, new NoOpAuditService())`.
- `TransactionServiceTest.createService`: append `, new NoOpAuditService()` after the existing `null` argument.
- `W1FoundationIntegrationTest`: delete the `record(...)` call inside the subscriber (the ledger now writes the row) so the subscriber only increments `events`; the final assertion (`search` for `TOP_UP` by the guest's id) now expects `1` from the ledger.
- Add `import com.snoozeshare.service.impl.NoOpAuditService;` where used.

- [ ] **Step 5: Verify**

Run: `./gradlew test --tests "com.snoozeshare.service.WalletLedgerAuditTest" --tests "com.snoozeshare.service.WalletLedgerTest" --tests "com.snoozeshare.service.WalletTransactionAtomicityTest" --tests "com.snoozeshare.service.TransactionServiceTest" --tests "com.snoozeshare.foundation.W1FoundationIntegrationTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(audit): every ledger write records a money row in the same transaction"
```

---

### Task 6: Booking lifecycle rows (submit, confirm, reject, cancel)

**Files:**
- Modify: `service/impl/BookingServiceImpl.java`, `app/AppContext.java`
- Modify/Test: `src/test/java/com/snoozeshare/service/BookingServiceTest.java`

- [ ] **Step 1: Write the failing tests** (append to `BookingServiceTest`, in a `// --- audit tests ---` section before `// --- helpers ---`; add imports `assertNull`, `com.snoozeshare.domain.model.AuditLogEntry`, `com.snoozeshare.repository.AuditCriteria`, `com.snoozeshare.repository.jdbc.JdbcAuditLogRepository`, `java.util.Set` is already imported)

```java
    // --- audit tests ---

    private static List<AuditLogEntry> auditRows(Connection connection) {
        return new JdbcAuditLogRepository(connection).search(AuditCriteria.all(), 100, 0);
    }

    private static AuditLogEntry row(List<AuditLogEntry> rows, String action) {
        return rows.stream().filter(r -> r.actionType().equals(action)).findFirst().orElseThrow();
    }

    @Test
    void submitRequestAuditsTheRequestAndTheEscrowHoldAsSeparateRows() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);

            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            List<AuditLogEntry> rows = auditRows(connection);
            assertEquals(Set.of("BOOKING_REQUESTED", "ESCROW_HOLD"),
                    Set.copyOf(rows.stream().map(AuditLogEntry::actionType).toList()));
            assertEquals(2, rows.size());
            AuditLogEntry requested = row(rows, "BOOKING_REQUESTED");
            assertEquals("Booking", requested.entityType());
            assertEquals(booking.bookingId(), requested.entityId());
            assertNull(requested.beforeState());
            assertEquals("PENDING", requested.afterState());
            assertNull(requested.walletAdjustment());
            assertEquals(booking.bookingId(), requested.bookingId());
            assertEquals(ctx.guestId, requested.actorUserId());
            assertEquals(ctx.guestId, requested.subjectUserId());
            AuditLogEntry hold = row(rows, "ESCROW_HOLD");
            assertEquals("WalletTransaction", hold.entityType());
            assertEquals(0, new BigDecimal("-300.00").compareTo(hold.walletAdjustment()));
            assertNull(hold.afterState());
            assertEquals(booking.bookingId(), hold.bookingId());
            assertEquals(ctx.guestId, hold.subjectUserId());
        }
    }

    @Test
    void hostConfirmAuditsOneStatusRowByTheHost() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            service.decide(booking.bookingId(), true, ctx.hostId);

            AuditLogEntry confirmed = row(auditRows(connection), "BOOKING_CONFIRMED");
            assertEquals("PENDING", confirmed.beforeState());
            assertEquals("CONFIRMED", confirmed.afterState());
            assertEquals(ctx.hostId, confirmed.actorUserId());
            assertEquals(ctx.guestId, confirmed.subjectUserId());
            assertEquals(booking.bookingId(), confirmed.bookingId());
        }
    }

    @Test
    void hostRejectAuditsTheStatusChangeAndTheFullRefundSeparately() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            service.decide(booking.bookingId(), false, ctx.hostId);

            List<AuditLogEntry> rows = auditRows(connection);
            AuditLogEntry rejected = row(rows, "BOOKING_REJECTED");
            assertEquals("PENDING", rejected.beforeState());
            assertEquals("REJECTED", rejected.afterState());
            assertNull(rejected.walletAdjustment());
            AuditLogEntry refund = row(rows, "ESCROW_REFUND");
            assertEquals(0, new BigDecimal("300.00").compareTo(refund.walletAdjustment()));
            assertEquals(ctx.hostId, refund.actorUserId());
            assertEquals(ctx.guestId, refund.subjectUserId());
        }
    }

    @Test
    void guestCancelAuditsTheStatusChangeAndTheRefundSeparately() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            BookingService service = createService(connection);
            Booking booking = service.submitRequest(ctx.guestId, ctx.propertyId,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

            service.cancel(booking.bookingId(), ctx.guestId);

            List<AuditLogEntry> rows = auditRows(connection);
            AuditLogEntry cancelled = row(rows, "BOOKING_CANCELLED_BY_GUEST");
            assertEquals("PENDING", cancelled.beforeState());
            assertEquals("CANCELLED_BY_GUEST", cancelled.afterState());
            assertNull(cancelled.walletAdjustment());
            assertEquals("Full refund", cancelled.reason());
            assertEquals(0, new BigDecimal("300.00").compareTo(row(rows, "ESCROW_REFUND").walletAdjustment()));
        }
    }

    @Test
    void aFailedSubmitLeavesNoAuditRow() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("10.00"));
            BookingService service = createService(connection);

            assertThrows(IllegalArgumentException.class, () -> service.submitRequest(ctx.guestId,
                    ctx.propertyId, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13)));

            assertEquals(0, auditRows(connection).size());
        }
    }
```

Change the helper `createService(Connection, InProcessEventBus)` (bottom of the file) so every existing booking test also exercises the audit FK constraints:

```java
    static BookingService createService(Connection connection, InProcessEventBus eventBus) {
        return new BookingServiceImpl(connection,
                new JdbcBookingRepository(connection),
                new JdbcPropertyRepository(connection),
                new JdbcAvailabilityBlockRepository(connection),
                new JdbcWalletRepository(connection),
                new JdbcWalletTransactionRepository(connection),
                eventBus,
                new AuditServiceImpl(new JdbcAuditLogRepository(connection),
                        new JdbcUserRepository(connection), Clock.systemUTC()));
    }
```
(imports `java.time.Clock`, `com.snoozeshare.service.impl.AuditServiceImpl`).

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.service.BookingServiceTest"`
Expected: FAIL (compile: `BookingServiceImpl` has no 8-arg constructor).

- [ ] **Step 3: Implement in `BookingServiceImpl`**

Add imports `com.snoozeshare.domain.enums.AuditAction`, `com.snoozeshare.service.AuditRecord`, `com.snoozeshare.service.AuditService`; add field `private final AuditService audit;` and constructor parameter `AuditService audit` (last). Then:

`submitRequest` — after `bookings.save(newBooking)`/block/wallet writes, capture the escrow transaction and audit at the end of the lambda:

```java
                WalletTransaction hold = transactions.save(new WalletTransaction(UUID.randomUUID(),
                        wallet.walletId(), WalletTransactionType.ESCROW_HOLD, totalAmount.negate(),
                        BigDecimal.ZERO, balanceAfter, bookingId, null, guestId, now));
                audit.record(AuditRecord.builder(guestId, AuditAction.BOOKING_REQUESTED, "Booking", bookingId)
                        .status(null, BookingStatus.PENDING).subject(guestId).booking(bookingId).at(now).build());
                audit.recordWalletTransaction(guestId, guestId, hold, hold.amount(), null);

                return newBooking;
```
(replacing the existing `transactions.save(...)` statement; the wallet/insufficient-funds check stays before it.)

`decide` — inside the lambda after `bookings.save(updated)`:

```java
                audit.record(AuditRecord.builder(hostId,
                                approve ? AuditAction.BOOKING_CONFIRMED : AuditAction.BOOKING_REJECTED,
                                "Booking", bookingId)
                        .status(booking.status(), target).subject(booking.guestId()).booking(bookingId)
                        .at(now).build());
```
and in the reject branch replace the `transactions.save(...)` with:

```java
                    WalletTransaction refund = transactions.save(new WalletTransaction(UUID.randomUUID(),
                            wallet.walletId(), WalletTransactionType.ESCROW_REFUND,
                            booking.totalAmount(), BigDecimal.ZERO, balanceAfter,
                            bookingId, null, hostId, now));
                    audit.recordWalletTransaction(hostId, booking.guestId(), refund, refund.amount(), null);
```

`cancel` — after `bookings.save(updated)`:

```java
                audit.record(AuditRecord.builder(actingGuestId, AuditAction.BOOKING_CANCELLED_BY_GUEST,
                                "Booking", bookingId)
                        .status(booking.status(), BookingStatus.CANCELLED_BY_GUEST).subject(actingGuestId)
                        .booking(bookingId)
                        .reason(refundAmount.compareTo(booking.totalAmount()) == 0
                                ? "Full refund" : "50% refund (within 48h of check-in)")
                        .at(now).build());
```
and the refund `transactions.save(...)` becomes:

```java
                WalletTransaction refund = transactions.save(new WalletTransaction(UUID.randomUUID(),
                        wallet.walletId(), WalletTransactionType.ESCROW_REFUND, refundAmount,
                        BigDecimal.ZERO, balanceAfter, bookingId, null, actingGuestId, now));
                audit.recordWalletTransaction(actingGuestId, actingGuestId, refund, refund.amount(), null);
```

`AppContext`: `new BookingServiceImpl(connection, bookingRepo, propertyRepo, blockRepo, wallets, txnRepo, eventBus, auditService)`.

Any other test that constructs `BookingServiceImpl` uses `BookingServiceTest.createService` (verify with `grep -rn "new BookingServiceImpl" src`).

- [ ] **Step 4: Verify**

Run: `./gradlew test --tests "com.snoozeshare.service.BookingServiceTest" --tests "com.snoozeshare.foundation.*"`
Expected: PASS (all existing booking tests plus 5 new).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(audit): booking submit/confirm/reject/cancel write one row per change"
```

---

### Task 7: Dispute settlement writes the four one-change rows

**Files:**
- Modify: `service/impl/DisputeSettlementServiceImpl.java`
- Modify tests: `service/DisputeSettlementServiceTest.java`

- [ ] **Step 1: Write the failing tests** (append to `DisputeSettlementServiceTest`; add imports `com.snoozeshare.service.impl.DisputeSettlementServiceImpl` already present, `java.sql.Statement` not needed)

```java
    private static final String NOW = "2026-09-25T04:00:00Z";

    private static List<String> resolutionActions(MockDbFixture db, UUID ticketId) throws Exception {
        List<String> actions = new ArrayList<>();
        try (var statement = db.connection().prepareStatement("SELECT actionType FROM audit_log "
                + "WHERE ticketId = ? AND timestamp = ? ORDER BY rowid")) {
            statement.setString(1, ticketId.toString());
            statement.setString(2, NOW);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    actions.add(result.getString(1));
                }
            }
        }
        return actions;
    }

    private static BigDecimal adjustment(MockDbFixture db, UUID ticketId, String action) throws Exception {
        return new BigDecimal(db.scalarString("SELECT walletAdjustment FROM audit_log "
                + "WHERE ticketId = ? AND actionType = ? AND timestamp = ?", ticketId, action, NOW));
    }

    @Test
    void aManualSplitWritesTicketBookingGuestAndHostRowsInCausalOrder(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            service(db, new InProcessEventBus()).settle(MockIds.TICKET_3, ResolutionMode.MANUAL,
                    new BigDecimal("60.00"), BEN, "split");

            assertEquals(List.of("TICKET_RESOLVED", "BOOKING_COMPLETED", "AGENT_OVERRIDE", "BOOKING_PAYOUT"),
                    resolutionActions(db, MockIds.TICKET_3));
            assertMoney("60", adjustment(db, MockIds.TICKET_3, "AGENT_OVERRIDE"));
            assertMoney("145.50", adjustment(db, MockIds.TICKET_3, "BOOKING_PAYOUT"));
            assertEquals("IN_REVIEW", db.scalarString("SELECT beforeState FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'TICKET_RESOLVED' AND timestamp = ?", MockIds.TICKET_3, NOW));
            assertEquals("RESOLVED_APPROVED", db.scalarString("SELECT afterState FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'TICKET_RESOLVED' AND timestamp = ?", MockIds.TICKET_3, NOW));
            assertEquals("COMPLETED", db.scalarString("SELECT afterState FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'BOOKING_COMPLETED' AND timestamp = ?", MockIds.TICKET_3, NOW));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? AND timestamp = ? "
                    + "AND walletAdjustment IS NOT NULL AND subjectUserId = ?", MockIds.TICKET_3, NOW,
                    MockIds.GUEST_SOPHIA));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? AND timestamp = ? "
                    + "AND walletAdjustment IS NOT NULL AND subjectUserId = ?", MockIds.TICKET_3, NOW,
                    MockIds.HOST_DIEGO));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? AND timestamp = ? "
                    + "AND reason LIKE '%3%% platform fee (4.50)%'", MockIds.TICKET_3, NOW));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL "
                    + "AND (beforeState IS NOT NULL OR afterState IS NOT NULL)"));
        }
    }

    @Test
    void rejectSkipsTheZeroGuestRefundRow(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            service(db, new InProcessEventBus()).settle(MockIds.TICKET_3, ResolutionMode.REJECT,
                    BigDecimal.ZERO, BEN, "No evidence of a violation");

            assertEquals(List.of("TICKET_RESOLVED", "BOOKING_COMPLETED", "BOOKING_PAYOUT"),
                    resolutionActions(db, MockIds.TICKET_3));
            assertMoney("203.70", adjustment(db, MockIds.TICKET_3, "BOOKING_PAYOUT"));
        }
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.service.DisputeSettlementServiceTest"`
Expected: FAIL (only `TICKET_RESOLVED` is written; the list assertions fail).

- [ ] **Step 3: Implement**

In `DisputeSettlementServiceImpl.apply(...)`, replace the temporary single-row `audit.record(...)` (from Task 4) with:

```java
        audit.record(AuditRecord.builder(agentId, AuditAction.TICKET_RESOLVED, "Ticket", ticketId)
                .status(ticket.status(), resolved).reason(reason).subject(ticket.raisedByUserId())
                .booking(booking.bookingId()).ticket(ticketId).at(now).build());
        audit.record(AuditRecord.builder(agentId, AuditAction.BOOKING_COMPLETED, "Booking", booking.bookingId())
                .status(booking.status(), BookingStatus.COMPLETED).reason("Escrow settled by ticket resolution")
                .subject(booking.guestId()).booking(booking.bookingId()).ticket(ticketId).at(now).build());
        if (guestTransaction != null) {
            audit.recordWalletTransaction(agentId, booking.guestId(), guestTransaction,
                    split.guestRefund(), null);
        }
        if (hostTransaction != null) {
            audit.recordWalletTransaction(agentId, property.hostId(), hostTransaction, split.hostNet(),
                    "Payout net of 3% platform fee ("
                            + split.fee().setScale(2, RoundingMode.HALF_UP).toPlainString() + ")");
        }
```

Add `import java.math.RoundingMode;`. Delete the now-unused private `json(String... pairs)` helper at the bottom of the class.

- [ ] **Step 4: Verify**

Run: `./gradlew test --tests "com.snoozeshare.service.DisputeSettlement*" --tests "com.snoozeshare.service.TicketService*"`
Expected: PASS (including the atomicity tests: a failing wallet save leaves 0 `TICKET_RESOLVED` rows).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(audit): ticket resolution writes ticket, booking, guest and host rows"
```

---

### Task 8: Mock DB — schema, seed and rebuilt `.db`

**Files:**
- Modify: `db/schema.sql` (already done in Task 2), `db/seed-mock-data.sql`, `db/snoozeshare-mock.db`
- Modify tests: `infra/db/MigrationRunnerReferenceDbTest.java`, and any test whose assertions count seeded `audit_log`/users rows (found in Step 4)
- Test: `src/test/java/com/snoozeshare/testsupport/MockAuditSeedTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.snoozeshare.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.AuditAction;

class MockAuditSeedTest {

    @Test
    void everyWalletTransactionHasExactlyOneMoneyRowWithTheAppliedAmount(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions t WHERE "
                    + "(SELECT COUNT(*) FROM audit_log a WHERE a.entityType = 'WalletTransaction' "
                    + "AND a.entityId = t.transactionId) <> 1"));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions t JOIN audit_log a "
                    + "ON a.entityId = t.transactionId WHERE abs(a.walletAdjustment - t.amount) > 0.005"));
        }
    }

    @Test
    void noRowMixesAStatusChangeWithMoneyAndEveryRowHasNamesAndAKnownAction(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL "
                    + "AND (beforeState IS NOT NULL OR afterState IS NOT NULL)"));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE actorName IS NULL"));
            try (var statement = db.connection().createStatement();
                 ResultSet result = statement.executeQuery("SELECT DISTINCT actionType FROM audit_log")) {
                while (result.next()) {
                    AuditAction.valueOf(result.getString(1));
                }
            }
            long total = db.scalarLong("SELECT COUNT(*) FROM audit_log");
            assertTrue(total > 50 && total < 200, "seeded rows fit the first page: " + total);
        }
    }

    @Test
    void ticketFourManualSettlementReadsAsFourRows(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            for (String action : new String[] {"TICKET_OPENED", "TICKET_ASSIGNED", "TICKET_RESOLVED",
                "BOOKING_COMPLETED", "AGENT_OVERRIDE", "BOOKING_PAYOUT"}) {
                assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE ticketId = ? "
                        + "AND actionType = ?", MockIds.TICKET_4, action), action);
            }
            assertEquals(165.0, Double.parseDouble(db.scalarString("SELECT walletAdjustment FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'AGENT_OVERRIDE'", MockIds.TICKET_4)), 0.001);
            assertEquals(160.05, Double.parseDouble(db.scalarString("SELECT walletAdjustment FROM audit_log "
                    + "WHERE ticketId = ? AND actionType = 'BOOKING_PAYOUT'", MockIds.TICKET_4)), 0.001);
        }
    }

    @Test
    void accountGovernanceRowsAreSeededInTheShapeW11Will(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            assertEquals(2L, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE actionType = 'ACCOUNT_SUSPENDED' "
                    + "AND entityType = 'User' AND beforeState = 'ACTIVE' AND afterState = 'SUSPENDED' "
                    + "AND reason IS NOT NULL AND subjectUserId = entityId"));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'BOOKING_FORCE_CANCELLED' AND bookingId IS NOT NULL"));
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.testsupport.MockAuditSeedTest"`
Expected: FAIL (the committed `.db` still holds the old 15 rows).

- [ ] **Step 3: Rewrite the seed**

In `db/seed-mock-data.sql`:

(a) Add the System user to the `users` INSERT (after the last agent row, keeping the statement's comma/semicolon shape):

```sql
('a0000000-0000-0000-0000-0000000000ff','AGENT','SnoozeShare System','system@snoozeshare.invalid','SUSPENDED',NULL,'2026-01-01T00:00:00Z'),
```

(b) Replace the whole `audit_log` section (from `-- ==== audit_log ====` through the old INSERT, up to but not including `PRAGMA foreign_keys = ON;`) with the following. Every derived row's id is `'5'` + a kind hex digit + `substr(sourceId, 3)`, which keeps a valid hex UUID (kinds: 1 requested, 2 confirmed, 3 rejected, 4 cancelled by guest, 5 cancelled by host, 6 completed, a money, b ticket opened, c assigned, d resolved); the handwritten governance rows use the `50…` prefix.

```sql
-- ============================== audit_log ==============================
-- One row per change (W12, C28). Booking, ticket and money rows are derived from the tables above so they
-- can never disagree with them; account-governance rows are written by hand in the shape W11 will emit.
INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '51' || substr(b.bookingId, 3), b.guestId, g.displayName, 'BOOKING_REQUESTED', 'Booking', b.bookingId,
       NULL, 'PENDING', NULL, NULL, b.guestId, g.displayName, b.bookingId, NULL, b.createdAt
FROM bookings b JOIN users g ON g.userId = b.guestId;

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '52' || substr(b.bookingId, 3), p.hostId, h.displayName, 'BOOKING_CONFIRMED', 'Booking', b.bookingId,
       'PENDING', 'CONFIRMED', NULL, NULL, b.guestId, g.displayName, b.bookingId, NULL, b.decidedAt
FROM bookings b JOIN properties p ON p.propertyId = b.listingId JOIN users h ON h.userId = p.hostId
JOIN users g ON g.userId = b.guestId
WHERE b.status IN ('CONFIRMED', 'COMPLETED', 'FORCE_CANCELLED') AND b.decidedAt IS NOT NULL;

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '53' || substr(b.bookingId, 3), p.hostId, h.displayName, 'BOOKING_REJECTED', 'Booking', b.bookingId,
       'PENDING', 'REJECTED', NULL, NULL, b.guestId, g.displayName, b.bookingId, NULL, b.decidedAt
FROM bookings b JOIN properties p ON p.propertyId = b.listingId JOIN users h ON h.userId = p.hostId
JOIN users g ON g.userId = b.guestId
WHERE b.status = 'REJECTED';

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '54' || substr(b.bookingId, 3), b.guestId, g.displayName, 'BOOKING_CANCELLED_BY_GUEST', 'Booking', b.bookingId,
       'CONFIRMED', 'CANCELLED_BY_GUEST', NULL, NULL, b.guestId, g.displayName, b.bookingId, NULL, b.decidedAt
FROM bookings b JOIN users g ON g.userId = b.guestId
WHERE b.status = 'CANCELLED_BY_GUEST';

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '55' || substr(b.bookingId, 3), p.hostId, h.displayName, 'BOOKING_CANCELLED_BY_HOST', 'Booking', b.bookingId,
       'CONFIRMED', 'CANCELLED_BY_HOST', NULL, NULL, b.guestId, g.displayName, b.bookingId, NULL, b.decidedAt
FROM bookings b JOIN properties p ON p.propertyId = b.listingId JOIN users h ON h.userId = p.hostId
JOIN users g ON g.userId = b.guestId
WHERE b.status = 'CANCELLED_BY_HOST';

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '56' || substr(b.bookingId, 3), a.userId, a.displayName, 'BOOKING_COMPLETED', 'Booking', b.bookingId,
       'CONFIRMED', 'COMPLETED', NULL, CASE WHEN t.ticketId IS NULL THEN NULL ELSE 'Escrow settled by ticket resolution' END,
       b.guestId, g.displayName, b.bookingId, t.ticketId, b.completedAt
FROM bookings b JOIN users g ON g.userId = b.guestId
LEFT JOIN tickets t ON t.bookingId = b.bookingId AND t.status LIKE 'RESOLVED%'
JOIN users a ON a.userId = COALESCE(t.assignedAgentId, 'a0000000-0000-0000-0000-0000000000ff')
WHERE b.status = 'COMPLETED';

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '5b' || substr(t.ticketId, 3), t.raisedByUserId, u.displayName, 'TICKET_OPENED', 'Ticket', t.ticketId,
       NULL, 'OPEN', NULL, t.title, t.raisedByUserId, u.displayName, t.bookingId, t.ticketId, t.createdAt
FROM tickets t JOIN users u ON u.userId = t.raisedByUserId;

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '5c' || substr(t.ticketId, 3), t.assignedAgentId, a.displayName, 'TICKET_ASSIGNED', 'Ticket', t.ticketId,
       'OPEN', 'IN_REVIEW', NULL, 'Assigned for review', t.raisedByUserId, u.displayName, t.bookingId, t.ticketId,
       strftime('%Y-%m-%dT%H:%M:%SZ', t.createdAt, '+1 hour')
FROM tickets t JOIN users a ON a.userId = t.assignedAgentId JOIN users u ON u.userId = t.raisedByUserId;

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '5d' || substr(t.ticketId, 3), t.assignedAgentId, a.displayName, 'TICKET_RESOLVED', 'Ticket', t.ticketId,
       'IN_REVIEW', t.status, NULL, t.resolutionReason, t.raisedByUserId, u.displayName, t.bookingId, t.ticketId, t.resolvedAt
FROM tickets t JOIN users a ON a.userId = t.assignedAgentId JOIN users u ON u.userId = t.raisedByUserId
WHERE t.status LIKE 'RESOLVED%';

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp)
SELECT '5a' || substr(t.transactionId, 3), actor.userId, actor.displayName, t.type, 'WalletTransaction', t.transactionId,
       NULL, NULL, t.amount,
       CASE WHEN t.feeAmount > 0 THEN 'Payout net of 3% platform fee (' || printf('%.2f', t.feeAmount) || ')' END,
       w.userId, o.displayName, t.relatedBookingId, t.relatedTicketId, t.createdAt
FROM wallet_transactions t JOIN wallets w ON w.walletId = t.walletId JOIN users o ON o.userId = w.userId
JOIN users actor ON actor.userId = COALESCE(t.initiatedBy, w.userId);

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp) VALUES
('50000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000002','Ben Alvarez','ACCOUNT_SUSPENDED','User','c0000000-0000-0000-0000-000000000006','ACTIVE','SUSPENDED',NULL,'Suspended by support agent pending review','c0000000-0000-0000-0000-000000000006','Kai Nakamura',NULL,NULL,'2026-09-09T08:30:00Z'),
('50000000-0000-0000-0000-000000000002','a0000000-0000-0000-0000-000000000002','Ben Alvarez','BOOKING_FORCE_CANCELLED','Booking','20000000-0000-0000-0000-000000000012','CONFIRMED','FORCE_CANCELLED',NULL,'Account suspended — cascading cancellation','c0000000-0000-0000-0000-000000000006','Kai Nakamura','20000000-0000-0000-0000-000000000012',NULL,'2026-09-09T09:00:00Z'),
('50000000-0000-0000-0000-000000000003','a0000000-0000-0000-0000-000000000003','Chen Wu','ACCOUNT_SUSPENDED','User','b0000000-0000-0000-0000-000000000006','ACTIVE','SUSPENDED',NULL,'Suspended by support agent pending review','b0000000-0000-0000-0000-000000000006','Sam O''Connor',NULL,NULL,'2026-07-20T10:00:00Z'),
('50000000-0000-0000-0000-000000000004','a0000000-0000-0000-0000-000000000003','Chen Wu','LISTING_STATUS_CASCADE','Property','10000000-0000-0000-0000-000000000010','ACTIVE','INACTIVE',NULL,'Host suspended','b0000000-0000-0000-0000-000000000006','Sam O''Connor',NULL,NULL,'2026-07-20T10:05:00Z');
```

- [ ] **Step 4: Rebuild the DB and verify integrity**

```bash
cd db && rm -f snoozeshare-mock.db && sqlite3 snoozeshare-mock.db < schema.sql && sqlite3 snoozeshare-mock.db < seed-mock-data.sql && cd ..
sqlite3 db/snoozeshare-mock.db "PRAGMA foreign_key_check; PRAGMA integrity_check; SELECT COUNT(*) FROM audit_log; SELECT actionType, COUNT(*) FROM audit_log GROUP BY 1 ORDER BY 1;"
```
Expected: `foreign_key_check` prints nothing, `integrity_check` prints `ok`, the count is between 50 and 200, and every `actionType` is one of the `AuditAction` names (no `WALLET_TRANSACTION_OVERRIDE`, no `BOOKING_FORCE_CANCEL`).
If `foreign_key_check` reports a row, fix the seed (do not weaken the check). Also verify the ledger invariant helper still passes in Step 5.

- [ ] **Step 5: Fix tests that depended on the old seed, then verify**

`MigrationRunnerReferenceDbTest`: users count `16L` → `17L`.
Run: `./gradlew test`
Expected: PASS. If a test still asserts old audit counts or the old `afterState` JSON (grep `audit_log` under `src/test`), update it to the new shape; do not edit committed seed to satisfy an obsolete assertion. `MockAuditSeedTest` (4 tests) must be green.

- [ ] **Step 6: Commit**

```bash
git add db src/test
git commit -m "feat(audit): reseed mock DB with one-change audit rows and the System user"
```

---

### Task 9: Audit Log screen (FXML + controller + CSS + shell wiring)

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/admin/audit/AuditLogController.java`
- Create: `src/main/resources/com/snoozeshare/ui/admin/audit/audit-log.fxml`
- Modify: `src/main/java/com/snoozeshare/ui/admin/AdminShellController.java`, `src/main/resources/com/snoozeshare/ui/admin/agent-theme.css`
- Test: `src/test/java/com/snoozeshare/ui/admin/audit/AuditLogFormattingTest.java`

- [ ] **Step 1: Write the failing formatting test** (pure functions, no toolkit)

```java
package com.snoozeshare.ui.admin.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.model.AuditLogEntry;

class AuditLogFormattingTest {

    private static AuditLogEntry entry(String action, String before, String after, BigDecimal amount,
                                       UUID booking, UUID ticket) {
        return new AuditLogEntry(UUID.randomUUID(), UUID.randomUUID(), "Amy", action, "Ticket", UUID.randomUUID(),
                before, after, amount, null, null, null, booking, ticket, Instant.parse("2026-09-25T04:00:00Z"));
    }

    @Test
    void statusShowsBeforeToAfterWithReadableLabels() {
        assertEquals("In review \u2192 Resolved approved",
                AuditLogController.statusText(entry("TICKET_RESOLVED", "IN_REVIEW", "RESOLVED_APPROVED", null,
                        null, null)));
        assertEquals("Pending", AuditLogController.statusText(entry("BOOKING_REQUESTED", null, "PENDING", null,
                null, null)));
        assertEquals("\u2014", AuditLogController.statusText(entry("TOP_UP", null, null, BigDecimal.TEN, null, null)));
    }

    @Test
    void amountIsSignedSgdOrADash() {
        assertEquals("+SGD 465.60", AuditLogController.amountText(entry("BOOKING_PAYOUT", null, null,
                new BigDecimal("465.6"), null, null)));
        assertEquals("-SGD 120.00", AuditLogController.amountText(entry("ESCROW_HOLD", null, null,
                new BigDecimal("-120"), null, null)));
        assertEquals("\u2014", AuditLogController.amountText(entry("TICKET_RESOLVED", "A", "B", null, null, null)));
    }

    @Test
    void refShowsTheLastFourOfBookingAndTicketIds() {
        UUID booking = UUID.fromString("20000000-0000-0000-0000-000000000009");
        UUID ticket = UUID.fromString("d0000000-0000-0000-0000-000000000004");
        assertEquals("Booking #0009 \u00b7 Ticket #0004",
                AuditLogController.refText(entry("TICKET_RESOLVED", null, null, null, booking, ticket)));
        assertEquals("Booking #0009", AuditLogController.refText(entry("BOOKING_REQUESTED", null, "PENDING", null,
                booking, null)));
        assertEquals("\u2014", AuditLogController.refText(entry("LISTING_UPDATED", null, null, null, null, null)));
    }

    @Test
    void pillToneGroupsActions() {
        assertEquals("agent-pill-success", AuditLogController.pillClass("BOOKING_PAYOUT"));
        assertEquals("agent-pill-accent", AuditLogController.pillClass("AGENT_OVERRIDE"));
        assertEquals("agent-pill-warning", AuditLogController.pillClass("ACCOUNT_SUSPENDED"));
        assertEquals("agent-pill-danger", AuditLogController.pillClass("BOOKING_FORCE_CANCELLED"));
        assertEquals("agent-pill-neutral", AuditLogController.pillClass("LISTING_UPDATED"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.ui.admin.audit.AuditLogFormattingTest"`
Expected: FAIL (`AuditLogController` not found).

- [ ] **Step 3: FXML**

`src/main/resources/com/snoozeshare/ui/admin/audit/audit-log.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.DatePicker?>
<?import javafx.scene.control.Hyperlink?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" styleClass="agent-content" xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.admin.audit.AuditLogController">
    <padding><Insets top="24" right="32" bottom="24" left="32"/></padding>

    <Label text="Audit log" styleClass="page-title"/>

    <HBox spacing="12" alignment="BOTTOM_LEFT" styleClass="agent-card, agent-filter-card">
        <VBox spacing="4" prefWidth="280">
            <Label text="SEARCH" styleClass="agent-dialog-field-label"/>
            <TextField fx:id="searchField" promptText="User name, or booking / ticket / user id"/>
        </VBox>
        <VBox spacing="4" prefWidth="200">
            <Label text="ACTION TYPE" styleClass="agent-dialog-field-label"/>
            <ComboBox fx:id="actionCombo" styleClass="agent-combo" maxWidth="Infinity"/>
        </VBox>
        <VBox spacing="4" prefWidth="150">
            <Label text="FROM" styleClass="agent-dialog-field-label"/>
            <DatePicker fx:id="fromPicker" maxWidth="Infinity"/>
        </VBox>
        <VBox spacing="4" prefWidth="150">
            <Label text="TO" styleClass="agent-dialog-field-label"/>
            <DatePicker fx:id="toPicker" maxWidth="Infinity"/>
        </VBox>
        <Button fx:id="applyButton" text="Apply filters" styleClass="button" onAction="#handleApply"/>
        <Hyperlink fx:id="clearLink" text="Clear" styleClass="agent-crumb-link" onAction="#handleClear"/>
    </HBox>
    <Label fx:id="errorLabel" styleClass="error-message"/>

    <ScrollPane fitToWidth="true" styleClass="agent-scroll" VBox.vgrow="ALWAYS">
        <VBox spacing="12">
            <VBox styleClass="agent-card">
                <TableView fx:id="table"/>
            </VBox>
            <Label fx:id="emptyLabel" styleClass="small"/>
            <Button fx:id="loadMoreButton" text="Load more" styleClass="outline-button"
                    onAction="#handleLoadMore" visible="false" managed="false"/>
        </VBox>
    </ScrollPane>
</VBox>
```

- [ ] **Step 4: Controller**

`src/main/java/com/snoozeshare/ui/admin/audit/AuditLogController.java`:

```java
package com.snoozeshare.ui.admin.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.service.AuditFilter;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

public final class AuditLogController {

    static final int PAGE_SIZE = 200;
    private static final String ALL_ACTIONS = "All action types";
    private static final String DASH = "\u2014";
    private static final double ROW_HEIGHT = 47;
    private static final double HEADER_HEIGHT = 34;
    private static final double TOTAL_SHARE = 9.4;
    private static final double WIDTH_FACTOR = 0.995;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> actionCombo;
    @FXML private DatePicker fromPicker;
    @FXML private DatePicker toPicker;
    @FXML private TableView<AuditLogEntry> table;
    @FXML private Label emptyLabel;
    @FXML private Label errorLabel;
    @FXML private Button loadMoreButton;

    private AppContext context;
    private AuditFilter applied = AuditFilter.none();

    @FXML
    private void initialize() {
        actionCombo.getItems().add(ALL_ACTIONS);
        for (AuditAction action : AuditAction.values()) {
            actionCombo.getItems().add(action.name());
        }
        actionCombo.getSelectionModel().selectFirst();
        searchField.setOnAction(event -> handleApply());

        table.getStyleClass().addAll("agent-table", "agent-audit-table");
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(new Label());
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.prefHeightProperty().bind(Bindings.max(1, Bindings.size(table.getItems()))
                .multiply(ROW_HEIGHT).add(HEADER_HEIGHT));
        table.minHeightProperty().bind(table.prefHeightProperty());
        table.maxHeightProperty().bind(table.prefHeightProperty());
        table.getColumns().add(textColumn("TIMESTAMP", 1.1,
                entry -> TIME.format(entry.timestamp().atZone(ZoneId.systemDefault())), null));
        table.getColumns().add(textColumn("ACTOR", 1.0,
                entry -> entry.actorName() == null ? "Unknown user" : entry.actorName(), "cell-strong"));
        table.getColumns().add(actionColumn());
        table.getColumns().add(textColumn("REF", 1.0, AuditLogController::refText, "cell-id"));
        table.getColumns().add(textColumn("STATUS", 1.5, AuditLogController::statusText, null));
        table.getColumns().add(textColumn("REASON", 2.4,
                entry -> entry.reason() == null ? DASH : entry.reason(), null));
        table.getColumns().add(textColumn("AMOUNT", 0.9, AuditLogController::amountText, "cell-strong"));
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        load(true);
    }

    @FXML
    private void handleApply() {
        if (fromPicker.getValue() != null && toPicker.getValue() != null
                && fromPicker.getValue().isAfter(toPicker.getValue())) {
            errorLabel.setText("The From date must not be after the To date.");
            return;
        }
        int selected = actionCombo.getSelectionModel().getSelectedIndex();
        AuditAction action = selected <= 0 ? null : AuditAction.values()[selected - 1];
        applied = new AuditFilter(searchField.getText(), action, fromPicker.getValue(), toPicker.getValue());
        load(true);
    }

    @FXML
    private void handleClear() {
        searchField.clear();
        actionCombo.getSelectionModel().selectFirst();
        fromPicker.setValue(null);
        toPicker.setValue(null);
        applied = AuditFilter.none();
        load(true);
    }

    @FXML
    private void handleLoadMore() {
        load(false);
    }

    private void load(boolean reset) {
        if (context == null) {
            return;
        }
        try {
            if (reset) {
                table.getItems().clear();
            }
            List<AuditLogEntry> page = context.auditService().search(applied, PAGE_SIZE, table.getItems().size());
            table.getItems().addAll(page);
            boolean more = page.size() == PAGE_SIZE;
            loadMoreButton.setVisible(more);
            loadMoreButton.setManaged(more);
            emptyLabel.setText(table.getItems().isEmpty() ? "No audit entries match these filters." : "");
            errorLabel.setText("");
        } catch (RuntimeException failure) {
            errorLabel.setText("Unable to load the audit log: " + failure.getMessage());
        }
    }

    private TableColumn<AuditLogEntry, String> textColumn(String title, double share,
            Function<AuditLogEntry, String> value, String cellClass) {
        TableColumn<AuditLogEntry, String> column = baseColumn(title, share);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("cell-id", "cell-strong");
                if (empty || item == null) {
                    setTooltip(null);
                    return;
                }
                if (cellClass != null) {
                    getStyleClass().add(cellClass);
                }
                if (item.length() > 24) {
                    Tooltip tip = new Tooltip(item);
                    tip.setWrapText(true);
                    tip.setMaxWidth(420);
                    setTooltip(tip);
                } else {
                    setTooltip(null);
                }
            }
        });
        return column;
    }

    private TableColumn<AuditLogEntry, AuditLogEntry> actionColumn() {
        TableColumn<AuditLogEntry, AuditLogEntry> column = baseColumn("ACTION TYPE", 1.5);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(AuditLogEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label pill = new Label(item.actionType());
                pill.getStyleClass().addAll("agent-pill", pillClass(item.actionType()));
                setGraphic(pill);
            }
        });
        return column;
    }

    private <T> TableColumn<AuditLogEntry, T> baseColumn(String title, double share) {
        TableColumn<AuditLogEntry, T> column = new TableColumn<>(title);
        column.setResizable(false);
        column.setReorderable(false);
        column.setSortable(false);
        DoubleBinding width = table.widthProperty().multiply(share * WIDTH_FACTOR / TOTAL_SHARE);
        column.prefWidthProperty().bind(width);
        column.minWidthProperty().bind(width);
        column.maxWidthProperty().bind(width);
        return column;
    }

    static String statusText(AuditLogEntry entry) {
        String before = label(entry.beforeState());
        String after = label(entry.afterState());
        if (before != null && after != null) {
            return before + " \u2192 " + after;
        }
        if (after != null) {
            return after;
        }
        return before != null ? before : DASH;
    }

    static String amountText(AuditLogEntry entry) {
        BigDecimal amount = entry.walletAdjustment();
        if (amount == null) {
            return DASH;
        }
        return (amount.signum() < 0 ? "-" : "+") + "SGD "
                + amount.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String refText(AuditLogEntry entry) {
        List<String> parts = new ArrayList<>();
        if (entry.bookingId() != null) {
            parts.add("Booking #" + lastFour(entry.bookingId().toString()));
        }
        if (entry.ticketId() != null) {
            parts.add("Ticket #" + lastFour(entry.ticketId().toString()));
        }
        return parts.isEmpty() ? DASH : String.join(" \u00b7 ", parts);
    }

    static String pillClass(String actionType) {
        return switch (actionType) {
            case "BOOKING_PAYOUT", "ESCROW_REFUND", "TICKET_REMEDY", "TOP_UP", "BOOKING_COMPLETED" ->
                    "agent-pill-success";
            case "AGENT_OVERRIDE", "TICKET_RESOLVED", "TICKET_OPENED", "TICKET_ASSIGNED", "TICKET_UNASSIGNED",
                    "TICKET_NOTE_SAVED" -> "agent-pill-accent";
            case "ACCOUNT_SUSPENDED", "ACCOUNT_REACTIVATED", "LISTING_STATUS_CASCADE", "LISTING_STATUS_CHANGED",
                    "BOOKING_CANCELLED_BY_GUEST", "BOOKING_CANCELLED_BY_HOST", "ESCROW_HOLD", "WITHDRAWAL" ->
                    "agent-pill-warning";
            case "BOOKING_REJECTED", "BOOKING_FORCE_CANCELLED", "TICKET_CATEGORY_DELETED" -> "agent-pill-danger";
            default -> "agent-pill-neutral";
        };
    }

    private static String lastFour(String id) {
        return id.substring(id.length() - 4);
    }

    /** "IN_REVIEW" becomes "In review"; null stays null. */
    private static String label(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String spaced = state.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
```

Note the test expects `In review → Resolved approved` (generic underscore→space label); this matches `label(...)`.

- [ ] **Step 5: Shell wiring and CSS**

`AdminShellController.showAuditLog()` (replace):

```java
    @FXML
    private void showAuditLog() {
        selectTab(auditTab);
        disposeQueue();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/admin/audit/audit-log.fxml"));
            Node view = loader.load();
            AuditLogController controller = loader.getController();
            controller.setContext(getContext());
            shellRoot.setCenter(view);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the audit log", exception);
        }
    }
```
(import `com.snoozeshare.ui.admin.audit.AuditLogController`.)

Append to `agent-theme.css`:

```css
/* ---- Audit log screen (W12) ---- */
.agent-root .agent-filter-card { -fx-padding: 14px 16px 14px 16px; }
.agent-root .agent-pill-neutral { -fx-background-color: bg-inset; -fx-text-fill: fg-muted; }
.agent-root .agent-audit-table .table-row-cell:filled { -fx-cursor: default; }
.agent-root .agent-audit-table .table-row-cell:hover { -fx-background-color: transparent; }
.agent-root .date-picker { -fx-background-color: transparent; -fx-padding: 0; }
.agent-root .date-picker > .text-field {
    -fx-background-color: bg-overlay;
    -fx-background-radius: 8px 0 0 8px;
    -fx-border-color: border;
    -fx-border-radius: 8px 0 0 8px;
    -fx-border-width: 1px 0 1px 1px;
    -fx-padding: 8px 12px 8px 12px;
    -fx-font-size: 12px;
}
.agent-root .date-picker > .arrow-button {
    -fx-background-color: bg-overlay;
    -fx-background-radius: 0 8px 8px 0;
    -fx-border-color: border;
    -fx-border-radius: 0 8px 8px 0;
    -fx-border-width: 1px;
    -fx-padding: 0 10px 0 6px;
}
.agent-root .date-picker > .arrow-button > .arrow { -fx-background-color: fg-subtle; }
.agent-root .date-picker:focused > .text-field,
.agent-root .date-picker:showing > .text-field { -fx-border-color: accent; }
.agent-root .date-picker:focused > .arrow-button,
.agent-root .date-picker:showing > .arrow-button { -fx-border-color: accent; }
```

- [ ] **Step 6: Verify**

Run: `./gradlew test --tests "com.snoozeshare.ui.admin.audit.AuditLogFormattingTest" --tests "com.snoozeshare.ui.admin.*"`
Expected: PASS (formatting tests green; existing admin UI tests unaffected — `AdminFxmlLayoutTest.adminShellHostsDisputesAndCategoriesInItsCenterPane` still finds `showAccounts`).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(audit): Agent Audit Log screen in the W10 agent theme"
```

---

### Task 10: UI integration tests and snapshot

**Files:**
- Modify: `src/test/java/com/snoozeshare/ui/admin/AdminFxmlLayoutTest.java`, `AdminUiSmokeTest.java`, `AdminUiSnapshotTest.java`

- [ ] **Step 1: Write the tests**

`AdminFxmlLayoutTest` — append:

```java
    @Test
    void auditLogScreenHasTheSearchActionDateFiltersAndTheTable() throws Exception {
        String audit = read("audit/audit-log.fxml");

        String[] ids = {"searchField", "actionCombo", "fromPicker", "toPicker", "applyButton", "clearLink",
            "table", "loadMoreButton", "errorLabel", "emptyLabel"};
        for (String id : ids) {
            assertTrue(audit.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(audit.contains("Audit log"));
        assertFalse(audit.contains("User ID"), "search replaces the board's separate User ID / Booking ID inputs");
        assertFalse(audit.toLowerCase().contains("force"));
        assertTrue(controller("AdminShellController.java").contains("audit-log.fxml"));
    }
```

`AdminUiSmokeTest` — add imports (`java.time.LocalDate`, `java.time.ZoneId`, `com.snoozeshare.domain.model.AuditLogEntry`, `com.snoozeshare.ui.admin.audit.AuditLogController`, `javafx.scene.control.ComboBox`, `javafx.scene.control.DatePicker`, `javafx.scene.control.Hyperlink`, `javafx.scene.control.TextField`, `static org.junit.jupiter.api.Assertions.assertTrue`) and append:

```java
    private static Object[] auditScreen(AppContext context) throws Exception {
        FXMLLoader loader = new FXMLLoader(AdminUiSmokeTest.class.getResource(
                "/com/snoozeshare/ui/admin/audit/audit-log.fxml"));
        loader.load();
        AuditLogController controller = loader.getController();
        controller.setContext(context);
        return new Object[] {loader.getNamespace(), controller};
    }

    @SuppressWarnings("unchecked")
    private static TableView<AuditLogEntry> auditTable(java.util.Map<String, Object> namespace) {
        return (TableView<AuditLogEntry>) namespace.get("table");
    }

    @Test
    void auditLogListsSeededRowsNewestFirstAndTheFiltersNarrowThem(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            long seeded = db.scalarLong("SELECT COUNT(*) FROM audit_log");

            Object[] result = onFx(() -> {
                @SuppressWarnings("unchecked")
                var ns = (java.util.Map<String, Object>) auditScreen(context)[0];
                var table = auditTable(ns);
                int all = table.getItems().size();
                boolean newestFirst = table.getItems().get(0).timestamp()
                        .compareTo(table.getItems().get(all - 1).timestamp()) >= 0;

                ((TextField) ns.get("searchField")).setText("Aria");
                ((Button) ns.get("applyButton")).fire();
                var byName = List.copyOf(table.getItems());

                ((Hyperlink) ns.get("clearLink")).fire();
                int afterClear = table.getItems().size();

                @SuppressWarnings("unchecked")
                ComboBox<String> combo = (ComboBox<String>) ns.get("actionCombo");
                combo.getSelectionModel().select("AGENT_OVERRIDE");
                ((Button) ns.get("applyButton")).fire();
                var byAction = List.copyOf(table.getItems());

                ((Hyperlink) ns.get("clearLink")).fire();
                ((DatePicker) ns.get("fromPicker")).setValue(LocalDate.of(2026, 8, 27));
                ((DatePicker) ns.get("toPicker")).setValue(LocalDate.of(2026, 8, 29));
                ((Button) ns.get("applyButton")).fire();
                var byDate = List.copyOf(table.getItems());
                return new Object[] {all, newestFirst, byName, afterClear, byAction, byDate};
            });

            assertEquals((int) seeded, result[0]);
            assertTrue((Boolean) result[1], "newest row first");
            @SuppressWarnings("unchecked")
            List<AuditLogEntry> byName = (List<AuditLogEntry>) result[2];
            assertFalse(byName.isEmpty());
            assertTrue(byName.stream().allMatch(row -> MockIds.GUEST_ARIA.equals(row.actorUserId())
                    || MockIds.GUEST_ARIA.equals(row.subjectUserId())));
            assertEquals(result[0], result[3], "Clear restores every row");
            @SuppressWarnings("unchecked")
            List<AuditLogEntry> byAction = (List<AuditLogEntry>) result[4];
            assertFalse(byAction.isEmpty());
            assertTrue(byAction.stream().allMatch(row -> row.actionType().equals("AGENT_OVERRIDE")));
            @SuppressWarnings("unchecked")
            List<AuditLogEntry> byDate = (List<AuditLogEntry>) result[5];
            assertFalse(byDate.isEmpty());
            var zone = ZoneId.systemDefault();
            assertTrue(byDate.stream().allMatch(row -> {
                LocalDate day = row.timestamp().atZone(zone).toLocalDate();
                return !day.isBefore(LocalDate.of(2026, 8, 27)) && !day.isAfter(LocalDate.of(2026, 8, 29));
            }));
        }
    }

    @Test
    void auditLogRejectsAFromDateAfterTheToDate(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            String message = onFx(() -> {
                @SuppressWarnings("unchecked")
                var ns = (java.util.Map<String, Object>) auditScreen(context)[0];
                ((DatePicker) ns.get("fromPicker")).setValue(LocalDate.of(2026, 9, 2));
                ((DatePicker) ns.get("toPicker")).setValue(LocalDate.of(2026, 9, 1));
                ((Button) ns.get("applyButton")).fire();
                return ((Labeled) ns.get("errorLabel")).getText();
            });

            assertEquals("The From date must not be after the To date.", message);
        }
    }
```

`AdminUiSnapshotTest` — append:

```java
    @Test
    void writesTheAuditLogSnapshot(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));

            Path audit = onFx(() -> {
                Shell shell = loadShell(context);
                shell.show("showAuditLog");
                return snapshot(shell.root(), "agent-audit-log", 1280, 800);
            });

            assertTrue(Files.size(audit) > 0);
        }
    }
```

- [ ] **Step 2: Run**

Run: `./gradlew test --tests "com.snoozeshare.ui.admin.*"`
Expected: PASS; `build/ui-snapshots/agent-audit-log.png` exists. (If the JavaFX toolkit is unavailable the FX tests are skipped via `assumeTrue`, which is the existing convention.)

- [ ] **Step 3: Visual comparison against the board (manual, required)**

Open `build/ui-snapshots/agent-audit-log.png` and compare with the board artboard `AgentAuditLog` (artifact `PWBCxbfv9e9FGVvY6RKUwd`): filter card border/padding, column header inset background, pill tones, amount weight, no pointer cursor on rows. Fix CSS (not the board) for any layout drift, re-run Step 2, and record the acceptance in the ledger. Also launch the real app on a copy of the mock DB to check the tab end to end: `cp db/snoozeshare-mock.db build/acceptance.db` then `SNOOZESHARE_DB_URL=jdbc:sqlite:build/acceptance.db ./gradlew run`, log in as `amy.tanaka@snoozeshare.test`, open Audit Log, search "Aria", pick an action, set dates, Clear.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test(audit): Audit Log screen layout, filter and snapshot tests"
```

---

### Task 11: Documentation, project state, and full verification

**Files:**
- Modify: `PROJECT_STATE.md`, `docs/project-state/done-ledger.md`, `docs/ProductBacklog.md`, `docs/superpowers/specs/2026-09-26-w12-platform-audit-trail-design.md` (three small corrections)

- [ ] **Step 1: Spec corrections** (the implementation refined three details)

In the spec: § 3 column table `actorName` type → `TEXT` "display-name snapshot at write time; always set by the service, null only on legacy rows"; § 7 "`ALTER TABLE ADD COLUMN` ×8" → "×7"; § 5 search semantics "hex id prefix of ≥ 6 chars" → "hex id fragment (contains match) of ≥ 4 chars"; § 6 REF labels are the last four characters (`Booking #0009`), matching the dispute queue's `#0004` ticket labels.

- [ ] **Step 2: Backlog** — in `docs/ProductBacklog.md` F11.1.3 reword to "filter audit logs by a single search (user name or User/Booking/Ticket ID), Action Type, and a From/To date range"; add a changelog entry dated 2026-09-26 referencing C28–C32 (operator approved).

- [ ] **Step 3: Full verification**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL (checkstyle, all tests). Record the test count.
Run: `sqlite3 db/snoozeshare-mock.db "PRAGMA foreign_key_check;"` — expected: no output.
Confirm `git status` shows no stray files (only `build/` is ignored).

- [ ] **Step 4: `PROJECT_STATE.md`** (per AGENTS.md § 2; do in one write)

- § 3 W12 row: Status `Building` while tasks run → `In review` when Step 3 passes; **Progress** ≤ 15 words (e.g. `Task 11/11: docs + verification`); Plan cell links this file; **Guide** stays `—` until the operator confirms `Done`.
- § 2 session S9: update Doing/Last touched.
- § 4 Architecture: add a `### 4.8 Audit trail` subsection (schema summary, one-change rule, `AuditRecord`/`AuditService.search`, System user, dual-write until W14) and mention the Audit Log screen under § 4.2 "W10 Agent screens".
- § 9: mark D2 point 2 **RESOLVED by C28/C32** (audit fields are columns; no projection layer) and point 1 resolved by the `ACCOUNT_SUSPENDED` row's `reason` (C32); note that `AuditService.query(...)` was replaced by `search(...)` and old `CATEGORY_DELETED` renamed `TICKET_CATEGORY_DELETED`; add a Deviation for the dual-write of wallet rows until W14.
- § 5 Conventions: replace the "Every mutating service method has exactly one `AuditService.record(...)`" bullet with "One row per change; a state change and its money movements are separate rows written in the same DB transaction; use `AuditRecord.builder`".
- § 6 Known Gaps: unchanged (platform wallet still open; C31 reverses it in W14).
- § 10 Record: update latest date/entry count.

- [ ] **Step 5: Ledger** — add one line per meaningful change, newest first, to `docs/project-state/done-ledger.md` (columns/migration, service API + instrumentation, mock DB reseed, Audit Log screen, tests), each referencing this plan.

- [ ] **Step 6: Guide checkpoint** — per AGENTS.md § 5, ask the operator to confirm W12 `Done`; on yes set Guide `Awaiting confirmation` → `Pending` and propose the DeveloperGuide update; write nothing to the guide until approved.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "docs: W12 audit trail state, backlog and ledger"
```
