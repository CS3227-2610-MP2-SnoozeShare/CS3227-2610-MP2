# W11 Agent Account Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Support Agent a working Accounts tab: list and search accounts, suspend a Guest or Host (with a required reason and an atomic cascade), and reactivate a suspended account.

**Architecture:** A new `AccountGovernanceService` (interface + impl) does the whole suspend/reactivate operation in one `TransactionManager` transaction with inline wallet writes (the same technique `BookingServiceImpl` uses, because `WalletLedgerWriter` opens its own transaction). Audit rows use W12's `AuditService.record(AuditRecord)` shapes. A nullable `users.suspensionReason` column (migration V003) holds the display reason. The UI is `ui.admin.accounts`: a fixed-header table screen (same pattern as the Categories screen) and one `AgentModal` card used for both Suspend and Reactivate.

**Tech Stack:** Java 25, JavaFX 25 (FXML), SQLite via plain JDBC, JUnit 5, Gradle + Checkstyle (line length 120; import order: static, `java.*`, `org.*`, `com.*`, then `javafx.*`, each block alphabetical).

**Spec:** [`docs/superpowers/specs/2026-09-26-w11-account-governance-design.md`](../specs/2026-09-26-w11-account-governance-design.md). Decisions C34, C35, C36 in `PROJECT_STATE.md`.

**Conventions for every task**
- Working directory `S:\CS3227\CS3227-2610-MP2`, branch `agent-account-governance`. Commands are POSIX shell (Git Bash). Gradle: `./gradlew`.
- Commit after each task with a message ending in the line `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Money assertions read back from SQLite use `compareTo`, never `equals` (D4).
- `PROJECT_STATE.md` is updated at the start (Task 1 Step 1) and at the end (Task 14); update the session row's **Doing** cell whenever a task finishes (one short phrase).

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `src/main/resources/db/migration/V003__suspension_reason.sql` | Create | Adds `users.suspensionReason` |
| `src/main/java/com/snoozeshare/infra/db/migration/MigrationRunner.java` | Modify | Applies/adopts V003 |
| `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db` | Modify | Column, seeded reasons, v3 history; rebuilt DB |
| `src/main/java/com/snoozeshare/domain/model/User.java` | Modify | `suspensionReason` component (7-arg constructor kept) |
| `repository/UserRepository.java`, `repository/jdbc/JdbcUserRepository.java`, `repository/jdbc/support/RowMappers.java` | Modify | `findAll()`, persist/read the reason |
| `repository/BookingRepository.java`, `repository/jdbc/JdbcBookingRepository.java` | Modify | `findByListing(UUID)` |
| `infra/events/events/AccountStatusChangedEvent.java` | Create | Published after a commit so the UI refreshes |
| `service/AccountSummary.java`, `service/AccountGovernanceService.java` | Create | Read model and contract |
| `service/impl/AccountGovernanceServiceImpl.java` | Create | Validation, suspend + cascade, reactivate, audit |
| `service/impl/BookingServiceImpl.java`, `app/AppContext.java` | Modify | Suspended-guest guard; wire the new service |
| `service/UserService.java`, `service/impl/UserServiceImpl.java` | Modify | Remove the `suspend` stub (D19) |
| `ui/admin/accounts/AccountText.java`, `AccountSearch.java` | Create | Display texts, `DD MMM YYYY`, live-search matching |
| `ui/admin/accounts/SuspensionDialogController.java` + `suspension-dialog.fxml` | Create | The Suspend / Reactivate modal card |
| `ui/admin/accounts/AccountGovernanceController.java` + `account-governance.fxml` | Create | The Accounts screen |
| `ui/admin/AdminShellController.java` | Modify | `showAccounts()` loads the screen; disposes its subscription |
| `ui/admin/agent-theme.css` | Modify | Banner name/email lines, dialog info note, row hover, status cell |
| tests (listed per task) | Create/Modify | TDD |
| `PROJECT_STATE.md`, `docs/project-state/done-ledger.md` | Modify | State, ledger |

---

### Task 1: Migration V003, schema, seed and mock DB

**Files:**
- Create: `src/main/resources/db/migration/V003__suspension_reason.sql`
- Modify: `src/main/java/com/snoozeshare/infra/db/migration/MigrationRunner.java`
- Modify: `db/schema.sql:10-18`, `db/seed-mock-data.sql` (end of file), `db/snoozeshare-mock.db` (rebuilt)
- Test: `src/test/java/com/snoozeshare/infra/db/SchemaParityTest.java`, `CommittedMockDbTest.java`, `MigrationRunnerReferenceDbTest.java`, `DatabaseBootstrapTest.java`, new `SuspensionReasonMigrationTest.java`

- [ ] **Step 1: Record the plan in PROJECT_STATE.md**

In the `W11` row of § Workstreams set the Plan cell to `[W11 plan](docs/superpowers/plans/2026-09-26-w11-account-governance.md)`, Status to `Planned`, Progress to `Plan written; Task 0/14`. Update session row S7 Doing to `Plan written; executing Task 1`.

- [ ] **Step 2: Write the failing migration test**

Create `src/test/java/com/snoozeshare/infra/db/SuspensionReasonMigrationTest.java`:

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class SuspensionReasonMigrationTest {

    @Test
    void freshDatabaseGetsANullableSuspensionReasonColumnAndVersionThree() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);

            try (Statement statement = connection.createStatement();
                 ResultSet column = statement.executeQuery(
                         "SELECT \"notnull\" FROM pragma_table_info('users') WHERE name = 'suspensionReason'")) {
                assertEquals(true, column.next(), "users.suspensionReason exists");
                assertEquals(0, column.getInt(1), "and is nullable");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet history = statement.executeQuery(
                         "SELECT COUNT(*) FROM schema_history WHERE version = 3")) {
                history.next();
                assertEquals(1, history.getInt(1));
            }
        }
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests "*SuspensionReasonMigrationTest" -q`
Expected: FAIL (`users.suspensionReason exists` is false).

- [ ] **Step 4: Create the migration file**

`src/main/resources/db/migration/V003__suspension_reason.sql` (one statement; the runner splits on `;`):

```sql
ALTER TABLE users ADD COLUMN suspensionReason TEXT;
```

- [ ] **Step 5: Teach `MigrationRunner` V003**

In `MigrationRunner.java` add the constant after `AUDIT_TRAIL_VERSION`:

```java
    private static final int SUSPENSION_REASON_VERSION = 3;
```

and, inside `migrate`, directly after the `AUDIT_TRAIL_VERSION` block (before `connection.commit();`):

```java
            if (!migrationApplied(connection, SUSPENSION_REASON_VERSION)) {
                if (!columnExists(connection, "users", "suspensionReason")) {
                    applyMigrationFile(connection, "/db/migration/V003__suspension_reason.sql");
                }
                // else: a reference database rebuilt from db/schema.sql already has the column.
                recordMigration(connection, SUSPENSION_REASON_VERSION);
            }
```

- [ ] **Step 6: Update `db/schema.sql`**

In the `users` table add the column after `createdAt`:

```sql
    createdAt           TEXT NOT NULL,
    suspensionReason    TEXT
);
```

(`createdAt           TEXT NOT NULL` gains a trailing comma; the closing `);` stays.)

- [ ] **Step 7: Update the seed**

In `db/seed-mock-data.sql`, directly above the `-- ===== schema_history =====` heading add:

```sql
-- ============================== suspension reasons ==============================
-- The two seeded suspended accounts show the same reason their ACCOUNT_SUSPENDED audit rows carry (W11, C34).
UPDATE users SET suspensionReason = 'Suspended by support agent pending review'
WHERE userId IN ('c0000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000006');

```

and change the schema_history block to:

```sql
-- The reference DB ships fully migrated (V001 + V002 + V003), so the app's MigrationRunner has nothing to apply.
INSERT INTO schema_history (version, appliedAt) VALUES (1, '2026-09-26 00:00:00'), (2, '2026-09-26 00:00:00'), (3, '2026-09-26 00:00:00');
```

- [ ] **Step 8: Rebuild the committed mock DB**

```bash
rm db/snoozeshare-mock.db
sqlite3 db/snoozeshare-mock.db < db/schema.sql
sqlite3 db/snoozeshare-mock.db < db/seed-mock-data.sql
sqlite3 db/snoozeshare-mock.db "PRAGMA foreign_key_check; SELECT version FROM schema_history; SELECT displayName, suspensionReason FROM users WHERE suspensionReason IS NOT NULL;"
```

Expected: no `foreign_key_check` rows; versions `1 2 3`; two rows (Kai Nakamura, Sam O'Connor) with the reason.

- [ ] **Step 9: Update the existing tests**

- `SchemaParityTest.java`: after the `V002__audit_trail.sql` apply line add
  `apply(migration, "src/main/resources/db/migration/V003__suspension_reason.sql");` and add `"users"` is already in `TABLES` (no change).
- `DatabaseBootstrapTest.java:38`: `assertEquals(2, migrationCount(connection));` -> `assertEquals(3, migrationCount(connection));`.
- `CommittedMockDbTest.java`: change `for (int version : new int[] {1, 2}) {` to `new int[] {1, 2, 3}`, rename the test to `theCommittedFileHasTheAuditColumnsTheSystemUserAndMigrationVersionThree`, and append before the final closing braces of the test body:

```java
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users "
                    + "WHERE suspensionReason IS NOT NULL AND accountStatus = 'SUSPENDED'")) {
                result.next();
                assertEquals(2, result.getInt(1), "the two seeded suspended accounts carry a reason");
            }
```
- `MigrationRunnerReferenceDbTest.java`: in `theShippedReferenceDatabaseIsAlreadyMigratedSoMigrateChangesNothing` add after the version-2 assert
  `assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM schema_history WHERE version = 3"));`; in `adoptsAFoundationOnlyReferenceDatabaseAndUpgradesItToV002` add after its version-2 assert
  `assertEquals(1L, scalar(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 3"));`; in `adoptsAReferenceDatabaseRebuiltFromSchemaSqlWithoutHistory` change `assertEquals(2L, scalar(connection, "SELECT COUNT(*) FROM schema_history"));` to `assertEquals(3L, ...)`.

- [ ] **Step 10: Run the DB tests**

Run: `./gradlew test --tests "com.snoozeshare.infra.db.*" -q`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat(w11): users.suspensionReason column (V003), schema, seed and rebuilt mock DB

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: `User.suspensionReason` and `UserRepository.findAll`

**Files:**
- Modify: `src/main/java/com/snoozeshare/domain/model/User.java`
- Modify: `src/main/java/com/snoozeshare/repository/UserRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcUserRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java:34-42`
- Modify: `src/test/java/com/snoozeshare/testsupport/Fakes.java` (`StubUsers`, line ~110)
- Test: `src/test/java/com/snoozeshare/repository/JdbcUserRepositoryTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/snoozeshare/repository/JdbcUserRepositoryTest.java`:

```java
package com.snoozeshare.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;

class JdbcUserRepositoryTest {

    private static Connection migrated() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }

    @Test
    void suspensionReasonPersistsAndClears() throws Exception {
        try (Connection connection = migrated()) {
            var users = new JdbcUserRepository(connection);
            UUID id = UUID.randomUUID();
            Instant created = Instant.parse("2026-03-05T08:00:00Z");
            users.save(new User(id, Role.GUEST, "Priya", "priya@test.com", AccountStatus.ACTIVE, null, created));
            assertNull(users.findById(id).orElseThrow().suspensionReason());

            users.save(new User(id, Role.GUEST, "Priya", "priya@test.com", AccountStatus.SUSPENDED, null, created,
                    "late cancellations"));
            User suspended = users.findById(id).orElseThrow();
            assertEquals(AccountStatus.SUSPENDED, suspended.accountStatus());
            assertEquals("late cancellations", suspended.suspensionReason());

            users.save(new User(id, Role.GUEST, "Priya", "priya@test.com", AccountStatus.ACTIVE, null, created,
                    null));
            assertNull(users.findById(id).orElseThrow().suspensionReason());
        }
    }

    @Test
    void findAllReturnsEveryUserOldestFirst() throws Exception {
        try (Connection connection = migrated()) {
            var users = new JdbcUserRepository(connection);
            User late = new User(UUID.randomUUID(), Role.HOST, "Late", "late@test.com", AccountStatus.ACTIVE,
                    "HOST2026", Instant.parse("2026-05-01T00:00:00Z"));
            User early = new User(UUID.randomUUID(), Role.GUEST, "Early", "early@test.com", AccountStatus.ACTIVE,
                    null, Instant.parse("2026-04-01T00:00:00Z"));
            users.save(late);
            users.save(early);

            List<String> names = users.findAll().stream().map(User::displayName).toList();

            // The migration seeds the System user (2026-01-01), which sorts first.
            assertEquals(List.of("SnoozeShare System", "Early", "Late"), names);
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*JdbcUserRepositoryTest" -q`
Expected: FAIL to compile (`suspensionReason`, 8-arg `User`, `findAll` are missing).

- [ ] **Step 3: Extend `User`**

Replace the body of `User.java` (keep the package/imports) with:

```java
public record User(
        UUID userId,
        Role role,
        String displayName,
        String email,
        AccountStatus accountStatus,
        String registrationCode,
        Instant createdAt,
        String suspensionReason
) {
    /** A user with no suspension reason (every account that is not suspended by an agent). */
    public User(UUID userId, Role role, String displayName, String email, AccountStatus accountStatus,
                String registrationCode, Instant createdAt) {
        this(userId, role, displayName, email, accountStatus, registrationCode, createdAt, null);
    }
}
```

- [ ] **Step 4: `UserRepository.findAll`**

Add to the interface (after `findByRole`):

```java
    /** Every user, oldest first (createdAt, then id). */
    List<User> findAll();
```

- [ ] **Step 5: Update `RowMappers.user`**

Replace the `return new User(...)` in `RowMappers.user` with:

```java
        return new User(
                JdbcCodecs.uuid(result.getString("userId")),
                Role.valueOf(result.getString("role")),
                result.getString("displayName"),
                result.getString("email"),
                AccountStatus.valueOf(result.getString("accountStatus")),
                result.getString("registrationCode"),
                JdbcCodecs.instant(result.getString("createdAt")),
                result.getString("suspensionReason"));
```

- [ ] **Step 6: Update `JdbcUserRepository`**

Add a shared constant and use it in all three SELECTs, then add `findAll` and persist the reason. Replace the file body after the constructor with:

```java
    private static final String COLUMNS = "userId, role, displayName, email, accountStatus, registrationCode, "
            + "createdAt, suspensionReason";

    @Override
    public Optional<User> findById(UUID userId) {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE userId = ?", userId.toString());
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE email = ?", email);
    }

    @Override
    public List<User> findByRole(Role role) {
        return findMany("SELECT " + COLUMNS + " FROM users WHERE role = ?", role.name());
    }

    @Override
    public List<User> findAll() {
        return findMany("SELECT " + COLUMNS + " FROM users ORDER BY createdAt, userId");
    }

    @Override
    public User save(User user) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO users (userId, role, displayName, email, accountStatus, "
                        + "registrationCode, createdAt, suspensionReason) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(userId) DO UPDATE SET role = excluded.role, "
                        + "displayName = excluded.displayName, email = excluded.email, "
                        + "accountStatus = excluded.accountStatus, "
                        + "registrationCode = excluded.registrationCode, "
                        + "suspensionReason = excluded.suspensionReason")) {
            statement.setString(1, JdbcCodecs.uuid(user.userId()));
            statement.setString(2, user.role().name());
            statement.setString(3, user.displayName());
            statement.setString(4, user.email());
            statement.setString(5, user.accountStatus().name());
            statement.setString(6, user.registrationCode());
            statement.setString(7, JdbcCodecs.instant(user.createdAt()));
            statement.setString(8, user.suspensionReason());
            statement.executeUpdate();
            return user;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save user", exception);
        }
    }

    private List<User> findMany(String sql, String... values) {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            try (var result = statement.executeQuery()) {
                var users = new java.util.ArrayList<User>();
                while (result.next()) {
                    users.add(RowMappers.user(result));
                }
                return users;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query users", exception);
        }
    }

    private Optional<User> findOne(String sql, String value) {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(RowMappers.user(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query user", exception);
        }
    }
}
```

- [ ] **Step 7: Update `Fakes.StubUsers`**

In `Fakes.java` inside `StubUsers`, after `findByRole` add:

```java
        @Override
        public List<User> findAll() {
            return store.values().stream()
                    .sorted(java.util.Comparator.comparing(User::createdAt).thenComparing(User::userId))
                    .toList();
        }
```

- [ ] **Step 8: Run the new test and the whole suite**

Run: `./gradlew test -q`
Expected: PASS (the 7-arg constructor keeps every existing `new User(...)` compiling).

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(w11): User.suspensionReason and UserRepository.findAll

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: `BookingRepository.findByListing`

**Files:**
- Modify: `src/main/java/com/snoozeshare/repository/BookingRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcBookingRepository.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcBookingRepositoryFindByListingTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;

class JdbcBookingRepositoryFindByListingTest {

    @Test
    void returnsEveryBookingOfOnePropertyRegardlessOfStatus() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            var users = new JdbcUserRepository(connection);
            var properties = new JdbcPropertyRepository(connection);
            var bookings = new JdbcBookingRepository(connection);
            Instant now = Instant.parse("2026-09-01T00:00:00Z");
            User host = users.save(new User(UUID.randomUUID(), Role.HOST, "H", "h@test.com",
                    AccountStatus.ACTIVE, "HOST2026", now));
            User guest = users.save(new User(UUID.randomUUID(), Role.GUEST, "G", "g@test.com",
                    AccountStatus.ACTIVE, null, now));
            Property one = properties.save(property(host, now));
            Property other = properties.save(property(host, now));
            bookings.save(booking(one, guest, BookingStatus.PENDING, now));
            bookings.save(booking(one, guest, BookingStatus.COMPLETED, now));
            bookings.save(booking(other, guest, BookingStatus.PENDING, now));

            assertEquals(2, bookings.findByListing(one.propertyId()).size());
            assertEquals(1, bookings.findByListing(other.propertyId()).size());
        }
    }

    private static Property property(User host, Instant now) {
        return new Property(UUID.randomUUID(), host.userId(), ListingStatus.ACTIVE, "T", "D",
                PropertyType.APARTMENT, "1 St", "Singapore", "Central", "123456", 2, 1, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0), Set.of(), now);
    }

    private static Booking booking(Property property, User guest, BookingStatus status, Instant now) {
        return new Booking(UUID.randomUUID(), property.propertyId(), guest.userId(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), status, new BigDecimal("100.00"),
                new BigDecimal("200.00"), now, null, null);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*FindByListingTest" -q`
Expected: FAIL to compile (`findByListing` missing).

- [ ] **Step 3: Add the interface method**

In `BookingRepository.java` after `findByGuest`:

```java
    /** Every booking of one property, any status, oldest first. */
    List<Booking> findByListing(UUID propertyId);
```

- [ ] **Step 4: Implement it** in `JdbcBookingRepository.java` (after `findByGuest`):

```java
    @Override
    public List<Booking> findByListing(UUID propertyId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM bookings WHERE listingId = ? ORDER BY createdAt")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            try (var result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(RowMappers.booking(result));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query bookings by listing", exception);
        }
    }
```

- [ ] **Step 5: Run and commit**

Run: `./gradlew test --tests "*FindByListingTest" -q` → PASS.

```bash
git add -A
git commit -m "feat(w11): BookingRepository.findByListing

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Service contract, event, read model and `listAccounts`

**Files:**
- Create: `src/main/java/com/snoozeshare/infra/events/events/AccountStatusChangedEvent.java`
- Create: `src/main/java/com/snoozeshare/service/AccountSummary.java`
- Create: `src/main/java/com/snoozeshare/service/AccountGovernanceService.java`
- Create: `src/main/java/com/snoozeshare/service/impl/AccountGovernanceServiceImpl.java` (skeleton; grown in Tasks 5–7)
- Create: `src/test/java/com/snoozeshare/service/AccountFixture.java`
- Test: `src/test/java/com/snoozeshare/service/AccountGovernanceServiceTest.java` (create)

- [ ] **Step 1: Create the event**

```java
package com.snoozeshare.infra.events.events;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.infra.events.DomainEvent;

/** An account was suspended or reactivated; published after the change commits so the Accounts screen refreshes. */
public record AccountStatusChangedEvent(UUID userId, AccountStatus newStatus, UUID actorId, Instant occurredAt,
                                        UUID eventId) implements DomainEvent {
    public AccountStatusChangedEvent(UUID userId, AccountStatus newStatus, UUID actorId, Instant occurredAt) {
        this(userId, newStatus, actorId, occurredAt, UUID.randomUUID());
    }
}
```

- [ ] **Step 2: Create the read model**

```java
package com.snoozeshare.service;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

/** One row of the agent Accounts screen. */
public record AccountSummary(UUID userId, String displayName, String email, Role role, Instant createdAt,
                             AccountStatus status, String suspensionReason) {

    public static AccountSummary from(User user) {
        return new AccountSummary(user.userId(), user.displayName(), user.email(), user.role(), user.createdAt(),
                user.accountStatus(), user.suspensionReason());
    }

    /** Only Guest and Host accounts can be suspended or reactivated (C34). */
    public boolean governable() {
        return role != Role.AGENT;
    }
}
```

- [ ] **Step 3: Create the contract**

```java
package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.User;

/** Agent account governance (F10): list accounts, suspend with a cascade, reactivate. */
public interface AccountGovernanceService {

    /** Every listed account, oldest first. The internal System user is not listed. */
    List<AccountSummary> listAccounts();

    /**
     * Suspends a Guest or Host and, in the same transaction, force-cancels their PENDING and not-yet-started
     * CONFIRMED bookings with a full refund and deactivates a host's ACTIVE listings (F10.1.2, C36).
     */
    User suspend(UUID userId, UUID agentId, String reason);

    /** Reactivates a suspended Guest or Host. Cancelled bookings and deactivated listings stay as they are. */
    User reactivate(UUID userId, UUID agentId, String reason);
}
```

- [ ] **Step 4: Create the service skeleton**

```java
package com.snoozeshare.service.impl;

import java.sql.Connection;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.AccountGovernanceService;
import com.snoozeshare.service.AccountSummary;
import com.snoozeshare.service.AuditService;

public final class AccountGovernanceServiceImpl implements AccountGovernanceService {

    static final String CASCADE_BOOKING_REASON = "Account suspended \u2014 cascading cancellation";
    static final String CASCADE_LISTING_REASON = "Host suspended";

    private final Connection connection;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final AvailabilityBlockRepository blocks;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final EventBus eventBus;
    private final AuditService audit;
    private final Clock clock;

    public AccountGovernanceServiceImpl(Connection connection, UserRepository users, BookingRepository bookings,
                                        PropertyRepository properties, AvailabilityBlockRepository blocks,
                                        WalletRepository wallets, WalletTransactionRepository transactions,
                                        EventBus eventBus, AuditService audit, Clock clock) {
        this.connection = connection;
        this.users = users;
        this.bookings = bookings;
        this.properties = properties;
        this.blocks = blocks;
        this.wallets = wallets;
        this.transactions = transactions;
        this.eventBus = eventBus;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<AccountSummary> listAccounts() {
        return users.findAll().stream()
                .filter(user -> !user.userId().equals(AuditService.SYSTEM_ACTOR_ID))
                .map(AccountSummary::from)
                .toList();
    }

    @Override
    public User suspend(UUID userId, UUID agentId, String reason) {
        throw new UnsupportedOperationException("Task 5");
    }

    @Override
    public User reactivate(UUID userId, UUID agentId, String reason) {
        throw new UnsupportedOperationException("Task 5");
    }
}
```

(The two `UnsupportedOperationException` bodies are replaced in Task 5; they are scaffolding, not part of the final code.)

- [ ] **Step 5: Create the shared test fixture**

`src/test/java/com/snoozeshare/service/AccountFixture.java`:

```java
package com.snoozeshare.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.AccountGovernanceServiceImpl;
import com.snoozeshare.service.impl.AuditServiceImpl;

/** An in-memory migrated database with one agent, guests, hosts, listings and bookings at a fixed "today". */
final class AccountFixture implements AutoCloseable {

    static final Instant NOW = Instant.parse("2026-09-26T04:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 9, 26);
    static final BigDecimal START_BALANCE = new BigDecimal("1000.00");

    final Connection connection;
    final JdbcUserRepository users;
    final JdbcPropertyRepository properties;
    final JdbcBookingRepository bookings;
    final JdbcAvailabilityBlockRepository blocks;
    final JdbcWalletRepository wallets;
    final JdbcWalletTransactionRepository transactions;
    final InProcessEventBus bus = new InProcessEventBus();
    final AuditService audit;
    final User agent;

    AccountFixture() throws Exception {
        connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        users = new JdbcUserRepository(connection);
        properties = new JdbcPropertyRepository(connection);
        bookings = new JdbcBookingRepository(connection);
        blocks = new JdbcAvailabilityBlockRepository(connection);
        wallets = new JdbcWalletRepository(connection);
        transactions = new JdbcWalletTransactionRepository(connection);
        audit = new AuditServiceImpl(new JdbcAuditLogRepository(connection), users, CLOCK);
        agent = user(Role.AGENT, "Amy Tanaka", "amy@test.com", "2026-01-10T09:00:00Z");
    }

    AccountGovernanceServiceImpl service() {
        return service(audit);
    }

    AccountGovernanceServiceImpl service(AuditService auditService) {
        return new AccountGovernanceServiceImpl(connection, users, bookings, properties, blocks, wallets,
                transactions, bus, auditService, CLOCK);
    }

    /** Creates a user; guests and hosts get a wallet holding {@link #START_BALANCE}. */
    User user(Role role, String name, String email, String createdAt) {
        User user = users.save(new User(UUID.randomUUID(), role, name, email, AccountStatus.ACTIVE,
                role == Role.GUEST ? null : "CODE", Instant.parse(createdAt)));
        if (role != Role.AGENT) {
            wallets.save(new Wallet(UUID.randomUUID(), user.userId(), START_BALANCE, "SGD", NOW));
        }
        return user;
    }

    Property property(User host, ListingStatus status) {
        return properties.save(new Property(UUID.randomUUID(), host.userId(), status, "Loft", "Nice",
                PropertyType.APARTMENT, "1 Street", "Singapore", "Central", "123456", 2, 1, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0), Set.of(), NOW));
    }

    /**
     * Creates a booking with its escrow hold already taken from the guest's wallet (and, for PENDING and
     * CONFIRMED, its availability block), so refunds keep the ledger balanced.
     */
    Booking booking(User guest, Property property, BookingStatus status, LocalDate start, LocalDate end) {
        long nights = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        BigDecimal total = property.baseNightlyRate().multiply(BigDecimal.valueOf(nights));
        Booking booking = bookings.save(new Booking(UUID.randomUUID(), property.propertyId(), guest.userId(), start,
                end, status, property.baseNightlyRate(), total, NOW.minusSeconds(86_400), null, null));
        Wallet wallet = wallets.findByUserId(guest.userId()).orElseThrow();
        BigDecimal after = wallet.balance().subtract(total);
        wallets.save(new Wallet(wallet.walletId(), guest.userId(), after, "SGD", NOW));
        transactions.save(new WalletTransaction(UUID.randomUUID(), wallet.walletId(),
                WalletTransactionType.ESCROW_HOLD, total.negate(), BigDecimal.ZERO, after, booking.bookingId(),
                null, guest.userId(), NOW.minusSeconds(86_400)));
        if (status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED) {
            blocks.save(new AvailabilityBlock(UUID.randomUUID(), property.propertyId(), start, end, "BOOKING",
                    booking.bookingId(), null));
        }
        return booking;
    }

    BigDecimal balance(User user) {
        return wallets.findByUserId(user.userId()).orElseThrow().balance();
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
```

- [ ] **Step 6: Write the failing test**

`src/test/java/com/snoozeshare/service/AccountGovernanceServiceTest.java`:

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

class AccountGovernanceServiceTest {

    @Test
    void listAccountsIsOldestFirstIncludesAgentsAndHidesTheSystemUser() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            fixture.user(Role.GUEST, "Guest Later", "later@test.com", "2026-03-05T08:00:00Z");
            fixture.user(Role.HOST, "Host Earlier", "earlier@test.com", "2026-02-01T10:00:00Z");

            List<AccountSummary> accounts = fixture.service().listAccounts();

            assertEquals(List.of("Amy Tanaka", "Host Earlier", "Guest Later"),
                    accounts.stream().map(AccountSummary::displayName).toList());
            assertFalse(accounts.stream().anyMatch(a -> a.displayName().equals("SnoozeShare System")));
            assertTrue(accounts.get(1).governable());
            assertFalse(accounts.get(0).governable(), "agents are listed but not governable");
            assertEquals(AccountStatus.ACTIVE, accounts.get(1).status());
        }
    }

    @Test
    void aSuspendedAccountShowsItsReason() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            fixture.users.save(new User(guest.userId(), guest.role(), guest.displayName(), guest.email(),
                    AccountStatus.SUSPENDED, null, guest.createdAt(), "late cancellations"));

            AccountSummary summary = fixture.service().listAccounts().stream()
                    .filter(a -> a.displayName().equals("Priya")).findFirst().orElseThrow();

            assertEquals(AccountStatus.SUSPENDED, summary.status());
            assertEquals("late cancellations", summary.suspensionReason());
        }
    }
}
```

- [ ] **Step 7: Run and verify**

Run: `./gradlew test --tests "*AccountGovernanceServiceTest" -q` → PASS (the skeleton already implements `listAccounts`; if it fails to compile, fix the imports of the fixture).

- [ ] **Step 8: Checkstyle-safe build and commit**

Run: `./gradlew checkstyleMain checkstyleTest -q` → no violations.

```bash
git add -A
git commit -m "feat(w11): account governance contract, event, read model and listAccounts

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Suspend/reactivate core — validation, status, reason, account audit, event

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/impl/AccountGovernanceServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/AccountGovernanceServiceTest.java`

- [ ] **Step 1: Add failing tests** (append inside the test class; add the imports `assertThrows`, `assertNull`, `assertNotNull`, `java.util.ArrayList`, `java.util.Set`, `com.snoozeshare.domain.enums.AuditAction`, `com.snoozeshare.domain.model.AuditLogEntry`, `com.snoozeshare.infra.events.events.AccountStatusChangedEvent`)

```java
    private static User guest(AccountFixture fixture) {
        return fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
    }

    private static List<AuditLogEntry> auditFor(AccountFixture fixture, AuditAction action) {
        return fixture.audit.search(new AuditFilter(null, Set.of(action), null, null), 50, 0);
    }

    @Test
    void suspendStoresTheTrimmedReasonAuditsItAndPublishesAfterCommit() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = guest(fixture);
            List<AccountStatusChangedEvent> events = new ArrayList<>();
            fixture.bus.subscribe(AccountStatusChangedEvent.class, events::add);

            User suspended = fixture.service().suspend(guest.userId(), fixture.agent.userId(),
                    "  repeated late cancellations  ");

            assertEquals(AccountStatus.SUSPENDED, suspended.accountStatus());
            User stored = fixture.users.findById(guest.userId()).orElseThrow();
            assertEquals(AccountStatus.SUSPENDED, stored.accountStatus());
            assertEquals("repeated late cancellations", stored.suspensionReason());
            AuditLogEntry row = auditFor(fixture, AuditAction.ACCOUNT_SUSPENDED).get(0);
            assertEquals("User", row.entityType());
            assertEquals(guest.userId(), row.entityId());
            assertEquals("ACTIVE", row.beforeState());
            assertEquals("SUSPENDED", row.afterState());
            assertEquals("repeated late cancellations", row.reason());
            assertEquals(fixture.agent.userId(), row.actorUserId());
            assertEquals(guest.userId(), row.subjectUserId());
            assertEquals(1, events.size());
            assertEquals(AccountStatus.SUSPENDED, events.get(0).newStatus());
        }
    }

    @Test
    void reactivateClearsTheReasonAndAudits() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = guest(fixture);
            fixture.service().suspend(guest.userId(), fixture.agent.userId(), "late cancellations");

            User reactivated = fixture.service().reactivate(guest.userId(), fixture.agent.userId(),
                    "appeal accepted");

            assertEquals(AccountStatus.ACTIVE, reactivated.accountStatus());
            assertNull(fixture.users.findById(guest.userId()).orElseThrow().suspensionReason());
            AuditLogEntry row = auditFor(fixture, AuditAction.ACCOUNT_REACTIVATED).get(0);
            assertEquals("SUSPENDED", row.beforeState());
            assertEquals("ACTIVE", row.afterState());
            assertEquals("appeal accepted", row.reason());
        }
    }

    @Test
    void validationRejectsBadRequestsAndChangesNothing() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = guest(fixture);
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            var service = fixture.service();
            var agentId = fixture.agent.userId();

            assertThrows(IllegalArgumentException.class, () -> service.suspend(guest.userId(), agentId, "  "));
            assertThrows(IllegalArgumentException.class, () -> service.suspend(guest.userId(), agentId, null));
            assertThrows(IllegalArgumentException.class,
                    () -> service.suspend(guest.userId(), host.userId(), "not an agent"));
            assertThrows(IllegalArgumentException.class,
                    () -> service.suspend(agentId, agentId, "self"), "agents cannot be suspended, even by themselves");
            assertThrows(IllegalArgumentException.class,
                    () -> service.suspend(java.util.UUID.randomUUID(), agentId, "unknown user"));
            assertThrows(IllegalArgumentException.class,
                    () -> service.suspend(com.snoozeshare.service.AuditService.SYSTEM_ACTOR_ID, agentId, "system"));
            assertThrows(IllegalStateException.class, () -> service.reactivate(guest.userId(), agentId, "not suspended"));
            service.suspend(guest.userId(), agentId, "reason");
            assertThrows(IllegalStateException.class, () -> service.suspend(guest.userId(), agentId, "again"));

            assertEquals(1, auditFor(fixture, AuditAction.ACCOUNT_SUSPENDED).size(), "only the one valid suspension");
        }
    }

    @Test
    void aSuspendedAgentCannotGovern() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = guest(fixture);
            fixture.users.save(new User(fixture.agent.userId(), Role.AGENT, "Amy Tanaka", "amy@test.com",
                    AccountStatus.SUSPENDED, "CODE", fixture.agent.createdAt()));

            assertThrows(IllegalStateException.class,
                    () -> fixture.service().suspend(guest.userId(), fixture.agent.userId(), "reason"));
        }
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "*AccountGovernanceServiceTest" -q`
Expected: FAIL (`UnsupportedOperationException: Task 5`).

- [ ] **Step 3: Implement**

Replace the two scaffold methods of `AccountGovernanceServiceImpl` with the code below and add the imports `java.sql.SQLException`, `java.time.Instant`, `java.util.ArrayList`, `com.snoozeshare.domain.enums.AccountStatus`, `com.snoozeshare.domain.enums.AuditAction`, `com.snoozeshare.domain.enums.Role`, `com.snoozeshare.infra.db.TransactionManager`, `com.snoozeshare.infra.events.DomainEvent`, `com.snoozeshare.infra.events.events.AccountStatusChangedEvent`, `com.snoozeshare.service.AuditRecord`:

```java
    @Override
    public User suspend(UUID userId, UUID agentId, String reason) {
        String text = requireReason(reason);
        requireActiveAgent(agentId);
        User target = requireGovernable(userId);
        if (target.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is already suspended");
        }
        List<DomainEvent> events = new ArrayList<>();
        User suspended = inTransaction("suspend account", () -> {
            Instant now = clock.instant();
            User saved = users.save(withStatus(target, AccountStatus.SUSPENDED, text));
            audit.record(AuditRecord.builder(agentId, AuditAction.ACCOUNT_SUSPENDED, "User", userId)
                    .status(AccountStatus.ACTIVE, AccountStatus.SUSPENDED).reason(text).subject(userId)
                    .at(now).build());
            cascade(target, agentId, now, events);
            return saved;
        });
        events.add(new AccountStatusChangedEvent(userId, AccountStatus.SUSPENDED, agentId, clock.instant()));
        events.forEach(this::publish);
        return suspended;
    }

    @Override
    public User reactivate(UUID userId, UUID agentId, String reason) {
        String text = requireReason(reason);
        requireActiveAgent(agentId);
        User target = requireGovernable(userId);
        if (target.accountStatus() != AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Account is not suspended");
        }
        User reactivated = inTransaction("reactivate account", () -> {
            Instant now = clock.instant();
            User saved = users.save(withStatus(target, AccountStatus.ACTIVE, null));
            audit.record(AuditRecord.builder(agentId, AuditAction.ACCOUNT_REACTIVATED, "User", userId)
                    .status(AccountStatus.SUSPENDED, AccountStatus.ACTIVE).reason(text).subject(userId)
                    .at(now).build());
            return saved;
        });
        publish(new AccountStatusChangedEvent(userId, AccountStatus.ACTIVE, agentId, clock.instant()));
        return reactivated;
    }

    /** Task 6 and Task 7 fill this in; suspension without a cascade is complete for a user with no bookings. */
    private void cascade(User target, UUID agentId, Instant now, List<DomainEvent> events) {
    }

    private <T> T inTransaction(String what, java.util.concurrent.Callable<T> work) {
        try {
            return new TransactionManager(connection).inTransaction(conn -> {
                try {
                    return work.call();
                } catch (SQLException | RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to " + what, exception);
        }
    }

    private void publish(DomainEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required");
        }
        return reason.trim();
    }

    private void requireActiveAgent(UUID agentId) {
        User agent = users.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent does not exist"));
        if (agent.role() != Role.AGENT) {
            throw new IllegalArgumentException("Only a support agent can manage accounts");
        }
        if (agent.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }

    private User requireGovernable(UUID userId) {
        User target = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        if (target.role() == Role.AGENT || target.userId().equals(AuditService.SYSTEM_ACTOR_ID)) {
            throw new IllegalArgumentException("Support agent accounts cannot be suspended or reactivated");
        }
        return target;
    }

    private static User withStatus(User user, AccountStatus status, String reason) {
        return new User(user.userId(), user.role(), user.displayName(), user.email(), status,
                user.registrationCode(), user.createdAt(), reason);
    }
```

`Callable`'s `call()` throws `Exception`, so the wrapper rethrows `SQLException | RuntimeException` and wraps anything else; `SqlWork.apply` throws `SQLException`, which `TransactionManager` propagates after rollback.

- [ ] **Step 4: Run and commit**

Run: `./gradlew test --tests "*AccountGovernanceServiceTest" checkstyleMain checkstyleTest -q` → PASS, no violations.

```bash
git add -A
git commit -m "feat(w11): suspend and reactivate core with validation, audit and event

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Cascade — bookings (guest and host, PENDING and not-yet-started CONFIRMED)

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/impl/AccountGovernanceServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/AccountGovernanceCascadeTest.java` (create)

- [ ] **Step 1: Write the failing tests**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.events.BookingCancelledEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;

class AccountGovernanceCascadeTest {

    private static final LocalDate UPCOMING_START = AccountFixture.TODAY.plusDays(9);
    private static final LocalDate UPCOMING_END = AccountFixture.TODAY.plusDays(11);

    private static BookingStatus statusOf(AccountFixture fixture, Booking booking) {
        return fixture.bookings.findById(booking.bookingId()).orElseThrow().status();
    }

    private static List<AuditLogEntry> audit(AccountFixture fixture, AuditAction action) {
        return fixture.audit.search(new AuditFilter(null, Set.of(action), null, null), 50, 0);
    }

    @Test
    void suspendingAGuestForceCancelsPendingAndUpcomingConfirmedWithFullRefundAndSparesTheRest() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);
            Booking pending = fixture.booking(guest, loft, BookingStatus.PENDING, UPCOMING_START, UPCOMING_END);
            Booking upcoming = fixture.booking(guest, loft, BookingStatus.CONFIRMED,
                    UPCOMING_END.plusDays(1), UPCOMING_END.plusDays(4));
            Booking inProgress = fixture.booking(guest, loft, BookingStatus.CONFIRMED,
                    AccountFixture.TODAY.minusDays(1), AccountFixture.TODAY.plusDays(2));
            Booking ended = fixture.booking(guest, loft, BookingStatus.CONFIRMED,
                    AccountFixture.TODAY.minusDays(6), AccountFixture.TODAY.minusDays(3));
            Booking completed = fixture.booking(guest, loft, BookingStatus.COMPLETED,
                    AccountFixture.TODAY.minusDays(30), AccountFixture.TODAY.minusDays(27));
            BigDecimal balanceBefore = fixture.balance(guest);
            List<Object> events = new ArrayList<>();
            fixture.bus.subscribe(BookingCancelledEvent.class, events::add);
            fixture.bus.subscribe(WalletTransactionRecordedEvent.class, events::add);

            fixture.service().suspend(guest.userId(), fixture.agent.userId(), "policy breach");

            assertEquals(BookingStatus.FORCE_CANCELLED, statusOf(fixture, pending));
            assertEquals(BookingStatus.FORCE_CANCELLED, statusOf(fixture, upcoming));
            assertEquals(BookingStatus.CONFIRMED, statusOf(fixture, inProgress));
            assertEquals(BookingStatus.CONFIRMED, statusOf(fixture, ended));
            assertEquals(BookingStatus.COMPLETED, statusOf(fixture, completed));
            BigDecimal refunded = pending.totalAmount().add(upcoming.totalAmount());
            assertEquals(0, balanceBefore.add(refunded).compareTo(fixture.balance(guest)));
            assertTrue(fixture.blocks.findByPropertyId(loft.propertyId()).stream()
                    .noneMatch(b -> b.bookingId() != null && (b.bookingId().equals(pending.bookingId())
                            || b.bookingId().equals(upcoming.bookingId()))), "blocks of cancelled bookings freed");
            assertEquals(2, audit(fixture, AuditAction.BOOKING_FORCE_CANCELLED).size());
            AuditLogEntry cancelled = audit(fixture, AuditAction.BOOKING_FORCE_CANCELLED).stream()
                    .filter(row -> row.entityId().equals(pending.bookingId())).findFirst().orElseThrow();
            assertEquals("PENDING", cancelled.beforeState());
            assertEquals("FORCE_CANCELLED", cancelled.afterState());
            assertEquals("Account suspended \u2014 cascading cancellation", cancelled.reason());
            assertEquals(guest.userId(), cancelled.subjectUserId());
            assertEquals(pending.bookingId(), cancelled.bookingId());
            assertEquals("CONFIRMED", audit(fixture, AuditAction.BOOKING_FORCE_CANCELLED).stream()
                    .filter(row -> row.entityId().equals(upcoming.bookingId())).findFirst().orElseThrow()
                    .beforeState());
            List<AuditLogEntry> money = audit(fixture, AuditAction.ESCROW_REFUND);
            assertEquals(2, money.size());
            assertTrue(money.stream().allMatch(row -> row.subjectUserId().equals(guest.userId())
                    && row.walletAdjustment().signum() > 0));
            assertEquals(4, events.size(), "a cancellation and a wallet event per booking");
        }
    }

    @Test
    void suspendingAHostForceCancelsTheirPropertiesBookingsAndRefundsEachGuest() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            User guestOne = fixture.user(Role.GUEST, "One", "one@test.com", "2026-03-01T08:00:00Z");
            User guestTwo = fixture.user(Role.GUEST, "Two", "two@test.com", "2026-03-02T08:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);
            Property villa = fixture.property(host, ListingStatus.INACTIVE);
            Booking pending = fixture.booking(guestOne, loft, BookingStatus.PENDING, UPCOMING_START, UPCOMING_END);
            Booking upcoming = fixture.booking(guestTwo, villa, BookingStatus.CONFIRMED,
                    UPCOMING_START, UPCOMING_END);
            Booking started = fixture.booking(guestTwo, loft, BookingStatus.CONFIRMED,
                    AccountFixture.TODAY.minusDays(1), AccountFixture.TODAY.plusDays(1));
            BigDecimal one = fixture.balance(guestOne);
            BigDecimal two = fixture.balance(guestTwo);

            fixture.service().suspend(host.userId(), fixture.agent.userId(), "fraud");

            assertEquals(BookingStatus.FORCE_CANCELLED, statusOf(fixture, pending));
            assertEquals(BookingStatus.FORCE_CANCELLED, statusOf(fixture, upcoming));
            assertEquals(BookingStatus.CONFIRMED, statusOf(fixture, started));
            assertEquals(0, one.add(pending.totalAmount()).compareTo(fixture.balance(guestOne)));
            assertEquals(0, two.add(upcoming.totalAmount()).compareTo(fixture.balance(guestTwo)));
            for (AuditLogEntry row : audit(fixture, AuditAction.BOOKING_FORCE_CANCELLED)) {
                assertEquals(host.userId(), row.subjectUserId(), "the suspended host is the subject");
            }
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AccountGovernanceCascadeTest" -q`
Expected: FAIL (statuses unchanged: the cascade is still empty).

- [ ] **Step 3: Implement the booking cascade**

Replace the empty `cascade` method of `AccountGovernanceServiceImpl` with the code below (Task 7 adds the listing part) and add imports `java.math.BigDecimal`, `java.time.LocalDate`, `com.snoozeshare.domain.enums.BookingStatus`, `com.snoozeshare.domain.enums.WalletTransactionType`, `com.snoozeshare.domain.model.Booking`, `com.snoozeshare.domain.model.Property`, `com.snoozeshare.domain.model.Wallet`, `com.snoozeshare.domain.model.WalletTransaction`, `com.snoozeshare.domain.statemachine.BookingStateMachine`, `com.snoozeshare.infra.events.events.BookingCancelledEvent`, `com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent`:

```java
    /** The suspended user's PENDING and not-yet-started CONFIRMED bookings are force-cancelled with a refund. */
    private void cascade(User target, UUID agentId, Instant now, List<DomainEvent> events) {
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        List<Booking> affected = new ArrayList<>();
        if (target.role() == Role.GUEST) {
            affected.addAll(bookings.findByGuest(target.userId()));
        } else {
            for (Property property : properties.findByHostId(target.userId())) {
                affected.addAll(bookings.findByListing(property.propertyId()));
            }
        }
        for (Booking booking : affected) {
            if (isCancellable(booking, today)) {
                forceCancel(booking, target, agentId, now, events);
            }
        }
    }

    /** PENDING, or CONFIRMED with a check-in date after today. Started and ended stays belong to W10 (C36). */
    static boolean isCancellable(Booking booking, LocalDate today) {
        return booking.status() == BookingStatus.PENDING
                || (booking.status() == BookingStatus.CONFIRMED && booking.startDate().isAfter(today));
    }

    private void forceCancel(Booking booking, User suspended, UUID agentId, Instant now, List<DomainEvent> events) {
        if (!BookingStateMachine.canTransition(booking.status(), BookingStatus.FORCE_CANCELLED, Role.AGENT)) {
            throw new IllegalStateException("Cannot force-cancel a booking in status " + booking.status());
        }
        bookings.save(new Booking(booking.bookingId(), booking.listingId(), booking.guestId(),
                booking.startDate(), booking.endDate(), BookingStatus.FORCE_CANCELLED,
                booking.nightlyRateSnapshot(), booking.totalAmount(), booking.createdAt(), now, null));
        blocks.deleteByBookingId(booking.bookingId());
        audit.record(AuditRecord.builder(agentId, AuditAction.BOOKING_FORCE_CANCELLED, "Booking",
                        booking.bookingId())
                .status(booking.status(), BookingStatus.FORCE_CANCELLED).reason(CASCADE_BOOKING_REASON)
                .subject(suspended.userId()).booking(booking.bookingId()).at(now).build());
        // Inline wallet write: WalletLedgerWriter opens its own transaction, which would commit early here.
        Wallet wallet = wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
        BigDecimal balanceAfter = wallet.balance().add(booking.totalAmount());
        wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter, wallet.currency(), now));
        WalletTransaction refund = transactions.save(new WalletTransaction(UUID.randomUUID(), wallet.walletId(),
                WalletTransactionType.ESCROW_REFUND, booking.totalAmount(), BigDecimal.ZERO, balanceAfter,
                booking.bookingId(), null, agentId, now));
        audit.recordWalletTransaction(agentId, booking.guestId(), refund, refund.amount(), null);
        events.add(new WalletTransactionRecordedEvent(refund.transactionId(), refund.walletId(), now));
        events.add(new BookingCancelledEvent(booking.bookingId(), agentId, now));
    }
```

- [ ] **Step 4: Run and commit**

Run: `./gradlew test --tests "*AccountGovernance*" checkstyleMain checkstyleTest -q` → PASS.

```bash
git add -A
git commit -m "feat(w11): suspension cascades force-cancel pending and upcoming bookings with refund (C36)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Cascade — deactivate a host's ACTIVE listings; reactivation leaves them alone

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/impl/AccountGovernanceServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/AccountGovernanceCascadeTest.java`

- [ ] **Step 1: Add failing tests** (append inside `AccountGovernanceCascadeTest`)

```java
    @Test
    void suspendingAHostDeactivatesActiveListingsAndReactivationLeavesThemInactive() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            Property active = fixture.property(host, ListingStatus.ACTIVE);
            Property alreadyInactive = fixture.property(host, ListingStatus.INACTIVE);

            fixture.service().suspend(host.userId(), fixture.agent.userId(), "fraud");

            assertEquals(ListingStatus.INACTIVE, fixture.properties.findById(active.propertyId()).orElseThrow()
                    .status());
            List<AuditLogEntry> rows = audit(fixture, AuditAction.LISTING_STATUS_CASCADE);
            assertEquals(1, rows.size(), "only the ACTIVE listing changes, so only it is audited");
            assertEquals(active.propertyId(), rows.get(0).entityId());
            assertEquals("Property", rows.get(0).entityType());
            assertEquals("ACTIVE", rows.get(0).beforeState());
            assertEquals("INACTIVE", rows.get(0).afterState());
            assertEquals("Host suspended", rows.get(0).reason());
            assertEquals(host.userId(), rows.get(0).subjectUserId());

            fixture.service().reactivate(host.userId(), fixture.agent.userId(), "cleared");

            assertEquals(ListingStatus.INACTIVE, fixture.properties.findById(active.propertyId()).orElseThrow()
                    .status(), "the host re-lists manually");
            assertEquals(ListingStatus.INACTIVE, fixture.properties.findById(alreadyInactive.propertyId())
                    .orElseThrow().status());
        }
    }

    @Test
    void suspendingAGuestLeavesListingsAndOtherAccountsAlone() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);

            fixture.service().suspend(guest.userId(), fixture.agent.userId(), "policy breach");

            assertEquals(ListingStatus.ACTIVE, fixture.properties.findById(loft.propertyId()).orElseThrow()
                    .status());
            assertEquals(0, audit(fixture, AuditAction.LISTING_STATUS_CASCADE).size());
        }
    }
```

- [ ] **Step 2: Run to verify the first fails**

Run: `./gradlew test --tests "*AccountGovernanceCascadeTest" -q`
Expected: FAIL on the listing status assertion.

- [ ] **Step 3: Implement**

In `cascade`, replace the host branch

```java
            for (Property property : properties.findByHostId(target.userId())) {
                affected.addAll(bookings.findByListing(property.propertyId()));
            }
```

with

```java
            for (Property property : properties.findByHostId(target.userId())) {
                affected.addAll(bookings.findByListing(property.propertyId()));
                if (property.status() == ListingStatus.ACTIVE) {
                    deactivate(property, target, agentId, now);
                }
            }
```

and add (imports `com.snoozeshare.domain.enums.ListingStatus`):

```java
    private void deactivate(Property property, User host, UUID agentId, Instant now) {
        properties.save(new Property(property.propertyId(), property.hostId(), ListingStatus.INACTIVE,
                property.title(), property.description(), property.propertyType(), property.streetAddress(),
                property.city(), property.region(), property.postalCode(), property.maxGuests(),
                property.bedrooms(), property.bathrooms(), property.baseNightlyRate(), property.checkInTime(),
                property.checkOutTime(), property.amenities(), property.createdAt()));
        audit.record(AuditRecord.builder(agentId, AuditAction.LISTING_STATUS_CASCADE, "Property",
                        property.propertyId())
                .status(ListingStatus.ACTIVE, ListingStatus.INACTIVE).reason(CASCADE_LISTING_REASON)
                .subject(host.userId()).at(now).build());
    }
```

- [ ] **Step 4: Run and commit**

Run: `./gradlew test --tests "*AccountGovernance*" checkstyleMain checkstyleTest -q` → PASS.

```bash
git add -A
git commit -m "feat(w11): suspending a host deactivates their active listings

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: Atomicity — a failure anywhere rolls the whole suspension back

**Files:**
- Test: `src/test/java/com/snoozeshare/service/AccountGovernanceAtomicityTest.java` (create)

The transaction is already in place from Tasks 5–7; this task proves it with the existing `FailingAuditService` (throws on the Nth `record` call).

- [ ] **Step 1: Write the test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.events.AccountStatusChangedEvent;
import com.snoozeshare.testsupport.FailingAuditService;

class AccountGovernanceAtomicityTest {

    @Test
    void anAuditFailureDuringTheCascadeRollsBackStatusBookingsWalletsAndListings() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);
            Booking pending = fixture.booking(guest, loft, BookingStatus.PENDING,
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7));
            BigDecimal balance = fixture.balance(guest);
            List<Object> events = new ArrayList<>();
            fixture.bus.subscribe(AccountStatusChangedEvent.class, events::add);
            // Calls: 1 ACCOUNT_SUSPENDED, 2 LISTING_STATUS_CASCADE, 3 BOOKING_FORCE_CANCELLED (fails).
            var service = fixture.service(new FailingAuditService(fixture.audit, 3));

            assertThrows(RuntimeException.class, () -> service.suspend(host.userId(), fixture.agent.userId(), "fraud"));

            User stored = fixture.users.findById(host.userId()).orElseThrow();
            assertEquals(AccountStatus.ACTIVE, stored.accountStatus());
            assertNull(stored.suspensionReason());
            assertEquals(ListingStatus.ACTIVE, fixture.properties.findById(loft.propertyId()).orElseThrow().status());
            assertEquals(BookingStatus.PENDING, fixture.bookings.findById(pending.bookingId()).orElseThrow()
                    .status());
            assertEquals(0, balance.compareTo(fixture.balance(guest)), "no refund was kept");
            assertEquals(0, fixture.audit.search(AuditFilter.none(), 50, 0).size(), "no audit row was kept");
            assertEquals(0, events.size(), "nothing is published for a rolled-back change");
        }
    }

    @Test
    void aMissingGuestWalletRollsBackTheSuspension() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);
            fixture.booking(guest, loft, BookingStatus.PENDING, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7));
            fixture.connection.createStatement().executeUpdate("PRAGMA foreign_keys = OFF");
            fixture.connection.createStatement().executeUpdate("DELETE FROM wallets WHERE userId = '"
                    + guest.userId() + "'");

            assertThrows(IllegalArgumentException.class,
                    () -> fixture.service().suspend(host.userId(), fixture.agent.userId(), "fraud"));

            assertEquals(AccountStatus.ACTIVE, fixture.users.findById(host.userId()).orElseThrow().accountStatus());
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew test --tests "*AccountGovernanceAtomicityTest" -q`
Expected: PASS. If `audit.search(AuditFilter.none(), ...)` returns the seeded rows of a fresh migrated DB, the count is 0 because an in-memory migrated DB has no audit rows; if the assertion fails with a non-zero count, compare with the count taken before the call instead: capture `int before = fixture.audit.search(AuditFilter.none(), 50, 0).size();` and assert `assertEquals(before, ...)`.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(w11): suspension is atomic across status, bookings, wallets, listings and audit

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: Enforcement guard, remove the old stub, wire `AppContext`

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/impl/BookingServiceImpl.java`
- Modify: `src/main/java/com/snoozeshare/service/UserService.java`, `src/main/java/com/snoozeshare/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`
- Test: `src/test/java/com/snoozeshare/service/BookingServiceTest.java`

- [ ] **Step 1: Write the failing test**

In `BookingServiceTest.java` add (imports already present: `assertThrows`, `AccountStatus`, `User`; the test uses `seedContext`, `createService`, `migratedConnection`):

```java
    @Test
    void aSuspendedGuestCannotSubmitABookingRequest() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection, new BigDecimal("500.00"));
            var users = new JdbcUserRepository(connection);
            User guest = users.findById(ctx.guestId).orElseThrow();
            users.save(new User(guest.userId(), guest.role(), guest.displayName(), guest.email(),
                    AccountStatus.SUSPENDED, null, guest.createdAt(), "policy breach"));
            BookingService service = createService(connection);

            var thrown = assertThrows(IllegalStateException.class, () -> service.submitRequest(ctx.guestId,
                    ctx.propertyId, LocalDate.now().plusDays(10), LocalDate.now().plusDays(12)));

            assertEquals("Account is not active", thrown.getMessage());
            assertEquals(0, count(connection, "bookings"));
        }
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*BookingServiceTest.aSuspendedGuestCannotSubmitABookingRequest" -q`
Expected: FAIL (a booking is created, or a compile error until Step 3 adds nothing; the test compiles today).

- [ ] **Step 3: Add the guard**

In `BookingServiceImpl` add a `UserRepository users` field and constructor parameter (place it after `bookings`): 

```java
    private final UserRepository users;
```

Constructor becomes
`public BookingServiceImpl(Connection connection, BookingRepository bookings, UserRepository users, PropertyRepository properties, AvailabilityBlockRepository blocks, WalletRepository wallets, WalletTransactionRepository transactions, EventBus eventBus, AuditService audit)` with `this.users = users;`. Add imports `com.snoozeshare.domain.enums.AccountStatus`, `com.snoozeshare.domain.model.User`, `com.snoozeshare.repository.UserRepository`.

In `submitRequest`, directly after `DomainValidation.requireDateRange(start, end);` add:

```java
        User guest = users.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("Guest does not exist"));
        if (guest.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
```

Update the two constructor call sites:
- `BookingServiceTest.createService(Connection, InProcessEventBus, AuditService)`: insert `new JdbcUserRepository(connection),` after `new JdbcBookingRepository(connection),`.
- `AppContext.java` (`this.bookingService = new BookingServiceImpl(connection, bookingRepo, propertyRepo,` ...): insert `users,` after `bookingRepo,`.

If existing booking tests submit for a guest id that has no user row, they will now fail with "Guest does not exist"; every test in `BookingServiceTest` uses `seedContext`, which saves the guest, so none should. Run the whole class to confirm.

- [ ] **Step 4: Remove `UserService.suspend`**

Delete `User suspend(UUID userId, UUID agentId);` from `UserService.java` and the whole `suspend` method from `UserServiceImpl.java` (D19). Then run `grep -rn "\.suspend(" src` — the only callers must be `AccountGovernanceService` ones.

- [ ] **Step 5: Wire `AppContext`**

Add field `private final AccountGovernanceService accountGovernanceService;`, imports `com.snoozeshare.service.AccountGovernanceService` and `com.snoozeshare.service.impl.AccountGovernanceServiceImpl`, and after the `bookingService` construction:

```java
        this.accountGovernanceService = new AccountGovernanceServiceImpl(connection, users, bookingRepo,
                propertyRepo, blockRepo, wallets, txnRepo, eventBus, auditService, Clock.systemDefaultZone());
```

and the accessor (next to `bookingService()`):

```java
    public AccountGovernanceService accountGovernanceService() {
        return accountGovernanceService;
    }
```

(`Clock.systemDefaultZone()` because the audit service and the UI use the local zone; `LocalDate.ofInstant(now, clock.getZone())` then matches the user's "today".)

- [ ] **Step 6: Run everything and commit**

Run: `./gradlew build -q`
Expected: PASS (checkstyle + all tests).

```bash
git add -A
git commit -m "feat(w11): refuse bookings from suspended guests, wire the governance service, drop UserService.suspend

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 10: UI text helpers — `AccountText` and `AccountSearch`

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/admin/accounts/AccountText.java`
- Create: `src/main/java/com/snoozeshare/ui/admin/accounts/AccountSearch.java`
- Test: `src/test/java/com/snoozeshare/ui/admin/accounts/AccountTextTest.java`, `AccountSearchTest.java` (create; add the package directory)

- [ ] **Step 1: Write the failing tests**

`AccountTextTest.java`:

```java
package com.snoozeshare.ui.admin.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;

class AccountTextTest {

    @Test
    void joinedUsesDdMmmYyyyInTheGivenZone() {
        Instant instant = Instant.parse("2026-03-05T20:00:00Z");
        assertEquals("05 Mar 2026", AccountText.joined(instant, ZoneId.of("UTC")));
        assertEquals("06 Mar 2026", AccountText.joined(instant, ZoneId.of("Asia/Singapore")));
    }

    @Test
    void rolesAndStatusesReadLikeTheCanvas() {
        assertEquals("Guest", AccountText.role(Role.GUEST));
        assertEquals("Host", AccountText.role(Role.HOST));
        assertEquals("Support Agent", AccountText.role(Role.AGENT));
        assertEquals("Active", AccountText.status(AccountStatus.ACTIVE));
        assertEquals("Suspended", AccountText.status(AccountStatus.SUSPENDED));
    }
}
```

`AccountSearchTest.java`:

```java
package com.snoozeshare.ui.admin.accounts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.service.AccountSummary;

class AccountSearchTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final AccountSummary PRIYA = new AccountSummary(UUID.randomUUID(), "Priya Nair",
            "priya.nair@snoozeshare.test", Role.HOST, Instant.parse("2026-03-05T08:00:00Z"),
            AccountStatus.SUSPENDED, "repeated late cancellations");

    private static boolean matches(String query) {
        return AccountSearch.matches(PRIYA, query, UTC);
    }

    @Test
    void blankShowsEverything() {
        assertTrue(matches(null));
        assertTrue(matches(""));
        assertTrue(matches("   "));
    }

    @Test
    void matchesEachDisplayedFieldCaseInsensitively() {
        assertTrue(matches("PRIYA"), "display name");
        assertTrue(matches("nair"), "display name, later word");
        assertTrue(matches("snoozeshare.test"), "email");
        assertTrue(matches("host"), "role");
        assertTrue(matches("mar"), "joined month name");
        assertTrue(matches("2026"), "joined year");
        assertTrue(matches("05 mar"), "joined as displayed");
        assertTrue(matches("suspend"), "status");
    }

    @Test
    void doesNotSearchTheReasonOrUnrelatedText() {
        assertFalse(matches("cancellations"), "the reason is not searched");
        assertFalse(matches("guest"), "the role is Host");
        assertFalse(matches("active"), "the status is Suspended");
        assertFalse(matches("zzz"));
    }

    @Test
    void agentsAreFoundBySupportAgent() {
        AccountSummary agent = new AccountSummary(UUID.randomUUID(), "Amy Tanaka", "amy@test.com", Role.AGENT,
                Instant.parse("2026-01-10T09:00:00Z"), AccountStatus.ACTIVE, null);
        assertTrue(AccountSearch.matches(agent, "support agent", UTC));
    }
}
```

Note `"active"` does not match "Suspended" because the status text is `Suspended`; and `"guest"` must not match the email/name of the fixture (they contain neither).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "com.snoozeshare.ui.admin.accounts.*" -q`
Expected: FAIL to compile.

- [ ] **Step 3: Implement `AccountText`**

```java
package com.snoozeshare.ui.admin.accounts;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;

/** The texts the Accounts screen and its dialog display, kept in one place so search matches what is shown. */
public final class AccountText {

    private static final DateTimeFormatter JOINED = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private AccountText() {
    }

    /** {@code DD MMM YYYY}, e.g. {@code 05 Mar 2026} (C35). */
    public static String joined(Instant createdAt, ZoneId zone) {
        return JOINED.format(createdAt.atZone(zone));
    }

    public static String joined(Instant createdAt) {
        return joined(createdAt, ZoneId.systemDefault());
    }

    public static String role(Role role) {
        return switch (role) {
            case GUEST -> "Guest";
            case HOST -> "Host";
            case AGENT -> "Support Agent";
        };
    }

    public static String status(AccountStatus status) {
        return status == AccountStatus.SUSPENDED ? "Suspended" : "Active";
    }
}
```

- [ ] **Step 4: Implement `AccountSearch`**

```java
package com.snoozeshare.ui.admin.accounts;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import com.snoozeshare.service.AccountSummary;

/** Live search over the displayed cell texts of an account row (spec § 3.1.1). */
public final class AccountSearch {

    private AccountSearch() {
    }

    /** True when {@code query} is blank or is contained, ignoring case, in any displayed cell text. */
    public static boolean matches(AccountSummary account, String query, ZoneId zone) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<String> cells = List.of(account.displayName(), account.email(), AccountText.role(account.role()),
                AccountText.joined(account.createdAt(), zone), AccountText.status(account.status()));
        return cells.stream().anyMatch(cell -> cell.toLowerCase(Locale.ROOT).contains(needle));
    }
}
```

- [ ] **Step 5: Run and commit**

Run: `./gradlew test --tests "com.snoozeshare.ui.admin.accounts.*" checkstyleMain checkstyleTest -q` → PASS.

```bash
git add -A
git commit -m "feat(w11): account display texts (DD MMM YYYY) and live search matching

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: The Suspend / Reactivate modal card

**Files:**
- Create: `src/main/resources/com/snoozeshare/ui/admin/accounts/suspension-dialog.fxml`
- Create: `src/main/java/com/snoozeshare/ui/admin/accounts/SuspensionDialogController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/admin/agent-theme.css`
- Test: `src/test/java/com/snoozeshare/ui/admin/SuspensionDialogFlowTest.java` (create)

- [ ] **Step 1: Write the failing test** (mirrors `CategoryDialogFlowTest`)

```java
package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.service.AccountSummary;
import com.snoozeshare.ui.admin.accounts.SuspensionDialogController;
import com.snoozeshare.ui.admin.accounts.SuspensionDialogController.Mode;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Drives the real Suspend / Reactivate modal through its own controls. */
class SuspensionDialogFlowTest {

    private static final AccountSummary PRIYA = new AccountSummary(UUID.randomUUID(), "Priya Nair",
            "priya.nair@snoozeshare.test", Role.GUEST, Instant.parse("2026-03-05T08:00:00Z"),
            AccountStatus.ACTIVE, null);

    private static boolean toolkitAvailable;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
            toolkitAvailable = true;
        } catch (IllegalStateException alreadyStarted) {
            toolkitAvailable = true;
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            toolkitAvailable = false;
        }
        if (toolkitAvailable) {
            Platform.setImplicitExit(false);
        }
    }

    private static Button button(Stage stage, String id) {
        return (Button) stage.getScene().getRoot().lookup("#" + id);
    }

    private static String text(Stage stage, String id) {
        return ((Label) stage.getScene().getRoot().lookup("#" + id)).getText();
    }

    private static void reason(Stage stage, String value) {
        ((TextArea) stage.getScene().getRoot().lookup("#reasonArea")).setText(value);
    }

    @Test
    void suspendCardShowsNameLargeEmailBelowAndTheJoinedDateAsDdMmmYyyy() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.SUSPEND, PRIYA, reason -> { });
            stage.show();
            Region banner = (Region) stage.getScene().getRoot().lookup("#bannerBox");
            Object[] values = {text(stage, "titleLabel"), text(stage, "nameLabel"), text(stage, "emailLabel"),
                text(stage, "metaLabel").startsWith("Guest \u00b7 joined "),
                text(stage, "metaLabel").endsWith(" Mar 2026") || text(stage, "metaLabel").endsWith(" 2026"),
                banner.getStyleClass().contains("agent-banner-danger"), button(stage, "confirmButton").getText(),
                button(stage, "confirmButton").isDisabled()};
            stage.close();
            return values;
        });

        assertEquals("Suspend account", state[0]);
        assertEquals("Priya Nair", state[1]);
        assertEquals("priya.nair@snoozeshare.test", state[2]);
        assertEquals(true, state[3]);
        assertEquals(true, state[4]);
        assertEquals(true, state[5]);
        assertEquals("Confirm suspend", state[6]);
        assertEquals(true, state[7], "confirm is disabled until a reason is typed");
    }

    @Test
    void reactivateCardUsesTheSuccessGreenAndItsOwnButtonText() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.REACTIVATE, PRIYA, reason -> { });
            stage.show();
            Region banner = (Region) stage.getScene().getRoot().lookup("#bannerBox");
            Object[] values = {text(stage, "titleLabel"), banner.getStyleClass().contains("agent-banner-success"),
                banner.getBackground().getFills().get(0).getFill().toString(),
                button(stage, "confirmButton").getText()};
            stage.close();
            return values;
        });

        assertEquals("Reactivate account", state[0]);
        assertEquals(true, state[1]);
        assertEquals("0x40680cff", state[2], "#40680C from the Force Complete mock-up");
        assertEquals("Confirm reactivate", state[3]);
    }

    @Test
    void confirmPassesTheTrimmedReasonAndClosesTheCard() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        List<String> reasons = new ArrayList<>();
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.SUSPEND, PRIYA, reasons::add);
            stage.show();
            reason(stage, "   ");
            boolean blankDisabled = button(stage, "confirmButton").isDisabled();
            reason(stage, "  repeated late cancellations  ");
            boolean typedEnabled = !button(stage, "confirmButton").isDisabled();
            button(stage, "confirmButton").fire();
            return new Object[] {blankDisabled, typedEnabled, stage.isShowing()};
        });

        assertEquals(true, state[0]);
        assertEquals(true, state[1]);
        assertEquals(false, state[2]);
        assertEquals(List.of("repeated late cancellations"), reasons);
    }

    @Test
    void aServiceFailureIsShownInsideTheCardWhichStaysOpen() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        Object[] state = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.SUSPEND, PRIYA, reason -> {
                throw new IllegalStateException("Account is already suspended");
            });
            stage.show();
            reason(stage, "policy breach");
            button(stage, "confirmButton").fire();
            Object[] values = {text(stage, "errorLabel"), stage.isShowing()};
            stage.close();
            return values;
        });

        assertEquals("Account is already suspended", state[0]);
        assertTrue((Boolean) state[1]);
    }

    @Test
    void cancelClosesWithoutCallingBack() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        List<String> reasons = new ArrayList<>();
        boolean showing = AdminUiSnapshotTest.onFx(() -> {
            Stage stage = SuspensionDialogController.createDialog(Mode.SUSPEND, PRIYA, reasons::add);
            stage.show();
            button(stage, "cancelButton").fire();
            return stage.isShowing();
        });

        assertFalse(showing);
        assertTrue(reasons.isEmpty());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*SuspensionDialogFlowTest" -q`
Expected: FAIL to compile (`SuspensionDialogController` missing).

- [ ] **Step 3: Add CSS** (append near the other dialog rules, after `.agent-root .agent-banner-remedy` at `agent-theme.css:545`)

```css
.agent-root .agent-banner-name { -fx-font-size: 24px; -fx-font-weight: 800; -fx-text-fill: #fdf8f0; }
.agent-root .agent-banner-sub { -fx-font-size: 13px; -fx-text-fill: #fdf8f0; -fx-opacity: 0.9; }
.agent-root .agent-dialog-info {
    -fx-background-color: bg-inset;
    -fx-background-radius: 8px;
    -fx-padding: 10px 12px 10px 12px;
    -fx-font-size: 12px;
    -fx-text-fill: fg-muted;
}
```

(`agent-confirm-danger` and `agent-confirm-success` for the confirm buttons already exist at `agent-theme.css:572-574`. The modal rule `.agent-modal .agent-banner { -fx-effect: null; }` already removes the label shadows.)

- [ ] **Step 4: Create the FXML**

`suspension-dialog.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import java.net.URL?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TextArea?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" prefWidth="460" minWidth="460" maxWidth="460" maxHeight="-Infinity"
      styleClass="agent-root, agent-modal, agent-dialog-card"
      xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.admin.accounts.SuspensionDialogController">
    <stylesheets>
        <URL value="@../agent-theme.css"/>
    </stylesheets>

    <Label fx:id="titleLabel" styleClass="agent-dialog-title"/>

    <VBox fx:id="bannerBox" spacing="4" styleClass="agent-banner">
        <Label fx:id="nameLabel" wrapText="true" styleClass="agent-banner-name"/>
        <Label fx:id="emailLabel" wrapText="true" styleClass="agent-banner-sub"/>
        <Label fx:id="metaLabel" styleClass="agent-banner-sub"/>
    </VBox>

    <Label fx:id="noteLabel" wrapText="true" maxWidth="Infinity" styleClass="agent-dialog-info"/>

    <VBox spacing="4">
        <Label text="Reason (required, written to the audit log)" styleClass="agent-dialog-field-label"/>
        <TextArea fx:id="reasonArea" wrapText="true" styleClass="agent-input, agent-dialog-area"
                  minHeight="64" prefHeight="64" maxHeight="64"/>
    </VBox>

    <Label fx:id="errorLabel" wrapText="true" styleClass="error-message"/>

    <HBox spacing="8" alignment="CENTER_RIGHT">
        <Button fx:id="cancelButton" text="Cancel" onAction="#handleCancel"
                styleClass="outline-button, agent-dialog-button"/>
        <Button fx:id="confirmButton" mnemonicParsing="false" onAction="#handleConfirm"
                styleClass="button, agent-dialog-button"/>
    </HBox>
</VBox>
```

- [ ] **Step 5: Create the controller**

```java
package com.snoozeshare.ui.admin.accounts;

import java.io.IOException;
import java.util.function.Consumer;

import com.snoozeshare.service.AccountSummary;
import com.snoozeshare.ui.admin.AgentModal;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The Suspend / Reactivate modal card (C34, C35). The service call is passed in as a callback that may throw; its
 * message is shown inline in the card (which stays open) instead of closing it.
 */
public final class SuspensionDialogController {

    /** Which action the card confirms; it decides the title, banner colour, note and button text. */
    public enum Mode { SUSPEND, REACTIVATE }

    private static final String FXML = "/com/snoozeshare/ui/admin/accounts/suspension-dialog.fxml";

    @FXML private Label titleLabel;
    @FXML private VBox bannerBox;
    @FXML private Label nameLabel;
    @FXML private Label emailLabel;
    @FXML private Label metaLabel;
    @FXML private Label noteLabel;
    @FXML private TextArea reasonArea;
    @FXML private Label errorLabel;
    @FXML private Button confirmButton;

    private Consumer<String> confirmAction = reason -> { };
    private Runnable closeAction = () -> { };
    private Runnable resizeAction = () -> { };

    /** Builds the modal (not yet shown); {@code onConfirm} receives the trimmed reason. */
    public static Stage createDialog(Mode mode, AccountSummary account, Consumer<String> onConfirm) {
        try {
            FXMLLoader loader = new FXMLLoader(SuspensionDialogController.class.getResource(FXML));
            Parent card = loader.load();
            SuspensionDialogController controller = loader.getController();
            controller.configure(mode, account, onConfirm);
            AgentModal modal = AgentModal.create(card, controller.titleLabel.getText());
            controller.closeAction = modal::close;
            controller.resizeAction = modal::refit;
            return modal.stage();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the account dialog", exception);
        }
    }

    public static void show(Mode mode, AccountSummary account, Consumer<String> onConfirm) {
        createDialog(mode, account, onConfirm).showAndWait();
    }

    private void configure(Mode mode, AccountSummary account, Consumer<String> onConfirm) {
        confirmAction = onConfirm;
        boolean suspend = mode == Mode.SUSPEND;
        errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.textProperty().addListener((observable, previous, text) -> resizeAction.run());
        titleLabel.setText(suspend ? "Suspend account" : "Reactivate account");
        bannerBox.getStyleClass().add(suspend ? "agent-banner-danger" : "agent-banner-success");
        nameLabel.setText(account.displayName());
        emailLabel.setText(account.email());
        metaLabel.setText(AccountText.role(account.role()) + " \u00b7 joined " + AccountText.joined(account.createdAt()));
        noteLabel.setText(suspend
                ? "The user cannot log in or book until reactivated. Their pending requests are cancelled with a "
                        + "full refund, upcoming confirmed stays are force-cancelled with a full refund, and a "
                        + "host's active listings are deactivated."
                : "The user can log in and use the app again. Cancelled bookings and deactivated listings are "
                        + "not restored; a host re-activates their listings.");
        reasonArea.setPromptText(suspend
                ? "e.g. repeated late cancellations flagged across 3 bookings"
                : "e.g. appeal accepted after review");
        confirmButton.setText(suspend ? "Confirm suspend" : "Confirm reactivate");
        confirmButton.getStyleClass().add(suspend ? "agent-confirm-danger" : "agent-confirm-success");
        confirmButton.disableProperty().bind(reasonArea.textProperty().map(text -> text == null || text.isBlank()));
    }

    @FXML
    private void handleCancel() {
        closeAction.run();
    }

    @FXML
    private void handleConfirm() {
        String reason = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
        if (reason.isEmpty()) {
            return;
        }
        try {
            confirmAction.accept(reason);
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
            return;
        }
        closeAction.run();
    }
}
```

`confirmButton.disableProperty().bind(...)` needs `reasonArea.textProperty()` non-null at bind time; a `TextArea` text is `""` initially, so the button starts disabled as the test expects.

- [ ] **Step 6: Run and commit**

Run: `./gradlew test --tests "*SuspensionDialogFlowTest" checkstyleMain checkstyleTest -q`
Expected: PASS. Fix line length (>120) in the `metaLabel.setText(...)` line by wrapping if checkstyle reports it.

```bash
git add -A
git commit -m "feat(w11): Suspend / Reactivate modal card

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 12: The Accounts screen and shell wiring

**Files:**
- Create: `src/main/resources/com/snoozeshare/ui/admin/accounts/account-governance.fxml`
- Create: `src/main/java/com/snoozeshare/ui/admin/accounts/AccountGovernanceController.java`
- Modify: `src/main/java/com/snoozeshare/ui/admin/AdminShellController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/admin/agent-theme.css`
- Test: `src/test/java/com/snoozeshare/ui/admin/AccountGovernanceUiTest.java` (create)

- [ ] **Step 1: Write the failing UI test**

```java
package com.snoozeshare.ui.admin;

import static com.snoozeshare.ui.admin.AdminUiSnapshotTest.loadShell;
import static com.snoozeshare.ui.admin.AdminUiSnapshotTest.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.testsupport.MockDbFixture;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** The Accounts tab on the FX toolkit against a copy of the mock DB (17 users, 16 listed). */
class AccountGovernanceUiTest {

    private static boolean toolkitAvailable;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
            toolkitAvailable = true;
        } catch (IllegalStateException alreadyStarted) {
            toolkitAvailable = true;
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            toolkitAvailable = false;
        }
        if (toolkitAvailable) {
            Platform.setImplicitExit(false);
        }
    }

    private static Parent accounts(AppContext context) throws Exception {
        AdminUiSnapshotTest.Shell shell = loadShell(context);
        shell.show("showAccounts");
        Parent root = shell.root();
        new Scene(root, 1280, 800);
        for (int pass = 0; pass < 2; pass++) {
            root.applyCss();
            root.layout();
        }
        return root;
    }

    private static List<GridPane> rows(Parent root) {
        VBox rows = (VBox) ((ScrollPane) root.lookup(".agent-rows-scroll")).getContent();
        return rows.getChildren().stream().filter(GridPane.class::isInstance).map(GridPane.class::cast).toList();
    }

    private static String cell(GridPane row, int column) {
        Node node = row.getChildren().stream()
                .filter(child -> GridPane.getColumnIndex(child) != null && GridPane.getColumnIndex(child) == column)
                .findFirst().orElseThrow();
        return node instanceof Label label ? label.getText() : node.toString();
    }

    private static AppContext agentContext(MockDbFixture db) throws Exception {
        AppContext context = AppContext.create(db.jdbcUrl());
        context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
        return context;
    }

    @Test
    void listsEveryAccountExceptTheSystemUserWithTheSpecifiedColumns(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            Object[] state = onFx(() -> {
                Parent root = accounts(context);
                GridPane header = (GridPane) root.lookup(".agent-grid-header");
                List<String> headers = header.getChildren().stream().map(child -> ((Label) child).getText()).toList();
                List<GridPane> rows = rows(root);
                return new Object[] {headers, rows.size(), cell(rows.get(0), 0), cell(rows.get(0), 1),
                    cell(rows.get(0), 3),
                    ((TextField) root.lookup("#searchField")).getPromptText(),
                    root.lookup("#searchField").getStyleClass().contains("agent-input")};
            });

            assertEquals(List.of("DISPLAY NAME", "EMAIL", "ROLE", "JOINED", "STATUS", "ACTION"), state[0]);
            assertEquals(16, state[1]);
            assertEquals("Amy Tanaka", state[2], "oldest account first");
            assertEquals("amy.tanaka@snoozeshare.test", state[3]);
            assertEquals("10 Jan 2026", state[4], "Joined as DD MMM YYYY");
            assertEquals("Search...", state[5]);
        }
    }

    @Test
    void searchFiltersLiveAcrossNameEmailRoleJoinedAndStatus(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            int[] counts = onFx(() -> {
                Parent root = accounts(context);
                TextField search = (TextField) root.lookup("#searchField");
                search.setText("suspended");
                int suspended = rows(root).size();
                search.setText("SAM O");
                int name = rows(root).size();
                search.setText("support agent");
                int agents = rows(root).size();
                search.setText("no such account");
                int none = rows(root).size();
                boolean emptyShown = ((Label) root.lookup("#emptyLabel")).isVisible();
                search.setText("");
                int all = rows(root).size();
                return new int[] {suspended, name, agents, none, emptyShown ? 1 : 0, all};
            });

            assertEquals(2, counts[0], "Kai and Sam are suspended (the System user is not listed)");
            assertEquals(1, counts[1]);
            assertEquals(3, counts[2]);
            assertEquals(0, counts[3]);
            assertEquals(1, counts[4], "an empty-state message replaces the rows");
            assertEquals(16, counts[5]);
        }
    }

    @Test
    void suspendedRowsShowTheReasonAndReactivateAgentsShowNoButton(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            Object[] state = onFx(() -> {
                Parent root = accounts(context);
                TextField search = (TextField) root.lookup("#searchField");
                search.setText("Kai");
                GridPane kai = rows(root).get(0);
                Node status = kai.getChildren().stream().filter(c -> GridPane.getColumnIndex(c) == 4).findFirst()
                        .orElseThrow();
                Node action = kai.getChildren().stream().filter(c -> GridPane.getColumnIndex(c) == 5).findFirst()
                        .orElseThrow();
                search.setText("Amy");
                GridPane amy = rows(root).get(0);
                Node amyAction = amy.getChildren().stream().filter(c -> GridPane.getColumnIndex(c) == 5).findFirst()
                        .orElseThrow();
                return new Object[] {status.lookupAll(".label").stream().map(n -> ((Label) n).getText()).toList(),
                    ((Button) action).getText(), amyAction instanceof Button};
            });

            assertEquals(List.of("SUSPENDED", "Reason: Suspended by support agent pending review"), state[0]);
            assertEquals("Reactivate", state[1]);
            assertFalse((Boolean) state[2], "agent rows have a dash, not a button");
        }
    }

    @Test
    void suspendThenReactivateThroughTheServiceRefreshesTheRowOnTheEvent(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            var wei = context.userService().authenticate("wei.zhang@snoozeshare.test");
            Object[] state = onFx(() -> {
                Parent root = accounts(context);
                ((TextField) root.lookup("#searchField")).setText("Wei");
                String before = cell(rows(root).get(0), 3);
                context.accountGovernanceService().suspend(wei.userId(),
                        context.session().currentUser().orElseThrow().userId(), "policy breach");
                return new Object[] {before, root};
            });
            // The event refresh runs through Platform.runLater; let it drain, then re-read.
            Object[] after = onFx(() -> {
                Parent root = (Parent) state[1];
                GridPane row = rows(root).get(0);
                Button action = (Button) row.getChildren().stream().filter(c -> GridPane.getColumnIndex(c) == 5)
                        .findFirst().orElseThrow();
                return new Object[] {action.getText()};
            });

            assertEquals("Reactivate", after[0]);
        }
    }

    @Test
    void theTabStripKeepsAccountsSelectedAndTheHeaderStaysFixedWhileRowsScroll(@TempDir Path directory)
            throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory); AppContext context = agentContext(db)) {
            Object[] state = onFx(() -> {
                Parent root = accounts(context);
                ScrollPane scroll = (ScrollPane) root.lookup(".agent-rows-scroll");
                Node header = root.lookup(".agent-grid-header");
                javafx.geometry.Bounds scrollBounds = scroll.localToScene(scroll.getBoundsInLocal());
                javafx.geometry.Bounds headerBounds = header.localToScene(header.getBoundsInLocal());
                return new Object[] {root.lookup("#accountsTab").getStyleClass().contains("agent-tab-active"),
                    scrollBounds.getMaxY() <= 800, scrollBounds.getMinY() >= headerBounds.getMaxY() - 0.5};
            });

            assertTrue((Boolean) state[0], "Accounts tab is the active tab");
            assertTrue((Boolean) state[1], "the list stays inside the window so its rows scroll");
            assertTrue((Boolean) state[2], "the header sits above the scrolling rows");
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AccountGovernanceUiTest" -q`
Expected: FAIL (the placeholder page shows, so `.agent-rows-scroll` is missing).

- [ ] **Step 3: Create `account-governance.fxml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.ColumnConstraints?>
<?import javafx.scene.layout.GridPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" styleClass="agent-content" xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.admin.accounts.AccountGovernanceController">
    <padding><Insets top="24" right="32" bottom="24" left="32"/></padding>

    <HBox alignment="CENTER_LEFT" spacing="10">
        <VBox spacing="2">
            <Label text="Account governance" styleClass="page-title"/>
            <Label text="Suspend or reactivate Guest and Host accounts" styleClass="small"/>
        </VBox>
        <Region HBox.hgrow="ALWAYS"/>
        <TextField fx:id="searchField" promptText="Search..." prefWidth="260" styleClass="agent-input"/>
    </HBox>

    <VBox styleClass="agent-card">
        <GridPane styleClass="agent-grid-header">
            <columnConstraints>
                <ColumnConstraints percentWidth="17"/>
                <ColumnConstraints percentWidth="24"/>
                <ColumnConstraints percentWidth="12"/>
                <ColumnConstraints percentWidth="15"/>
                <ColumnConstraints percentWidth="20"/>
                <ColumnConstraints percentWidth="12"/>
            </columnConstraints>
            <Label text="DISPLAY NAME" GridPane.columnIndex="0"/>
            <Label text="EMAIL" GridPane.columnIndex="1"/>
            <Label text="ROLE" GridPane.columnIndex="2"/>
            <Label text="JOINED" GridPane.columnIndex="3"/>
            <Label text="STATUS" GridPane.columnIndex="4"/>
            <Label text="ACTION" GridPane.columnIndex="5"/>
        </GridPane>
        <ScrollPane fitToWidth="true" hbarPolicy="NEVER" styleClass="agent-rows-scroll">
            <VBox fx:id="rows"/>
        </ScrollPane>
        <Label fx:id="emptyLabel" text="No accounts match your search" styleClass="agent-empty"/>
    </VBox>
    <Label fx:id="errorLabel" styleClass="error-message"/>
</VBox>
```

- [ ] **Step 4: Create the controller**

```java
package com.snoozeshare.ui.admin.accounts;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.AccountStatusChangedEvent;
import com.snoozeshare.service.AccountSummary;
import com.snoozeshare.ui.admin.accounts.SuspensionDialogController.Mode;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** The agent Accounts tab: live-searchable list with Suspend / Reactivate actions (F10.1.1). */
public final class AccountGovernanceController {

    private static final double[] COLUMN_SHARES = {17, 24, 12, 15, 20, 12};
    private static final String DASH = "\u2014";

    @FXML private TextField searchField;
    @FXML private VBox rows;
    @FXML private Label emptyLabel;
    @FXML private Label errorLabel;

    private AppContext context;
    private Subscription subscription;
    private List<AccountSummary> accounts = List.of();

    @FXML
    private void initialize() {
        emptyLabel.visibleProperty().bind(emptyLabel.textProperty().isNotEmpty());
        emptyLabel.managedProperty().bind(emptyLabel.visibleProperty());
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        searchField.textProperty().addListener((observable, previous, text) -> render());
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        subscription = context.eventBus().subscribe(AccountStatusChangedEvent.class,
                event -> Platform.runLater(this::refresh));
        refresh();
    }

    /** Stops listening; the shell calls this when another tab replaces the screen. */
    public void dispose() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private void refresh() {
        accounts = context.accountGovernanceService().listAccounts();
        render();
    }

    private void render() {
        rows.getChildren().clear();
        ZoneId zone = ZoneId.systemDefault();
        String query = searchField.getText();
        for (AccountSummary account : accounts) {
            if (AccountSearch.matches(account, query, zone)) {
                rows.getChildren().add(row(account));
            }
        }
        emptyLabel.setText(rows.getChildren().isEmpty() ? "No accounts match your search" : "");
    }

    private GridPane row(AccountSummary account) {
        GridPane row = new GridPane();
        row.getStyleClass().addAll("agent-grid-row", "agent-account-row");
        for (double share : COLUMN_SHARES) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(share);
            row.getColumnConstraints().add(constraints);
        }
        row.add(cell(account.displayName(), "agent-cell-strong"), 0, 0);
        row.add(cell(account.email(), "agent-cell"), 1, 0);
        row.add(cell(AccountText.role(account.role()), "agent-cell"), 2, 0);
        row.add(cell(AccountText.joined(account.createdAt()), "agent-cell"), 3, 0);
        row.add(statusCell(account), 4, 0);
        row.add(actionCell(account), 5, 0);
        row.getChildren().forEach(child -> GridPane.setValignment(child, VPos.CENTER));
        return row;
    }

    private static Label cell(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("agent-cell", styleClass);
        return label;
    }

    private static VBox statusCell(AccountSummary account) {
        boolean suspended = account.status() == AccountStatus.SUSPENDED;
        Label pill = new Label(AccountText.status(account.status()).toUpperCase(Locale.ROOT));
        pill.getStyleClass().addAll("agent-pill", suspended ? "agent-pill-danger" : "agent-pill-success");
        VBox box = new VBox(3, pill);
        if (suspended && account.suspensionReason() != null) {
            Label reason = new Label("Reason: " + account.suspensionReason());
            reason.setWrapText(true);
            reason.getStyleClass().add("small");
            box.getChildren().add(reason);
        }
        return box;
    }

    private javafx.scene.Node actionCell(AccountSummary account) {
        if (!account.governable()) {
            Label dash = new Label(DASH);
            dash.getStyleClass().add("agent-cell");
            return dash;
        }
        boolean suspended = account.status() == AccountStatus.SUSPENDED;
        Button button = new Button(suspended ? "Reactivate" : "Suspend");
        button.getStyleClass().add(suspended ? "outline-button" : "agent-button-danger");
        button.setOnAction(event -> openDialog(suspended ? Mode.REACTIVATE : Mode.SUSPEND, account));
        return button;
    }

    private void openDialog(Mode mode, AccountSummary account) {
        UUID agentId = context.session().currentUser().orElseThrow().userId();
        errorLabel.setText("");
        SuspensionDialogController.show(mode, account, reason -> {
            if (mode == Mode.SUSPEND) {
                context.accountGovernanceService().suspend(account.userId(), agentId, reason);
            } else {
                context.accountGovernanceService().reactivate(account.userId(), agentId, reason);
            }
        });
        refresh();
    }
}
```

(`Subscription.unsubscribe()` is the existing method.) The `Platform.runLater` inside the event listener is safe because `setContext` runs on the FX thread and the listener only schedules a refresh.

- [ ] **Step 5: Add row hover CSS** (in `agent-theme.css` after the `.agent-grid-row-click:hover` rule at line 158)

```css
/* Account rows are not clickable: the shared grey hover, default cursor; only the action button is a hand. */
.agent-root .agent-account-row:hover { -fx-background-color: rgba(240, 232, 216, 0.4); }
```

- [ ] **Step 6: Wire the shell**

In `AdminShellController.java`:
1. Add import `com.snoozeshare.ui.admin.accounts.AccountGovernanceController;` (alphabetical, before `audit`).
2. Add field `private AccountGovernanceController accountsController;` after `queueController`.
3. Replace `showAccounts()` with:

```java
    @FXML
    private void showAccounts() {
        selectTab(accountsTab);
        disposeLiveViews();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/admin/accounts/account-governance.fxml"));
            Node view = loader.load();
            accountsController = loader.getController();
            accountsController.setContext(getContext());
            shellRoot.setCenter(view);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load account governance", exception);
        }
    }
```

4. Rename `disposeQueue` to `disposeLiveViews` at every call site in the file (`sed -i 's/disposeQueue/disposeLiveViews/g' src/main/java/com/snoozeshare/ui/admin/AdminShellController.java`) and replace its body with:

```java
    private void disposeLiveViews() {
        if (queueController != null) {
            queueController.dispose();
            queueController = null;
        }
        if (accountsController != null) {
            accountsController.dispose();
            accountsController = null;
        }
    }
```

`displayPage` and `restoreDefaultCenter` remain (the Audit and other placeholders no longer need them, but `restoreDefaultCenter` is still referenced by `showAccounts`' predecessor only; if it is now unused, delete `restoreDefaultCenter`, `defaultCenter` usage and the `initialize` body only if `grep -n "restoreDefaultCenter\|defaultCenter"` shows no remaining callers, otherwise leave them).

- [ ] **Step 7: Run and iterate**

Run: `./gradlew test --tests "*AccountGovernanceUiTest" --tests "com.snoozeshare.ui.admin.*" checkstyleMain checkstyleTest -q`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(w11): Accounts screen with live search, fixed header and Suspend/Reactivate actions

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 13: Layout, hover and snapshot tests; existing layout test update

**Files:**
- Modify: `src/test/java/com/snoozeshare/ui/admin/AdminFxmlLayoutTest.java`
- Modify: `src/test/java/com/snoozeshare/ui/admin/AdminTableLayoutTest.java`
- Modify: `src/test/java/com/snoozeshare/ui/admin/AdminModalSnapshotTest.java`

- [ ] **Step 1: FXML layout test**

Append to `AdminFxmlLayoutTest`:

```java
    @Test
    void accountsScreenAndDialogHaveTheSpecifiedControls() throws Exception {
        String screen = read("accounts/account-governance.fxml");
        for (String id : new String[] {"searchField", "rows", "emptyLabel", "errorLabel"}) {
            assertTrue(screen.contains("fx:id=\"" + id + "\""), id);
        }
        for (String header : new String[] {"DISPLAY NAME", "EMAIL", "ROLE", "JOINED", "STATUS", "ACTION"}) {
            assertTrue(screen.contains("text=\"" + header + "\""), header);
        }
        assertTrue(screen.contains("promptText=\"Search...\""));
        assertTrue(screen.contains("agent-rows-scroll"), "fixed header, only rows scroll (C33)");
        assertFalse(screen.contains("Username"));

        String dialog = read("accounts/suspension-dialog.fxml");
        for (String id : new String[] {"titleLabel", "bannerBox", "nameLabel", "emailLabel", "metaLabel",
            "noteLabel", "reasonArea", "errorLabel", "cancelButton", "confirmButton"}) {
            assertTrue(dialog.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(dialog.contains("Reason (required, written to the audit log)"));
    }
```

- [ ] **Step 2: Table layout test (scroll bar under the header, hover, no hand cursor)**

Append to `AdminTableLayoutTest` (all needed imports already exist in the file; add `javafx.scene.layout.GridPane` if absent):

```java
    @Test
    void accountListKeepsItsHeaderFixedAndRowsHighlightOnHoverWithoutAHandCursor(@TempDir Path directory)
            throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
            onFx(() -> {
                Parent root = show(context, "showAccounts");
                ScrollPane scroll = (ScrollPane) root.lookup(".agent-rows-scroll");
                Node header = root.lookup(".agent-grid-header");
                ScrollBar vertical = scroll.lookupAll(".scroll-bar").stream().map(node -> (ScrollBar) node)
                        .filter(bar -> bar.getOrientation() == Orientation.VERTICAL && bar.isVisible())
                        .findFirst().orElseThrow(() -> new AssertionError("no vertical scroll bar"));
                assertTrue(inScene(scroll).getMaxY() <= 800, "the list stays inside the window");
                assertTrue(inScene(vertical).getMinY() >= inScene(header).getMaxY() - 0.5,
                        "the scroll bar starts below the header row");

                Node row = root.lookup(".agent-account-row");
                row.pseudoClassStateChanged(HOVER, true);
                root.applyCss();
                Background background = ((Region) row).getBackground();
                Paint fill = background.getFills().get(0).getFill();
                assertTrue(fill instanceof Color color && color.getOpacity() > 0.2 && color.getOpacity() < 0.6,
                        "translucent hover highlight, like the queue: " + fill);
                assertEquals(null, row.getCursor() == Cursor.HAND ? Cursor.HAND : null, "no hand cursor on rows");
                AdminUiSnapshotTest.writePng(root.getScene().snapshot(null), "agent-accounts");
                return null;
            });
        }
    }
```

- [ ] **Step 3: Modal snapshots**

Append to `AdminModalSnapshotTest` (add imports `java.time.Instant`, `com.snoozeshare.domain.enums.AccountStatus`, `com.snoozeshare.domain.enums.Role`, `com.snoozeshare.service.AccountSummary`, `com.snoozeshare.ui.admin.accounts.SuspensionDialogController`; `Path`, `Files`, `UUID`, `Parent`, `Stage`, `assertTrue`, `assumeTrue` are already imported):

```java
    @Test
    void writesSuspendAndReactivateModalSnapshots() throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        AccountSummary priya = new AccountSummary(UUID.randomUUID(), "Priya Nair", "priya.nair@snoozeshare.test",
                Role.GUEST, Instant.parse("2026-03-05T08:00:00Z"), AccountStatus.ACTIVE, null);
        for (SuspensionDialogController.Mode mode : SuspensionDialogController.Mode.values()) {
            Path file = AdminUiSnapshotTest.onFx(() -> {
                Stage stage = SuspensionDialogController.createDialog(mode, priya, reason -> { });
                stage.show();
                Parent card = stage.getScene().getRoot();
                card.applyCss();
                card.layout();
                Path written = AdminUiSnapshotTest.writePngOver(stage.getScene().snapshot(null),
                        "agent-" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-modal",
                        new java.awt.Color(0xb0, 0xc0, 0xff));
                stage.close();
                return written;
            });
            assertTrue(Files.size(file) > 0);
        }
    }
```

- [ ] **Step 4: Run and inspect**

Run: `./gradlew test --tests "com.snoozeshare.ui.admin.*" -q`
Expected: PASS. Open `build/ui-snapshots/agent-accounts.png`, `agent-suspend-modal.png`, `agent-reactivate-modal.png` and compare with the canvas boards `AgentAccounts` and `ConfirmSuspendAccount` (fonts: Display Name column, Email column, `DD MMM YYYY`, green `#40680C` banner on Reactivate).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test(w11): accounts layout, fixed header, hover and modal snapshots

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 14: Full build, real-app check, state and ledger

**Files:**
- Modify: `PROJECT_STATE.md`, `docs/project-state/done-ledger.md`

- [ ] **Step 1: Full verification**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL (checkstyle + every test). Report the real test count in the ledger.

- [ ] **Step 2: Run the real app on a copy of the mock DB**

```bash
cp db/snoozeshare-mock.db build/acceptance.db
SNOOZESHARE_DB_URL=jdbc:sqlite:build/acceptance.db ./gradlew run
```

Log in as `amy.tanaka@snoozeshare.test`, open Accounts, search `mar`, suspend a guest with a booking, confirm the Audit Log tab shows `ACCOUNT_SUSPENDED`, `BOOKING_FORCE_CANCELLED` and `ESCROW_REFUND` rows, then reactivate and confirm the row flips. If the app cannot be driven in this environment, say so and leave D14-style operator acceptance in the state file.

- [ ] **Step 3: Update `PROJECT_STATE.md`**

- § Workstreams W11: Status `Done` only after the operator confirms; until then `In review`, Progress `All 14 tasks done; awaiting operator acceptance`, Guide `—`.
- § Architecture 4.2 UI: add a **W11 Accounts screen (built)** paragraph (files, fixed-header table, modal, search) after the W10 agent screens paragraph. 4.3 Service: add `AccountGovernanceServiceImpl` (atomic suspend + cascade, C36) and the removed `UserService.suspend`. 4.5 Repository: `users.suspensionReason` (V003), `UserRepository.findAll`, `BookingRepository.findByListing`. 4.6 Events: `AccountStatusChangedEvent`.
- § Deviations: mark D19 RESOLVED; add D20 — "the Accounts list hides the System user; `BookingServiceImpl` gained a `UserRepository` constructor parameter and a suspended-guest guard; `MigrationRunner` now handles V003".
- Session row S7: Doing → `W11 implemented; awaiting operator review`; Last touched today.

- [ ] **Step 4: Add the ledger entry** at the top of the table in `docs/project-state/done-ledger.md`:

`| 2026-09-26 | Implemented W11 Agent Account Governance (F10.1.1, F10.1.2; Reactivate added): `users.suspensionReason` (V003), `AccountGovernanceService` (atomic suspend + cascade of pending and upcoming bookings with refund, listings deactivated, W12 audit rows), suspended-guest booking guard, Accounts tab with live search, fixed header and Suspend/Reactivate modal; `gradlew clean build` green with N tests | W11 | plan `docs/superpowers/plans/2026-09-26-w11-account-governance.md`, spec `docs/superpowers/specs/2026-09-26-w11-account-governance-design.md` |` (replace N with the real count) and bump the § Record entry count and latest date.

- [ ] **Step 4b: Log the session** if the operator ends here: invoke the `logging-agent-interactions` skill (manual, at session end).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: W11 implemented, state and ledger updated

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage** (spec § → task):
- § 3.1.1 Accounts screen (columns, search, pill, reason, actions, dash for agents, System user hidden) → Tasks 4, 10, 12, 13.
- § 3.1.2–3 Suspend/Reactivate modals (name large + email row, `DD MMM YYYY`, `#40680C`, required reason) → Tasks 10, 11.
- § 3.1.4 cascade incl. C36 scope, refunds, block release, `FORCE_CANCELLED` → Tasks 6, 7.
- § 3.1.5 enforcement (booking guard; login/listing guards already exist) → Task 9.
- § 3.1.6 reactivate semantics → Tasks 5, 7.
- § 3.1.7 audit shapes → Tasks 5, 6, 7 (asserted on rows).
- § 4.2 data (V003, schema, seed reasons, mock DB rebuilt, `User` component) → Tasks 1, 2.
- § 4.3 service (validation, one transaction, event after commit, stub removed) → Tasks 4, 5, 9.
- § 4.4 UI (shell wiring, fixed header, hover, banner classes, formatter, placeholder `Search...`) → Tasks 11, 12, 13.
- § 5 testing list → Tasks 1–13 (service, repo, migration, guard, FXML/table/smoke/snapshot, search, date).
- Spec § 6 D19 → Task 9 (removal), Task 14 (state).

**Placeholder scan:** the only scaffold text is the temporary `UnsupportedOperationException("Task 5")` in Task 4, explicitly replaced in Task 5. Task 13 Step 4's "open the PNGs and compare with the canvas" is a manual visual check, not code.

**Type consistency:** `AccountSummary(userId, displayName, email, role, createdAt, status, suspensionReason)` and `governable()` are used identically in Tasks 4, 10, 11, 12. `AccountGovernanceService` methods `listAccounts()`, `suspend(userId, agentId, reason)`, `reactivate(userId, agentId, reason)` match in Tasks 4, 5, 9, 12. `SuspensionDialogController.createDialog(Mode, AccountSummary, Consumer<String>)` matches Tasks 11–13. `AppContext.accountGovernanceService()` is added in Task 9 and used in Task 12. `AccountFixture` members (`user`, `property`, `booking`, `balance`, `service()`, `service(AuditService)`, `bus`, `audit`, `agent`, `users`, `bookings`, `blocks`, `properties`, `connection`) are used consistently in Tasks 4–8.
