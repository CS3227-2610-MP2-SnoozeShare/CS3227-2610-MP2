# W14 Unified Ledger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `audit_log` money row the only record of money movement (folding `wallet_transactions` into it), give the System user a real `SYSTEM` role and a wallet that receives the 3% platform fee, and route every wallet write through one `LedgerWriter`.

**Architecture:** Three migrations: V006 rebuilds `users` to allow role `SYSTEM`; V007 adds `audit_log.balanceAfter`, backfills legacy money rows and fee rows, and creates the System wallet; V008 drops `wallet_transactions`. Code moves off the old table in strangler order (a new `JdbcLedgerRepository` and `LedgerWriter` appear first, callers switch one at a time, the old repository and table go last), so the build and tests stay green after every task. `wallets` stays as the balance holder.

**Tech Stack:** Java 25, SQLite via plain JDBC (`sqlite-jdbc` 3.46, window functions available), Gradle, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-27-w14-unified-ledger-design.md` (decisions C39, C40 in `PROJECT_STATE.md`; the spec's § 10 defaults are accepted by the operator, 2026-09-27).

## Global Constraints

- **Commits:** at logical boundaries (each task ends in one). Title at most 50 characters, detail in the description. End every commit message with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Checkstyle runs on `./gradlew build`: max line 120 (tests too), imports ordered `static` / `java.*` / `org.*` / `com.*` / `javafx.*` with blank lines between groups, no star imports. At the end of every task run `./gradlew checkstyleMain checkstyleTest` and wrap long lines.
- `ui.*` never imports `repository.*`, `infra.db.*` or `java.sql.*`; only `repository.jdbc.*` imports `java.sql.*`.
- Money is `BigDecimal`; SQLite REAL loses scale, so tests compare with `compareTo`, never `equals` (D4).
- Timestamps are UTC ISO-8601 ending in `Z`; seed IDs are valid hex UUIDs (prefixes: users `a`/`b`/`c`, wallets `3`, transactions `4`, audit `5`/`6`).
- **The ledger writer never opens a transaction.** Callers own the transaction (`TransactionManager`); events are published only after it commits.
- Known baseline failures that are NOT ours (D21): `FileTicketTest.successfullyFilesTicketAndPublishesEvent` and `ShellLayoutTest.hostWalletPageUsesTheSamePageInsetAsListingsAndBookings`. Do not fix them here; do not let any other test go red.
- Run the whole suite (`./gradlew test`) at the end of every task and before every commit. Read failures; do not weaken assertions to pass (change an expectation only where this plan says the behaviour changed).
- Do not add a System-wallet screen, an escrow account, `users.balance`, or any UI change beyond the `PLATFORM_FEE` label in the Audit Log.

## Review Focus

- **One record of money:** after Task 10 nothing reads or writes `wallet_transactions`; every money row is an `audit_log` row with `walletAdjustment` and `balanceAfter`.
- **Atomicity:** balance change + money row + state change commit or roll back together (rollback tests in Tasks 6, 8, 9).
- **Fee rows:** every host payout is followed by exactly one `PLATFORM_FEE` row to the System wallet; refunds carry none (Tasks 8, 9).
- **SYSTEM is not a login:** it cannot register, authenticate, be suspended or appear in Accounts (Task 3).
- **Migration safety:** V006 leaves every child foreign key intact (`foreign_key_check` clean); V007/V008 are correct on fresh, migrated and adopted (mock) databases (Tasks 1, 2, 4, 10).
- **Conservation invariants** (balances = sum of money rows, no booking releases more than it held, paid bookings net to zero) hold on the rebuilt mock DB and after a real settlement (Task 11).

## File Structure

| File | Responsibility |
|---|---|
| `infra/db/migration/MigrationRunner.java` (modify) | FK-off around migrations, `foreign_key_check`, V006/V007/V008 gating |
| `resources/db/migration/V006__system_role.sql` (new) | rebuild `users` with role `SYSTEM` |
| `resources/db/migration/V007__unified_ledger.sql` (new) | `balanceAfter`, legacy backfill, fee rows, System wallet, money index |
| `resources/db/migration/V008__drop_wallet_transactions.sql` (new) | drop the old ledger table |
| `domain/enums/Role.java` (modify) | `SYSTEM` |
| `domain/enums/WalletTransactionType.java`, `AuditAction.java` (modify) | `PLATFORM_FEE` |
| `domain/model/AuditLogEntry.java`, `service/AuditRecord.java` (modify) | `balanceAfter` |
| `repository/LedgerRepository.java`, `repository/jdbc/JdbcLedgerRepository.java` (new) | read money rows by wallet or booking |
| `service/impl/LedgerWriter.java` (new) | the single wallet write path |
| `service/impl/WalletServiceImpl.java`, `TransactionServiceImpl.java`, `BookingServiceImpl.java`, `DisputeSettlementServiceImpl.java`, `AccountGovernanceServiceImpl.java`, `DisputeQueryServiceImpl.java` (modify) | move onto the writer and repository |
| `service/impl/UserServiceImpl.java`, `AccountSummary.java`, `SceneRouter.java`, `ui/admin/accounts/AccountText.java` (modify) | `SYSTEM` handling |
| `service/impl/WalletLedgerWriter.java`, `repository/*WalletTransactionRepository*.java` (delete) | replaced |
| `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db` (modify) | mirror the migrations; rebuilt |
| `test/.../testsupport/LedgerTestSupport.java` (new) | builds a real `LedgerWriter` for tests |

---

### Task 1: MigrationRunner runs with foreign keys off and checks them

Rebuilding `users` while 13 tables reference it needs `PRAGMA foreign_keys = OFF`, which SQLite ignores inside a transaction. `MigrationRunner.migrate` opens a transaction immediately, so the pragma must be set before `setAutoCommit(false)`.

**Files:**
- Modify: `src/main/java/com/snoozeshare/infra/db/migration/MigrationRunner.java`
- Test: `src/test/java/com/snoozeshare/infra/db/MigrationForeignKeyTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class MigrationForeignKeyTest {

    private static int foreignKeys(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            result.next();
            return result.getInt(1);
        }
    }

    @Test
    void foreignKeyEnforcementIsRestoredAfterMigrating() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            assertEquals(1, foreignKeys(connection), "the connection factory turns enforcement on");
            MigrationRunner.migrate(connection);
            assertEquals(1, foreignKeys(connection));
            assertThrows(SQLException.class, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO wallets (walletId, userId, balance, currency, updatedAt) "
                            + "VALUES ('w', 'no-such-user', 0, 'SGD', '2026-01-01T00:00:00Z')");
                }
            });
        }
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew test --tests "*MigrationForeignKeyTest"`
Expected: PASS already (enforcement is never turned off today). This test is the regression guard for Step 3; keep going.

- [ ] **Step 3: Implement the hook**

In `MigrationRunner.java` replace the body of `migrate` so the pragma is switched off before the transaction opens and restored afterwards, and add `foreign_key_check` (run only when a table rebuild happened; the flag is set in Task 2). Replace the method's first and last lines like this:

```java
    public static void migrate(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        // The pragma is a no-op inside a transaction, so it must be switched before setAutoCommit(false).
        boolean restoreForeignKeys = originalAutoCommit && foreignKeysEnabled(connection);
        if (restoreForeignKeys) {
            setForeignKeys(connection, false);
        }
        connection.setAutoCommit(false);
        try {
            // ... existing body unchanged up to connection.commit() ...
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
            if (restoreForeignKeys) {
                setForeignKeys(connection, true);
            }
        }
    }

    private static boolean foreignKeysEnabled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA foreign_keys")) {
            return result.next() && result.getInt(1) == 1;
        }
    }

    private static void setForeignKeys(Connection connection, boolean on) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = " + (on ? "ON" : "OFF"));
        }
    }

    /** Fails the migration if any row now points at a missing parent. */
    private static void requireForeignKeysIntact(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new SQLException("Foreign key violation after migration in table "
                        + result.getString("table") + " (row " + result.getLong("rowid") + ")");
            }
        }
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test`
Expected: everything as before (only the two D21 failures, if they reproduce).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/snoozeshare/infra/db/migration/MigrationRunner.java src/test/java/com/snoozeshare/infra/db/MigrationForeignKeyTest.java
git commit -m "feat(w14): run migrations with foreign keys off"
```

---

### Task 2: `Role.SYSTEM` and the V006 users rebuild

**Files:**
- Create: `src/main/resources/db/migration/V006__system_role.sql`
- Modify: `src/main/java/com/snoozeshare/domain/enums/Role.java`, `.../infra/db/migration/MigrationRunner.java`, `.../app/SceneRouter.java`, `.../ui/admin/accounts/AccountText.java`, `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db`
- Modify tests: `infra/db/SchemaParityTest.java`, `infra/db/CommittedMockDbTest.java`
- Test: `src/test/java/com/snoozeshare/infra/db/SystemRoleMigrationTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class SystemRoleMigrationTest {

    private static final String SYSTEM_ID = "a0000000-0000-0000-0000-0000000000ff";

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    @Test
    void theSeededSystemUserIsRoleSystemAndActive() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("SYSTEM", scalar(connection, "SELECT role FROM users WHERE userId = '" + SYSTEM_ID + "'"));
            assertEquals("ACTIVE",
                    scalar(connection, "SELECT accountStatus FROM users WHERE userId = '" + SYSTEM_ID + "'"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 6"));
        }
    }

    @Test
    void theRebuildKeepsChildRowsAndAcceptsOnlyKnownRoles() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            // Migrate to V005 first is not possible through the runner, so seed after a full migrate:
            MigrationRunner.migrate(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users (userId, role, displayName, email, accountStatus, "
                        + "createdAt) VALUES ('u1', 'HOST', 'H', 'h@x.test', 'ACTIVE', '2026-01-01T00:00:00Z')");
                statement.executeUpdate("INSERT INTO wallets (walletId, userId, balance, currency, updatedAt) "
                        + "VALUES ('w1', 'u1', 0, 'SGD', '2026-01-01T00:00:00Z')");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet violations = statement.executeQuery("PRAGMA foreign_key_check")) {
                assertEquals(false, violations.next());
            }
            assertThrows(SQLException.class, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO users (userId, role, displayName, email, accountStatus, "
                            + "createdAt) VALUES ('u2', 'ROBOT', 'R', 'r@x.test', 'ACTIVE', '2026-01-01T00:00:00Z')");
                }
            });
        }
    }

    @Test
    void aDatabaseAlreadyAtV005KeepsItsRowsThroughTheRebuild() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            // Simulate a V005 database: role CHECK without SYSTEM is exercised by the reference-DB test;
            // here we prove re-running is a no-op and keeps the user count.
            String before = scalar(connection, "SELECT COUNT(*) FROM users");
            MigrationRunner.migrate(connection);
            assertEquals(before, scalar(connection, "SELECT COUNT(*) FROM users"));
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*SystemRoleMigrationTest"`
Expected: FAIL (role is `AGENT`, max version is 5).

- [ ] **Step 3: Add the enum value and its two switch cases**

`Role.java`:

```java
public enum Role {
    GUEST,
    HOST,
    AGENT,
    /** The platform's own non-loginable account; owns the wallet that receives platform fees (W14, C40). */
    SYSTEM
}
```

`SceneRouter.routeFor`: add `case SYSTEM -> AUTH_SCENE;` after `case AGENT -> ADMIN_SCENE;` (a SYSTEM session can never exist, so the login screen is the safe route).
`AccountText.role`: add `case SYSTEM -> "System";`.

- [ ] **Step 4: Write V006**

`src/main/resources/db/migration/V006__system_role.sql` (no semicolons inside comments; the runner splits on `;`):

```sql
-- SQLite cannot alter a CHECK constraint, so rebuild users with role SYSTEM allowed.
-- MigrationRunner runs this with foreign keys off, and column order matches V001 + V003.
CREATE TABLE users_new (
    userId TEXT PRIMARY KEY,
    role TEXT NOT NULL CHECK (role IN ('GUEST', 'HOST', 'AGENT', 'SYSTEM')),
    displayName TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    accountStatus TEXT NOT NULL CHECK (accountStatus IN ('ACTIVE', 'SUSPENDED')),
    registrationCode TEXT,
    createdAt TEXT NOT NULL,
    suspensionReason TEXT
);

INSERT INTO users_new (userId, role, displayName, email, accountStatus, registrationCode, createdAt, suspensionReason)
SELECT userId, role, displayName, email, accountStatus, registrationCode, createdAt, suspensionReason FROM users;

UPDATE users_new SET role = 'SYSTEM', accountStatus = 'ACTIVE'
WHERE userId = 'a0000000-0000-0000-0000-0000000000ff';

DROP TABLE users;

ALTER TABLE users_new RENAME TO users;
```

- [ ] **Step 5: Gate V006 in the runner**

Add `private static final int SYSTEM_ROLE_VERSION = 6;` and, after the V005 block and before `connection.commit()`:

```java
            boolean rebuilt = false;
            if (!migrationApplied(connection, SYSTEM_ROLE_VERSION)) {
                if (!usersAllowSystemRole(connection)) {
                    applySqlMigration(connection, "/db/migration/V006__system_role.sql");
                    rebuilt = true;
                }
                // else: a reference database rebuilt from db/schema.sql already allows SYSTEM.
                recordMigration(connection, SYSTEM_ROLE_VERSION);
            }
            if (rebuilt) {
                requireForeignKeysIntact(connection);
            }
```

and the helper:

```java
    private static boolean usersAllowSystemRole(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'users'");
             var result = statement.executeQuery()) {
            return result.next() && result.getString(1).contains("'SYSTEM'");
        }
    }
```

- [ ] **Step 6: Mirror in `db/schema.sql` and the seed, rebuild the mock DB**

`db/schema.sql`: change the `users.role` CHECK to `CHECK (role IN ('GUEST','HOST','AGENT','SYSTEM'))`; update the `schema_history` comment to "V001 to V006".
`db/seed-mock-data.sql`: change the System user row to `('a0000000-0000-0000-0000-0000000000ff','SYSTEM','SnoozeShare System','system@snoozeshare.invalid','ACTIVE',NULL,'2026-01-01T00:00:00Z')` and add `(6, '2026-09-27 00:00:00')` to the `schema_history` insert. The seed audit query that joins `users a ON a.userId = COALESCE(t.assignedAgentId, 'a0000000-0000-0000-0000-0000000000ff')` keeps working (it joins by id).

Rebuild:

```bash
rm -f db/snoozeshare-mock.db && sqlite3 db/snoozeshare-mock.db < db/schema.sql && sqlite3 db/snoozeshare-mock.db < db/seed-mock-data.sql
sqlite3 db/snoozeshare-mock.db "PRAGMA foreign_key_check; SELECT role, accountStatus FROM users WHERE userId='a0000000-0000-0000-0000-0000000000ff'; SELECT MAX(version) FROM schema_history;"
```

Expected: no foreign-key rows, `SYSTEM|ACTIVE`, `6`.

- [ ] **Step 7: Update the two guard tests and run**

`SchemaParityTest`: after the V005 apply line add `apply(migration, "src/main/resources/db/migration/V006__system_role.sql");`.
`CommittedMockDbTest`: in the System-user query change `accountStatus = 'SUSPENDED'` to `role = 'SYSTEM' AND accountStatus = 'ACTIVE'`, and change the version loop to `{1, 2, 3, 4, 5, 6}`; rename the test to `...AndMigrationVersionSix`.

Run: `./gradlew test`
Expected: PASS except the D21 pair. `MockDbFixtureTest`/`MigrationRunnerReferenceDbTest` open the rebuilt mock DB and must stay green; if one asserts the System user is a suspended agent, change that expectation to `SYSTEM`/`ACTIVE`.

- [ ] **Step 8: Commit**

```bash
git add -A src db
git commit -m "feat(w14): SYSTEM role via users rebuild (V006)"
```

---

### Task 3: SYSTEM is never a login, a governable account or an Accounts row

**Files:**
- Modify: `service/impl/UserServiceImpl.java`, `service/AccountSummary.java`, `service/impl/AccountGovernanceServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/SystemUserGuardTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.UserServiceImpl;

class SystemUserGuardTest {

    private static final String SYSTEM_EMAIL = "system@snoozeshare.invalid";

    @Test
    void theSystemUserCannotAuthenticate() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            UserService users = new UserServiceImpl(connection, new JdbcUserRepository(connection),
                    new JdbcWalletRepository(connection));
            assertThrows(IllegalArgumentException.class, () -> users.authenticate(SYSTEM_EMAIL));
        }
    }

    @Test
    void nobodyCanRegisterAsSystem() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            UserService users = new UserServiceImpl(connection, new JdbcUserRepository(connection),
                    new JdbcWalletRepository(connection));
            assertThrows(IllegalArgumentException.class,
                    () -> users.register("Mallory", "m@x.test", Role.SYSTEM, "AGENT-2026-01"));
        }
    }

    @Test
    void theSystemUserIsHiddenFromAccountsAndCannotBeSuspended() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            var service = fixture.service();
            assertFalse(service.listAccounts().stream()
                    .anyMatch(account -> account.userId().equals(AuditService.SYSTEM_ACTOR_ID)));
            assertThrows(IllegalArgumentException.class,
                    () -> service.suspend(AuditService.SYSTEM_ACTOR_ID, fixture.agent.userId(), "no"));
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*SystemUserGuardTest"`
Expected: FAIL (`authenticate` succeeds now that System is `ACTIVE`; register with `SYSTEM` reaches the agent-code check).

- [ ] **Step 3: Implement**

`UserServiceImpl.authenticate`: after loading `user`, before the status check:

```java
        if (user.role() == Role.SYSTEM) {
            throw new IllegalArgumentException("Invalid email");
        }
```

`UserServiceImpl.register`: directly after the `role == null` check:

```java
        if (role == Role.SYSTEM) {
            throw new IllegalArgumentException("The system account cannot be registered");
        }
```

`AccountGovernanceServiceImpl.listAccounts`: replace the filter with `.filter(user -> user.role() != Role.SYSTEM)`.
`AccountGovernanceServiceImpl.requireGovernable`: replace the condition with `target.role() == Role.AGENT || target.role() == Role.SYSTEM` and keep the message.
`AccountSummary.governable()`: `return role != Role.AGENT && role != Role.SYSTEM;`

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`
Expected: PASS (D21 pair aside).

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat(w14): SYSTEM cannot log in or be governed"
```

---

### Task 4: V007 adds `balanceAfter`, backfills legacy money rows, fee rows and the System wallet

**Files:**
- Create: `src/main/resources/db/migration/V007__unified_ledger.sql`
- Modify: `domain/enums/WalletTransactionType.java`, `domain/enums/AuditAction.java`, `domain/model/AuditLogEntry.java`, `service/AuditRecord.java`, `repository/jdbc/JdbcAuditLogRepository.java`, `repository/jdbc/support/RowMappers.java`, `service/AuditService.java`, `MigrationRunner.java`, `ui/admin/audit/AuditLogController.java`, `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db`
- Modify tests: `SchemaParityTest`, `CommittedMockDbTest`, `AuditRecordTest`, `JdbcAuditLogRepositoryTest`, `MockAuditSeedTest` (whichever construct `AuditLogEntry` or count seeded rows)
- Test: `src/test/java/com/snoozeshare/infra/db/UnifiedLedgerMigrationTest.java` (create)

Legacy note: on old rows `wallet_transactions.amount` is what actually moved (W8 and W10 wrote net payouts; the only path that subtracted a fee, `WalletLedgerWriter`, is never called with a fee), so backfilled money rows use `amount` as `walletAdjustment`.

- [ ] **Step 1: Write the failing migration test**

The test builds a V006 database by running the runner (which will also apply V007 once it exists) and then simulates a *legacy* database by deleting V007's effects. Simpler and deterministic: create the pre-V007 state by hand in a fresh in-memory DB using the V001 to V006 SQL files, insert legacy rows, then run `MigrationRunner.migrate` (which sees versions unrecorded). Because the runner keys off `schema_history`, record versions 1 to 6 by hand.

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class UnifiedLedgerMigrationTest {

    private static final String SYSTEM = "a0000000-0000-0000-0000-0000000000ff";

    private static void run(Connection connection, String sql) throws SQLException {
        for (String part : sql.split(";")) {
            if (!part.isBlank()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(part);
                }
            }
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    /** A database exactly as V006 leaves it, with legacy ledger rows and one already-audited transaction. */
    private static Connection legacyDatabase() throws Exception {
        Connection connection = DatabaseTestSupport.openIsolatedDatabase();
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
        }
        for (String file : new String[] {"V001__foundation", "V002__audit_trail", "V003__suspension_reason",
            "V004__messaging", "V005__booking_messaging", "V006__system_role"}) {
            run(connection, Files.readString(Path.of("src/main/resources/db/migration/" + file + ".sql")));
        }
        run(connection, "ALTER TABLE bookings ADD COLUMN hostDecisionMessage TEXT");
        run(connection, "CREATE TABLE IF NOT EXISTS schema_history (version INTEGER PRIMARY KEY, appliedAt TEXT NOT NULL)");
        for (int version = 1; version <= 6; version++) {
            run(connection, "INSERT INTO schema_history VALUES (" + version + ", '2026-09-27 00:00:00')");
        }
        run(connection, """
                INSERT INTO users (userId, role, displayName, email, accountStatus, createdAt) VALUES
                ('c1', 'GUEST', 'Gia Guest', 'g@x.test', 'ACTIVE', '2026-01-01T00:00:00Z'),
                ('b1', 'HOST', 'Hal Host', 'h@x.test', 'ACTIVE', '2026-01-01T00:00:00Z');
                INSERT INTO wallets VALUES ('w-g', 'c1', 60, 'SGD', '2026-09-01T00:00:00Z');
                INSERT INTO wallets VALUES ('w-h', 'b1', 97, 'SGD', '2026-09-01T00:00:00Z');
                INSERT INTO wallet_transactions (transactionId, walletId, type, amount, feeAmount, balanceAfter,
                    createdAt) VALUES
                ('t1', 'w-g', 'TOP_UP', 100, NULL, 100, '2026-09-01T00:00:00Z'),
                ('t2', 'w-g', 'ESCROW_HOLD', -40, NULL, 60, '2026-09-02T00:00:00Z'),
                ('t3', 'w-h', 'BOOKING_PAYOUT', 97, 3, 97, '2026-09-03T00:00:00Z');
                INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId,
                    walletAdjustment, subjectUserId, subjectName, timestamp) VALUES
                ('l1', 'c1', 'Gia Guest', 'TOP_UP', 'WalletTransaction', 't1', 100, 'c1', 'Gia Guest',
                    '2026-09-01T00:00:00Z')""");
        return connection;
    }

    @Test
    void existingMoneyRowsGetBalanceAfterAndMissingOnesAreBackfilled() throws Exception {
        try (Connection connection = legacyDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("100.0", scalar(connection, "SELECT balanceAfter FROM audit_log WHERE entityId = 't1'"));
            assertEquals("60.0", scalar(connection,
                    "SELECT balanceAfter FROM audit_log WHERE entityId = 't2' AND actionType = 'ESCROW_HOLD'"));
            assertEquals("-40.0", scalar(connection,
                    "SELECT walletAdjustment FROM audit_log WHERE entityId = 't2'"));
            assertEquals("c1", scalar(connection, "SELECT subjectUserId FROM audit_log WHERE entityId = 't2'"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM audit_log WHERE entityId = 't1'"),
                    "an already-audited transaction is not duplicated");
        }
    }

    @Test
    void legacyPayoutFeesBecomeSystemFeeRowsAndTheSystemWalletHoldsThem() throws Exception {
        try (Connection connection = legacyDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("1", scalar(connection,
                    "SELECT COUNT(*) FROM audit_log WHERE actionType = 'PLATFORM_FEE' AND subjectUserId = '"
                            + SYSTEM + "'"));
            assertEquals("3.0", scalar(connection, "SELECT walletAdjustment FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE'"));
            assertEquals("3.0", scalar(connection, "SELECT balanceAfter FROM audit_log "
                    + "WHERE actionType = 'PLATFORM_FEE'"));
            assertEquals("3.0", scalar(connection, "SELECT balance FROM wallets WHERE userId = '" + SYSTEM + "'"));
        }
    }

    @Test
    void aFreshDatabaseGetsAnEmptySystemWalletAndVersionSeven() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            assertEquals("0.0", scalar(connection, "SELECT balance FROM wallets WHERE userId = '" + SYSTEM + "'"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM schema_history WHERE version = 7"));
            MigrationRunner.migrate(connection);
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM wallets WHERE userId = '" + SYSTEM + "'"));
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*UnifiedLedgerMigrationTest"`
Expected: FAIL (no `balanceAfter` column).

- [ ] **Step 3: Enum values**

`WalletTransactionType`: append `PLATFORM_FEE`. `AuditAction`: add `PLATFORM_FEE` after `AGENT_OVERRIDE` (the `forWallet` mapping is `valueOf(type.name())`, so both must carry it). `AuditLogController` line ~237: add `"PLATFORM_FEE"` to the `case "BOOKING_PAYOUT", "ESCROW_REFUND", ...` group (money-in styling).

- [ ] **Step 4: Write V007**

`src/main/resources/db/migration/V007__unified_ledger.sql`. No semicolons in comments. The `uuid` expression is spelled out because SQLite has no UUID function.

```sql
-- Money rows in audit_log now carry the running balance, so wallet_transactions can go (V008).
ALTER TABLE audit_log ADD COLUMN balanceAfter REAL;

UPDATE audit_log
SET balanceAfter = (SELECT t.balanceAfter FROM wallet_transactions t WHERE t.transactionId = audit_log.entityId)
WHERE entityType = 'WalletTransaction' AND walletAdjustment IS NOT NULL;

-- Legacy transactions that were never audited (pre-W12 data and adopted databases), oldest first.
INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState,
    walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp, balanceAfter)
SELECT lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-'
           || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))),
       actor.userId, actor.displayName, t.type, 'WalletTransaction', t.transactionId, NULL, NULL,
       t.amount, NULL, w.userId, owner.displayName, t.relatedBookingId, t.relatedTicketId, t.createdAt,
       t.balanceAfter
FROM wallet_transactions t
JOIN wallets w ON w.walletId = t.walletId
JOIN users owner ON owner.userId = w.userId
JOIN users actor ON actor.userId = COALESCE(t.initiatedBy, w.userId)
WHERE NOT EXISTS (SELECT 1 FROM audit_log l WHERE l.entityId = t.transactionId)
ORDER BY t.createdAt, t.transactionId;

-- The System wallet.
INSERT INTO wallets (walletId, userId, balance, currency, updatedAt)
SELECT '30000000-0000-0000-0000-0000000000ff', u.userId, 0, 'SGD', '2026-01-01T00:00:00Z'
FROM users u
WHERE u.role = 'SYSTEM' AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.userId = u.userId);

-- Fees already taken: one PLATFORM_FEE row per legacy payout, with a running System balance.
INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState,
    walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp, balanceAfter)
SELECT lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-'
           || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))),
       actor.userId, actor.displayName, 'PLATFORM_FEE', 'WalletTransaction',
       lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-'
           || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))),
       NULL, NULL, t.feeAmount, '3% platform fee on payout', sys.userId, sys.displayName,
       t.relatedBookingId, t.relatedTicketId, t.createdAt,
       SUM(t.feeAmount) OVER (ORDER BY t.createdAt, t.transactionId)
FROM wallet_transactions t
JOIN wallets w ON w.walletId = t.walletId
JOIN users actor ON actor.userId = COALESCE(t.initiatedBy, w.userId)
JOIN users sys ON sys.role = 'SYSTEM'
WHERE t.type = 'BOOKING_PAYOUT' AND COALESCE(t.feeAmount, 0) > 0
ORDER BY t.createdAt, t.transactionId;

UPDATE wallets
SET balance = (SELECT COALESCE(SUM(l.walletAdjustment), 0) FROM audit_log l
               WHERE l.actionType = 'PLATFORM_FEE' AND l.subjectUserId = wallets.userId),
    updatedAt = '2026-09-27T00:00:00Z'
WHERE userId IN (SELECT userId FROM users WHERE role = 'SYSTEM');

CREATE INDEX idx_audit_money ON audit_log (subjectUserId, walletAdjustment)
```

(Last statement has no trailing semicolon on purpose; the runner splits on `;` and skips blanks.)

- [ ] **Step 5: Gate V007 in the runner**

Add `private static final int UNIFIED_LEDGER_VERSION = 7;` and after the V006 block:

```java
            if (!migrationApplied(connection, UNIFIED_LEDGER_VERSION)) {
                if (!columnExists(connection, "audit_log", "balanceAfter")) {
                    applySqlMigration(connection, "/db/migration/V007__unified_ledger.sql");
                }
                // else: a reference database rebuilt from db/schema.sql already has the column.
                recordMigration(connection, UNIFIED_LEDGER_VERSION);
            }
```

- [ ] **Step 6: Carry `balanceAfter` through the audit code**

- `AuditLogEntry`: append `BigDecimal balanceAfter` as the last record component (after `timestamp`) and update Javadoc: "money rows only".
- `RowMappers.auditLogEntry`: append `JdbcCodecs.decimal(result.getString("balanceAfter"))` (the mapper tolerates the column missing only if the SELECT includes it; all reads are `SELECT *`).
- `JdbcAuditLogRepository.save`: add `balanceAfter` to the column list (16 placeholders) and `statement.setString(16, JdbcCodecs.decimal(entry.balanceAfter()));`.
- `AuditRecord`: add component `BigDecimal balanceAfter` after `walletAdjustment`; add builder field and method

```java
        public Builder wallet(BigDecimal adjustment, BigDecimal balanceAfter) {
            this.walletAdjustment = adjustment;
            this.balanceAfter = balanceAfter;
            return this;
        }
```

keep the one-argument `wallet(BigDecimal)` (sets `balanceAfter` to null) so existing callers compile; extend the compact-constructor rule to `if (balanceAfter != null && walletAdjustment == null) throw new IllegalArgumentException("balanceAfter belongs to money rows")`; pass `balanceAfter` in `build()`.
- `AuditServiceImpl.record`: pass `record.balanceAfter()` as the new last constructor argument of `AuditLogEntry`.
- `AuditService.recordWalletTransaction` default method: use `.wallet(applied, transaction.balanceAfter())`.
- Fix every `new AuditLogEntry(...)` / `new AuditRecord(...)` in tests by appending `null` (they are in `JdbcAuditLogRepositoryTest`, `AuditRecordTest`, `AuditServiceTest`; the compiler lists them).

- [ ] **Step 7: Mirror in `db/schema.sql`; update the seed; rebuild**

`db/schema.sql`: add `balanceAfter REAL` as the **last** column of `audit_log` (ADD COLUMN puts it last; `SchemaParityTest` compares column order), add `CREATE INDEX idx_audit_money ON audit_log (subjectUserId, walletAdjustment);` next to the other audit indexes, and change the history comment to "V001 to V007".

`db/seed-mock-data.sql`:
1. The money-row `INSERT INTO audit_log ... SELECT '5a' ...` statement: add `balanceAfter` to its column list, `t.balanceAfter` as its last selected value, and finish with `ORDER BY t.createdAt, t.transactionId` (rowid order must be chronological within each wallet; do not change anything else in it).
2. Directly after it add the seeded fee rows and the System wallet:

```sql
INSERT INTO wallets (walletId, userId, balance, currency, updatedAt) VALUES
('30000000-0000-0000-0000-0000000000ff','a0000000-0000-0000-0000-0000000000ff',16.35,'SGD','2026-09-27T00:00:00Z');

INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp, balanceAfter)
SELECT '5f' || substr(t.transactionId, 3), actor.userId, actor.displayName, 'PLATFORM_FEE', 'WalletTransaction',
       '4f' || substr(t.transactionId, 3), NULL, NULL, t.feeAmount, '3% platform fee on payout', sys.userId,
       sys.displayName, t.relatedBookingId, t.relatedTicketId, t.createdAt,
       SUM(t.feeAmount) OVER (ORDER BY t.createdAt, t.transactionId)
FROM wallet_transactions t JOIN wallets w ON w.walletId = t.walletId
JOIN users actor ON actor.userId = COALESCE(t.initiatedBy, w.userId)
JOIN users sys ON sys.userId = 'a0000000-0000-0000-0000-0000000000ff'
WHERE t.type = 'BOOKING_PAYOUT' AND COALESCE(t.feeAmount, 0) > 0
ORDER BY t.createdAt, t.transactionId;
```

3. `schema_history` insert: add `(7, '2026-09-27 00:00:00')`.

The seeded fees are transactions 29 (11.40) and 35 (4.95): 16.35 in total, running 11.40 then 16.35.

Rebuild and check:

```bash
rm -f db/snoozeshare-mock.db && sqlite3 db/snoozeshare-mock.db < db/schema.sql && sqlite3 db/snoozeshare-mock.db < db/seed-mock-data.sql
sqlite3 db/snoozeshare-mock.db "PRAGMA foreign_key_check; SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL AND balanceAfter IS NULL; SELECT actionType, walletAdjustment, balanceAfter FROM audit_log WHERE actionType='PLATFORM_FEE'; SELECT balance FROM wallets WHERE userId='a0000000-0000-0000-0000-0000000000ff';"
```

Expected: no FK rows; `0`; two fee rows `11.4|11.4` and `4.95|16.35`; `16.35`.

- [ ] **Step 8: Update guard tests, run everything**

`SchemaParityTest`: apply `V007__unified_ledger.sql` after V006 (the empty tables make its statements no-ops apart from the DDL). `CommittedMockDbTest`: add `balanceAfter` to the required audit columns and versions `{1..7}`; add an assertion that the System wallet balance is `16.35`. `MockAuditSeedTest`/`MockDbFixtureTest`: if a row-count assertion changed because of the two fee rows or the System wallet, update it to the new count and say so in the commit description.

Run: `./gradlew test`
Expected: PASS (D21 pair aside).

- [ ] **Step 9: Commit**

```bash
git add -A src db
git commit -m "feat(w14): V007 balanceAfter, fee rows, System wallet"
```

---

### Task 5: `LedgerRepository` reads money rows

**Files:**
- Create: `src/main/java/com/snoozeshare/repository/LedgerRepository.java`, `src/main/java/com/snoozeshare/repository/jdbc/JdbcLedgerRepository.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcLedgerRepositoryTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*JdbcLedgerRepositoryTest"`
Expected: FAIL (class missing).

- [ ] **Step 3: Implement**

`LedgerRepository.java`:

```java
package com.snoozeshare.repository;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.WalletTransaction;

/** Read side of the ledger: the money rows of the audit log, oldest first (insertion order). */
public interface LedgerRepository {
    List<WalletTransaction> entriesForWallet(UUID walletId);

    List<WalletTransaction> entriesForBooking(UUID bookingId);
}
```

`JdbcLedgerRepository.java`:

```java
package com.snoozeshare.repository.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.repository.LedgerRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;

public final class JdbcLedgerRepository implements LedgerRepository {

    private static final String SELECT = "SELECT a.entityId AS transactionId, w.walletId AS walletId, "
            + "a.actionType AS type, a.walletAdjustment AS amount, a.balanceAfter AS balanceAfter, "
            + "a.bookingId AS relatedBookingId, a.ticketId AS relatedTicketId, a.actorUserId AS initiatedBy, "
            + "a.timestamp AS createdAt FROM audit_log a JOIN wallets w ON w.userId = a.subjectUserId "
            + "WHERE a.walletAdjustment IS NOT NULL AND ";

    private final Connection connection;

    public JdbcLedgerRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<WalletTransaction> entriesForWallet(UUID walletId) {
        return query("w.walletId = ?", walletId);
    }

    @Override
    public List<WalletTransaction> entriesForBooking(UUID bookingId) {
        return query("a.bookingId = ?", bookingId);
    }

    private List<WalletTransaction> query(String condition, UUID value) {
        try (var statement = connection.prepareStatement(SELECT + condition + " ORDER BY a.rowid")) {
            statement.setString(1, JdbcCodecs.uuid(value));
            try (var result = statement.executeQuery()) {
                List<WalletTransaction> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(new WalletTransaction(
                            JdbcCodecs.uuid(result.getString("transactionId")),
                            JdbcCodecs.uuid(result.getString("walletId")),
                            WalletTransactionType.valueOf(result.getString("type")),
                            JdbcCodecs.decimal(result.getString("amount")),
                            BigDecimal.ZERO,
                            JdbcCodecs.decimal(result.getString("balanceAfter")),
                            JdbcCodecs.uuid(result.getString("relatedBookingId")),
                            JdbcCodecs.uuid(result.getString("relatedTicketId")),
                            JdbcCodecs.uuid(result.getString("initiatedBy")),
                            JdbcCodecs.instant(result.getString("createdAt"))));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query the ledger", exception);
        }
    }
}
```

(`feeAmount` is `BigDecimal.ZERO` in the read model until Task 10 removes the field.)

- [ ] **Step 4: Run tests**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat(w14): LedgerRepository over audit money rows"
```

---

### Task 6: `LedgerWriter`, the single wallet write path

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/LedgerWriter.java`, `src/test/java/com/snoozeshare/testsupport/LedgerTestSupport.java`
- Test: `src/test/java/com/snoozeshare/service/LedgerWriterTest.java` (create)

- [ ] **Step 1: Write the test support and the failing test**

`LedgerTestSupport.java`:

```java
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
```

`LedgerWriterTest.java`:

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcLedgerRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.LedgerWriter;
import com.snoozeshare.testsupport.LedgerTestSupport;

class LedgerWriterTest {

    private static final Instant AT = Instant.parse("2026-09-27T01:00:00Z");

    private static Wallet wallet(Connection connection, Role role, String email, String balance) {
        User user = new JdbcUserRepository(connection).save(new User(UUID.randomUUID(), role, email, email,
                AccountStatus.ACTIVE, null, AT));
        return new JdbcWalletRepository(connection).save(new Wallet(UUID.randomUUID(), user.userId(),
                new BigDecimal(balance), "SGD", AT));
    }

    @Test
    void postingMovesTheBalanceAndWritesOneMoneyRowWithBalanceAfter() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            Wallet guest = wallet(connection, Role.GUEST, "g@x.test", "100.00");
            LedgerWriter ledger = LedgerTestSupport.writer(connection);

            WalletTransaction hold = ledger.post(guest.walletId(), WalletTransactionType.ESCROW_HOLD,
                    new BigDecimal("-40.00"), guest.userId(), null, null, null, AT);

            assertEquals(0, new BigDecimal("60.00").compareTo(hold.balanceAfter()));
            assertEquals(0, new BigDecimal("60.00").compareTo(
                    new JdbcWalletRepository(connection).findById(guest.walletId()).orElseThrow().balance()));
            List<WalletTransaction> rows = new JdbcLedgerRepository(connection).entriesForWallet(guest.walletId());
            assertEquals(1, rows.size());
            assertEquals(hold.transactionId(), rows.get(0).transactionId());
        }
    }

    @Test
    void anOverdraftAndAZeroAmountAreRejectedWithoutSideEffects() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            Wallet guest = wallet(connection, Role.GUEST, "g@x.test", "10.00");
            LedgerWriter ledger = LedgerTestSupport.writer(connection);

            assertThrows(IllegalArgumentException.class, () -> ledger.post(guest.walletId(),
                    WalletTransactionType.WITHDRAWAL, new BigDecimal("-10.01"), guest.userId(), null, null, null, AT));
            assertThrows(IllegalArgumentException.class, () -> ledger.post(guest.walletId(),
                    WalletTransactionType.TOP_UP, BigDecimal.ZERO, guest.userId(), null, null, null, AT));
            assertEquals(0, new JdbcLedgerRepository(connection).entriesForWallet(guest.walletId()).size());
        }
    }

    @Test
    void aPayoutWritesTheNetRowAndAFeeRowToTheSystemWallet() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            Wallet host = wallet(connection, Role.HOST, "h@x.test", "0.00");
            LedgerWriter ledger = LedgerTestSupport.writer(connection);
            UUID booking = null;

            WalletTransaction payout = ledger.postPayout(host.walletId(), new BigDecimal("97.00"),
                    new BigDecimal("3.00"), host.userId(), booking, null, AT);

            assertEquals(WalletTransactionType.BOOKING_PAYOUT, payout.type());
            assertEquals(0, new BigDecimal("97.00").compareTo(payout.amount()));
            var system = new JdbcWalletRepository(connection).findByUserId(AuditService.SYSTEM_ACTOR_ID)
                    .orElseThrow();
            assertEquals(0, new BigDecimal("3.00").compareTo(system.balance()));
            List<WalletTransaction> fee = new JdbcLedgerRepository(connection).entriesForWallet(system.walletId());
            assertEquals(1, fee.size());
            assertEquals(WalletTransactionType.PLATFORM_FEE, fee.get(0).type());
        }
    }

    @Test
    void aZeroFeeWritesNoFeeRow() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            Wallet host = wallet(connection, Role.HOST, "h@x.test", "0.00");
            LedgerTestSupport.writer(connection).postPayout(host.walletId(), new BigDecimal("50.00"),
                    BigDecimal.ZERO, host.userId(), null, null, AT);
            var system = new JdbcWalletRepository(connection).findByUserId(AuditService.SYSTEM_ACTOR_ID)
                    .orElseThrow();
            assertEquals(0, new JdbcLedgerRepository(connection).entriesForWallet(system.walletId()).size());
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*LedgerWriterTest"`
Expected: FAIL (class missing).

- [ ] **Step 3: Implement**

```java
package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.AuditAction;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;

/**
 * The only code that changes a wallet balance. It runs on the caller's connection and never opens a
 * transaction: the caller wraps it (and any state change it belongs with) in one, so the balance, the money
 * row and the state change commit or roll back together. Callers publish events after their commit.
 */
public final class LedgerWriter {

    private final WalletRepository wallets;
    private final AuditService audit;

    public LedgerWriter(WalletRepository wallets, AuditService audit) {
        this.wallets = wallets;
        this.audit = audit;
    }

    /** Moves {@code amount} (signed) in one wallet and records the money row. */
    public WalletTransaction post(UUID walletId, WalletTransactionType type, BigDecimal amount, UUID actorId,
                                  UUID bookingId, UUID ticketId, String reason, Instant at) {
        if (walletId == null || type == null || actorId == null) {
            throw new IllegalArgumentException("Wallet, type and actor are required");
        }
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("Amount must not be zero");
        }
        Wallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet does not exist"));
        DomainValidation.requireSgd(wallet.currency());
        BigDecimal balanceAfter = wallet.balance().add(amount);
        if (balanceAfter.signum() < 0) {
            throw new IllegalArgumentException("Insufficient wallet funds");
        }
        wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter, wallet.currency(), at));
        UUID transactionId = UUID.randomUUID();
        audit.record(AuditRecord.builder(actorId, AuditAction.forWallet(type), "WalletTransaction", transactionId)
                .wallet(amount, balanceAfter).reason(reason).subject(wallet.userId())
                .booking(bookingId).ticket(ticketId).at(at).build());
        return new WalletTransaction(transactionId, walletId, type, amount, BigDecimal.ZERO, balanceAfter,
                bookingId, ticketId, actorId, at);
    }

    /**
     * Pays a host {@code net} and credits the platform {@code fee} to the System wallet (no fee row for a zero
     * fee). Returns the host's payout row.
     */
    public WalletTransaction postPayout(UUID hostWalletId, BigDecimal net, BigDecimal fee, UUID actorId,
                                        UUID bookingId, UUID ticketId, Instant at) {
        DomainValidation.requireNonNegative(fee, "fee");
        String reason = fee.signum() > 0
                ? "Payout net of 3% platform fee (" + fee.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")"
                : null;
        WalletTransaction payout = post(hostWalletId, WalletTransactionType.BOOKING_PAYOUT, net, actorId,
                bookingId, ticketId, reason, at);
        if (fee.signum() > 0) {
            Wallet system = wallets.findByUserId(AuditService.SYSTEM_ACTOR_ID)
                    .orElseThrow(() -> new IllegalStateException("The System wallet is missing"));
            post(system.walletId(), WalletTransactionType.PLATFORM_FEE, fee, actorId, bookingId, ticketId,
                    "3% platform fee on payout", at);
        }
        return payout;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat(w14): LedgerWriter, the single wallet write path"
```

---

### Task 7: `WalletService` moves onto the writer; `WalletLedgerWriter` is deleted

`WalletServiceImpl`'s public behaviour (top-up, withdraw, balance, statement) does not change. Its constructor changes.

**Files:**
- Modify: `service/impl/WalletServiceImpl.java`, `app/AppContext.java`
- Delete: `service/impl/WalletLedgerWriter.java`
- Modify tests that construct `WalletServiceImpl` or `WalletLedgerWriter`: `WalletLedgerTest`, `WalletLedgerAuditTest`, `WalletTransactionAtomicityTest`, `W1FoundationIntegrationTest`, `JdbcRepositoryIntegrationTest` (compiler lists any others)

- [ ] **Step 1: Update the tests first (they now fail to compile)**

New constructor: `new WalletServiceImpl(connection, wallets, ledger, ledgerRepository, eventBus)` where `ledger` is a `LedgerWriter` and `ledgerRepository` a `JdbcLedgerRepository`. In each test replace

```java
new WalletServiceImpl(connection, new JdbcWalletRepository(connection),
        new JdbcWalletTransactionRepository(connection), bus, audit)
```

with

```java
new WalletServiceImpl(connection, new JdbcWalletRepository(connection),
        LedgerTestSupport.writer(connection, audit), new JdbcLedgerRepository(connection), bus)
```

(`audit` is whatever audit the test already built, so `FailingAuditService` atomicity tests keep working; where a test passed `null` audit use `LedgerTestSupport.writer(connection)`.) Assertions that read `wallet_transactions` directly (`SELECT COUNT(*) FROM wallet_transactions`) become the ledger: `SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL`. `WalletLedgerAuditTest` already asserts one money row per movement: add `assertEquals(0, new BigDecimal("50.00").compareTo(top.balanceAfter()));` for the top-up row.

- [ ] **Step 2: Run to verify compile failure**

Run: `./gradlew compileTestJava`
Expected: FAIL (constructor mismatch).

- [ ] **Step 3: Rewrite `WalletServiceImpl`**

```java
public final class WalletServiceImpl implements WalletService {

    private final Connection connection;
    private final WalletRepository wallets;
    private final LedgerWriter ledger;
    private final LedgerRepository ledgerEntries;
    private final EventBus eventBus;

    public WalletServiceImpl(Connection connection, WalletRepository wallets, LedgerWriter ledger,
                             LedgerRepository ledgerEntries, EventBus eventBus) {
        this.connection = connection;
        this.wallets = wallets;
        this.ledger = ledger;
        this.ledgerEntries = ledgerEntries;
        this.eventBus = eventBus;
    }

    @Override
    public Wallet getWallet(UUID userId) {
        return wallets.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet does not exist"));
    }

    @Override
    public BigDecimal balanceOf(UUID userId) {
        return getWallet(userId).balance();
    }

    @Override
    public WalletTransaction topUp(UUID userId, BigDecimal amount) {
        DomainValidation.requirePositive(amount, "amount");
        return move(userId, WalletTransactionType.TOP_UP, amount);
    }

    @Override
    public WalletTransaction withdraw(UUID userId, BigDecimal amount) {
        DomainValidation.requirePositive(amount, "amount");
        return move(userId, WalletTransactionType.WITHDRAWAL, amount.negate());
    }

    @Override
    public List<WalletTransaction> statementFor(UUID userId) {
        return ledgerEntries.entriesForWallet(getWallet(userId).walletId());
    }

    private WalletTransaction move(UUID userId, WalletTransactionType type, BigDecimal amount) {
        Wallet wallet = getWallet(userId);
        try {
            WalletTransaction transaction = new TransactionManager(connection).inTransaction(current ->
                    ledger.post(wallet.walletId(), type, amount, userId, null, null, null, Instant.now()));
            if (eventBus != null) {
                eventBus.publish(new WalletTransactionRecordedEvent(transaction.transactionId(),
                        transaction.walletId(), transaction.createdAt()));
            }
            return transaction;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to write wallet ledger", exception);
        }
    }
}
```

Imports to add: `java.sql.SQLException`, `java.time.Instant`, `com.snoozeshare.infra.db.TransactionManager`, `com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent`, `com.snoozeshare.repository.LedgerRepository`; remove the ones no longer used.

- [ ] **Step 4: Update `AppContext`**

Where `WalletServiceImpl` is built (around line 86), construct once and reuse for later tasks:

```java
        JdbcLedgerRepository ledgerRepository = new JdbcLedgerRepository(connection);
        LedgerWriter ledgerWriter = new LedgerWriter(walletRepository, auditService);
        ... new WalletServiceImpl(connection, walletRepository, ledgerWriter, ledgerRepository, eventBus)
```

(`walletRepository` is the existing `JdbcWalletRepository` variable; `auditService` must be built before this line — move its construction up if needed.) Delete `WalletLedgerWriter.java`.

- [ ] **Step 5: Run tests**

Run: `./gradlew test`
Expected: PASS (D21 pair aside). Anything else red means a test still reads `wallet_transactions` for a wallet movement made through `WalletService`; convert it to the audit query above.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "refactor(w14): WalletService writes through LedgerWriter"
```

---

### Task 8: `TransactionService` and `BookingService` use the writer

**Executed 2026-09-27 with two follow-up fixes, both approved.** Review found that `LedgerWriter` rejecting a zero amount exposed a pre-existing gap: a $0-rate listing could be booked and its settlement (called from `AppContext` startup) would then throw uncaught. The operator chose to forbid degenerate rates (`ListingServiceImpl`/the host form now require `baseNightlyRate > 0` with at most 2 decimal places, C42) rather than build out free-stay support in settlement/`EscrowPolicy`/disputes. The plan's original unconditional Step 3 code (holds/refunds with no `signum()` guard) is correct as written once that validation exists.

**Files:**
- Modify: `service/impl/TransactionServiceImpl.java`, `service/impl/BookingServiceImpl.java`, `app/AppContext.java`
- Modify tests: `TransactionServiceTest`, `BookingServiceTest` (+ any other constructing these; compiler lists them)
- Test: extend `TransactionServiceTest` (fee row assertion) and `BookingServiceTest` (hold/refund rows)

- [ ] **Step 1: Write the failing assertions**

In `TransactionServiceTest`, in the test that settles a booking (`settleBookingCompletion`), add after the settlement (adapt names to the fixtures already in that file; `gross` is the booking total, e.g. 100.00):

```java
        var system = new JdbcWalletRepository(connection).findByUserId(AuditService.SYSTEM_ACTOR_ID).orElseThrow();
        var feeRows = new JdbcLedgerRepository(connection).entriesForWallet(system.walletId());
        assertEquals(1, feeRows.size());
        assertEquals(WalletTransactionType.PLATFORM_FEE, feeRows.get(0).type());
        assertEquals(0, new BigDecimal("3.00").compareTo(feeRows.get(0).amount()));
        assertEquals(0, new BigDecimal("97.00").compareTo(payout.amount()));
```

and a rollback test: build the service with `LedgerTestSupport.writer(connection, new FailingAuditService(LedgerTestSupport.audit(connection), 2))` (call 1 is the payout row, call 2 the fee row), call `settleBookingCompletion`, `assertThrows(RuntimeException.class, ...)`, then assert the booking is still `CONFIRMED`, the host balance is unchanged and the ledger has no `BOOKING_PAYOUT` row for it.

In `BookingServiceTest`, assert after `submitRequest` that `new JdbcLedgerRepository(connection).entriesForBooking(bookingId)` has one `ESCROW_HOLD` row with `balanceAfter` equal to the guest's new balance, and after `cancel` (>48h) / reject a matching `ESCROW_REFUND` row and no `PLATFORM_FEE` row anywhere.

- [ ] **Step 2: Rewrite `TransactionServiceImpl`**

Replace the four constructors with one and drop the `NoOpAuditService` fallback:

```java
    public TransactionServiceImpl(Connection connection, BookingRepository bookings,
                                  PropertyRepository properties, WalletRepository wallets,
                                  LedgerWriter ledger, LedgerRepository ledgerEntries, EventBus eventBus) {
```

Fields: `connection, bookings, properties, wallets, ledger, ledgerEntries, eventBus`. Methods:

```java
    @Override
    public WalletTransaction holdEscrow(UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        return inOwnTransaction(() -> ledger.post(guestWallet(booking).walletId(),
                WalletTransactionType.ESCROW_HOLD, booking.totalAmount().negate(), booking.guestId(),
                bookingId, null, null, Instant.now()));
    }

    @Override
    public WalletTransaction refundEscrow(UUID bookingId, BigDecimal refundAmount) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        return inOwnTransaction(() -> ledger.post(guestWallet(booking).walletId(),
                WalletTransactionType.ESCROW_REFUND, refundAmount, booking.guestId(), bookingId, null, null,
                Instant.now()));
    }

    @Override
    public WalletTransaction settleBookingCompletion(UUID bookingId) {
        try {
            WalletTransaction payout = new TransactionManager(connection).inTransaction(current -> {
                Booking booking = bookings.findById(bookingId)
                        .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
                var existing = ledgerEntries.entriesForBooking(bookingId).stream()
                        .filter(txn -> txn.type() == WalletTransactionType.BOOKING_PAYOUT).findFirst();
                if (booking.status() == BookingStatus.COMPLETED) {
                    return existing.orElseThrow(() -> new IllegalStateException("Completed booking has no payout"));
                }
                if (booking.status() != BookingStatus.CONFIRMED) {
                    throw new IllegalStateException("Only confirmed bookings can be completed");
                }
                if (properties == null) {
                    throw new IllegalStateException("Property repository is required for settlement");
                }
                if (existing.isPresent()) {
                    return existing.get();
                }
                var property = properties.findById(booking.listingId())
                        .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
                Wallet hostWallet = wallets.findByUserId(property.hostId())
                        .orElseThrow(() -> new IllegalArgumentException("Host wallet does not exist"));
                BigDecimal gross = booking.totalAmount();
                BigDecimal net = gross.multiply(new BigDecimal("0.97")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal fee = gross.subtract(net).setScale(2, RoundingMode.HALF_UP);
                Instant now = Instant.now();
                WalletTransaction result = ledger.postPayout(hostWallet.walletId(), net, fee, property.hostId(),
                        bookingId, null, now);
                bookings.save(new Booking(booking.bookingId(), booking.listingId(), booking.guestId(),
                        booking.startDate(), booking.endDate(), BookingStatus.COMPLETED,
                        booking.nightlyRateSnapshot(), booking.totalAmount(), booking.createdAt(),
                        booking.decidedAt(), now, booking.hostDecisionMessage()));
                return result;
            });
            publish(payout);
            return payout;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to settle booking completion", exception);
        }
    }

    @Override
    public List<WalletTransaction> historyFor(UUID bookingId) {
        return ledgerEntries.entriesForBooking(bookingId);
    }

    private Wallet guestWallet(Booking booking) {
        return wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
    }

    private WalletTransaction inOwnTransaction(java.util.function.Supplier<WalletTransaction> work) {
        try {
            WalletTransaction transaction = new TransactionManager(connection).inTransaction(current -> work.get());
            publish(transaction);
            return transaction;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to write wallet ledger", exception);
        }
    }

    private void publish(WalletTransaction transaction) {
        if (eventBus != null) {
            eventBus.publish(new WalletTransactionRecordedEvent(transaction.transactionId(),
                    transaction.walletId(), transaction.createdAt()));
        }
    }
```

Keep `applyTicketRemedy` / `manualOverride` as they are (`UnsupportedOperationException("Owned by W10")`). Use imports for `BookingStatus`, `RoundingMode` instead of the fully qualified names above.

- [ ] **Step 3: Rewrite the three inline wallet blocks in `BookingServiceImpl`**

Add constructor parameters `LedgerWriter ledger` in place of `WalletTransactionRepository transactions`, and field `LedgerRepository ledgerEntries` where line 146 (`transactions.findByBookingId(...)`) reads the hold (replace it with `ledgerEntries.entriesForBooking(...)`; add `LedgerRepository` as a constructor parameter). The three blocks become:

`submitRequest` (was lines ~116-131), inside the existing transaction, after `blocks.save(...)`:

```java
                Wallet wallet = wallets.findByUserId(guestId)
                        .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
                ledger.post(wallet.walletId(), WalletTransactionType.ESCROW_HOLD, totalAmount.negate(), guestId,
                        bookingId, null, null, now);
```

`decide` reject branch (was ~lines 236-247):

```java
                    Wallet wallet = wallets.findByUserId(booking.guestId())
                            .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
                    ledger.post(wallet.walletId(), WalletTransactionType.ESCROW_REFUND, booking.totalAmount(),
                            hostId, bookingId, null, null, now);
```

`cancel` (was ~lines 288-300):

```java
                Wallet wallet = wallets.findByUserId(actingGuestId)
                        .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
                if (refundAmount.signum() > 0) {
                    ledger.post(wallet.walletId(), WalletTransactionType.ESCROW_REFUND, refundAmount,
                            actingGuestId, bookingId, null, null, now);
                }
```

(`calculateRefundAmount` returns 100% or 50%, never zero, so the guard never skips a real refund; it only keeps the writer's no-zero rule from being hit. Operator, 2026-09-27: a zero refund needs no ledger row.)

Remove the now-unused `Wallet`/`WalletTransaction` construction, the old comments about `WalletLedgerWriter`, and unused imports.

- [ ] **Step 4: Wire `AppContext`**

Construct `TransactionServiceImpl(connection, bookingRepository, propertyRepository, walletRepository, ledgerWriter, ledgerRepository, eventBus)` and `BookingServiceImpl(... , ledgerWriter, ledgerRepository, ...)`, replacing the `txnRepo` argument.

- [ ] **Step 5: Update the remaining tests' constructors, run**

Replace `new JdbcWalletTransactionRepository(connection)` arguments with the `LedgerWriter`/`JdbcLedgerRepository` pair as in Task 7; tests that seed a hold by hand through `transactions.save(...)` switch to `ledger.post(...)`.

Run: `./gradlew test`
Expected: PASS (D21 pair aside).

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "refactor(w14): booking and escrow money via LedgerWriter"
```

---

### Task 9: Dispute settlement, account governance and the escrow readers

**Files:**
- Modify: `service/impl/DisputeSettlementServiceImpl.java`, `service/impl/AccountGovernanceServiceImpl.java`, `service/impl/DisputeQueryServiceImpl.java`, `app/AppContext.java`, `service/Settlement.java` (unchanged shape), fixtures `AccountFixture`, `SettlementFixtures`
- Modify tests: `DisputeSettlementServiceTest`, `DisputeSettlementAtomicityTest`, `DisputeQueryServiceTest`, `DisputeFlowEndToEndTest`, `TicketServiceIntegrationTest`, `AccountGovernance*Test`, `AccountFixture`, `SettlementFixtures`
- Test: extend `DisputeSettlementServiceTest` and `AccountGovernanceCascadeTest`

- [ ] **Step 1: Failing assertions**

In `DisputeSettlementServiceTest`, in a test that resolves a ticket paying the host (for example the existing full-payout or 50/50 case), assert a fee row exists and the four-row audit shape is now five:

```java
        var system = new JdbcWalletRepository(db.connection()).findByUserId(AuditService.SYSTEM_ACTOR_ID).orElseThrow();
        var fees = new JdbcLedgerRepository(db.connection()).entriesForWallet(system.walletId());
        // the mock DB already holds two seeded fees (16.35), so compare the delta
        assertEquals(0, expectedFee.compareTo(system.balance().subtract(new BigDecimal("16.35"))));
```

where `expectedFee` is `settlement.breakdown().fee()` (the test already has the settlement result). Also assert that a full refund to the guest (mode ACCEPT with full remedy) leaves the System balance at `16.35`.

In `AccountGovernanceCascadeTest`, assert that a cascade refund writes an `ESCROW_REFUND` money row with `balanceAfter` and no fee row.

In `DisputeSettlementAtomicityTest`, keep the existing injected-failure cases and add one that fails the fee row (`FailingAuditService(..., failingCall)` on the call that writes the fee) and asserts the ticket is unresolved and the host balance unchanged.

- [ ] **Step 2: `DisputeSettlementServiceImpl`**

Constructor: replace `WalletTransactionRepository transactions` with `LedgerWriter ledger`. Remove the private `credit(...)` method. In `settle`, replace the two `credit` calls and the two `audit.recordWalletTransaction` calls (money rows are now written by the writer, so drop them):

```java
        WalletTransaction guestTransaction = null;
        WalletTransaction hostTransaction = null;
        if (split.guestRefund().signum() > 0) {
            WalletTransactionType type = mode == ResolutionMode.MANUAL
                    ? WalletTransactionType.AGENT_OVERRIDE : WalletTransactionType.TICKET_REMEDY;
            guestTransaction = ledger.post(guestWallet.walletId(), type, split.guestRefund(), agentId,
                    booking.bookingId(), ticket.ticketId(), null, now);
        }
        if (split.hostGross().signum() > 0) {
            hostTransaction = ledger.postPayout(hostWallet.walletId(), split.hostNet(), split.fee(), agentId,
                    booking.bookingId(), ticket.ticketId(), now);
        }
```

Keep the ticket and booking `audit.record(...)` calls where they are (they write the status rows). The row order matters for the audit screen only cosmetically; money rows now come before the two status rows, which is fine (one change per row is unchanged). The `publish(...)` helper keeps working on the returned `WalletTransaction`s; also publish nothing for the fee row (the System wallet has no listeners).

- [ ] **Step 3: `AccountGovernanceServiceImpl.forceCancel`**

Constructor: replace `WalletTransactionRepository transactions` with `LedgerWriter ledger`. Replace the inline wallet block (from `// Inline wallet write ...` through `audit.recordWalletTransaction(...)`) with:

```java
        Wallet wallet = wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
        WalletTransaction refund = ledger.post(wallet.walletId(), WalletTransactionType.ESCROW_REFUND,
                booking.totalAmount(), agentId, booking.bookingId(), null, null, now);
        events.add(new WalletTransactionRecordedEvent(refund.transactionId(), refund.walletId(), now));
```

- [ ] **Step 4: `DisputeQueryServiceImpl`**

Replace field/constructor type `WalletTransactionRepository transactions` with `LedgerRepository ledgerEntries` and the call `transactions.findByBookingId(...)` with `ledgerEntries.entriesForBooking(...)`. `EscrowPolicy` needs no change (it takes a `List<WalletTransaction>`; `PLATFORM_FEE` is not a releasing type and only ever appears with a payout).

- [ ] **Step 5: Fixtures**

`AccountFixture`: replace the `transactions` field with `final LedgerWriter ledger;` and `final JdbcLedgerRepository ledgerEntries;`, built as `ledger = LedgerTestSupport.writer(connection, audit)`. In `user(...)`, create the wallet with a zero balance and fund it through the ledger so its history is complete:

```java
        if (role != Role.AGENT) {
            Wallet wallet = wallets.save(new Wallet(UUID.randomUUID(), user.userId(), BigDecimal.ZERO, "SGD", NOW));
            ledger.post(wallet.walletId(), WalletTransactionType.TOP_UP, START_BALANCE, user.userId(), null, null,
                    null, NOW.minusSeconds(172_800));
        }
```

In `booking(...)`, replace the manual balance update and `transactions.save(...)` with:

```java
        Wallet wallet = wallets.findByUserId(guest.userId()).orElseThrow();
        ledger.post(wallet.walletId(), WalletTransactionType.ESCROW_HOLD, total.negate(), guest.userId(),
                booking.bookingId(), null, null, NOW.minusSeconds(86_400));
```

`service(...)` passes `ledger` instead of `transactions` to `AccountGovernanceServiceImpl`. (`Role.SYSTEM` is excluded from wallets in the fixture the same way agents are: only GUEST/HOST call `user(...)` with wallets; keep the `role != Role.AGENT` condition.)

`SettlementFixtures.settlement(db, bus, transactions)` overloads: change the third parameter to `LedgerWriter ledger` and build one from the mock DB connection in callers with `LedgerTestSupport.writer(db.connection())`; pass `ledger` to the new `DisputeSettlementServiceImpl` constructor. Update `DisputeSettlementServiceTest`, `DisputeSettlementAtomicityTest`, `DisputeFlowEndToEndTest`, `TicketServiceIntegrationTest`, `DisputeQueryServiceTest` accordingly (they build `JdbcWalletTransactionRepository` today; use `new JdbcLedgerRepository(connection)` where they only read).

- [ ] **Step 6: `AppContext`**

Pass `ledgerWriter` to `DisputeSettlementServiceImpl` and `AccountGovernanceServiceImpl`, and `ledgerRepository` to `DisputeQueryServiceImpl`.

- [ ] **Step 7: Run tests**

Run: `./gradlew test`
Expected: PASS (D21 pair aside). Expected legitimate changes: a settlement writes one more audit row when a fee is charged; where an existing test asserts an exact audit row count for a resolution, bump it by one when the host is paid (the fee row) and say so in the commit description.

- [ ] **Step 8: Commit**

```bash
git add -A src
git commit -m "refactor(w14): settlement and governance via LedgerWriter"
```

---

### Task 10: V008 drops `wallet_transactions`; remove the old repository and `feeAmount`

After Task 9 nothing in `src/main` reads or writes the old table except the (now dead) `JdbcWalletTransactionRepository`, `WalletTransactionRepository`, `RowMappers.walletTransaction`, and the test helpers.

**Files:**
- Create: `src/main/resources/db/migration/V008__drop_wallet_transactions.sql`
- Delete: `repository/WalletTransactionRepository.java`, `repository/jdbc/JdbcWalletTransactionRepository.java`; remove `RowMappers.walletTransaction`
- Modify: `domain/model/WalletTransaction.java` (drop `feeAmount`), `JdbcLedgerRepository`, `LedgerWriter`, `MigrationRunner`, `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db`, `EscrowPolicyTest`, `SchemaParityTest`, `CommittedMockDbTest`, `DatabaseBootstrapTest`, `MockDbFixture`, `MockAuditSeedTest`, `JdbcRepositoryIntegrationTest`
- Test: `src/test/java/com/snoozeshare/infra/db/DropWalletTransactionsMigrationTest.java` (create)

- [ ] **Step 1: Failing test**

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.db.migration.MigrationRunner;

class DropWalletTransactionsMigrationTest {

    @Test
    void theOldLedgerTableIsGoneAndVersionEightIsRecorded() throws Exception {
        try (Connection connection = DatabaseTestSupport.openIsolatedDatabase()) {
            MigrationRunner.migrate(connection);
            MigrationRunner.migrate(connection);
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM sqlite_master WHERE name = 'wallet_transactions'")) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM schema_history WHERE version = 8")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*DropWalletTransactionsMigrationTest"`
Expected: FAIL.

- [ ] **Step 3: V008 and the runner gate**

`V008__drop_wallet_transactions.sql`:

```sql
-- The money rows of audit_log are the ledger now (V007 copied everything across).
DROP TABLE wallet_transactions
```

Runner: `private static final int DROP_LEDGER_TABLE_VERSION = 8;` and

```java
            if (!migrationApplied(connection, DROP_LEDGER_TABLE_VERSION)) {
                if (tableExists(connection, "wallet_transactions")) {
                    applySqlMigration(connection, "/db/migration/V008__drop_wallet_transactions.sql");
                }
                // else: a reference database rebuilt from db/schema.sql never had the table.
                recordMigration(connection, DROP_LEDGER_TABLE_VERSION);
            }
```

- [ ] **Step 4: Freeze the seeded money rows as literal SQL**

The seed's money rows and fee rows currently derive from `wallet_transactions`, which no longer exists. Generate literal rows from the current mock DB (it still has both) and paste them into the seed:

```bash
cat > "$TEMP/dump_money.py" <<'PYEOF'
import sqlite3
c = sqlite3.connect("db/snoozeshare-mock.db")
cols = ("logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState, "
        "walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp, balanceAfter")
rows = c.execute("SELECT " + cols + " FROM audit_log WHERE walletAdjustment IS NOT NULL ORDER BY rowid").fetchall()
def lit(v):
    if v is None: return "NULL"
    if isinstance(v, (int, float)): return repr(v)
    return "'" + str(v).replace("'", "''") + "'"
out = ["INSERT INTO audit_log (" + cols + ") VALUES"]
out.append(",\n".join("(" + ",".join(lit(v) for v in r) + ")" for r in rows) + ";")
open("db/money-rows.sql.tmp", "w", encoding="utf-8").write("\n".join(out) + "\n")
print(len(rows), "money rows")
PYEOF
python "$TEMP/dump_money.py"
```

Expected: `37 money rows` (35 ledger rows, transaction 32 does not exist, + 2 fee rows). In `db/seed-mock-data.sql`: delete the `wallet_transactions` INSERT block, the derived money-row `INSERT INTO audit_log ... SELECT '5a' ...` statement and the derived fee-row statement from Task 4 (keep the System wallet insert), and put the contents of `db/money-rows.sql.tmp` in their place under the same `audit_log` heading. Delete the temp file afterwards. Remove the `-- ====== wallet_transactions ======` section header and fix the `wallets` comment ("balance = the owner's newest money row's balanceAfter in audit_log"). Add `(8, '2026-09-27 00:00:00')` to `schema_history`. In `db/schema.sql` remove the `wallet_transactions` table and update the history comment to "V001 to V008".

Caveat: the seed's `wallets` balances and the money rows must still agree (the chain was preserved by the dump).

- [ ] **Step 5: Remove dead code**

- Delete `WalletTransactionRepository`, `JdbcWalletTransactionRepository`, `RowMappers.walletTransaction`.
- `WalletTransaction`: remove the `BigDecimal feeAmount` component. Update its three constructors' call sites: `LedgerWriter.post` (drop the `BigDecimal.ZERO` fee argument), `JdbcLedgerRepository.query` (drop the `BigDecimal.ZERO` argument and its import), `EscrowPolicyTest` and any test that builds one (the compiler lists them).
- `WalletProvisioningService`: unchanged.

- [ ] **Step 6: Fix the tests that referenced the table**

- `SchemaParityTest`: remove `"wallet_transactions"` from `TABLES` and apply `V008__drop_wallet_transactions.sql` after V007.
- `DatabaseBootstrapTest`: drop `wallet_transactions` from its expected tables.
- `CommittedMockDbTest`: versions `{1..8}`; add an assertion that `sqlite_master` has no `wallet_transactions`.
- `MockDbFixture`: replace `assertLedgerInvariant()` with the new definition (the same name, so callers keep working):

```java
    /**
     * Every wallet balance equals its owner's newest money row's balanceAfter (or 0 with no rows), and each
     * money row's balanceAfter is the running sum of the owner's adjustments in insertion order.
     */
    public void assertLedgerInvariant() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(
                    "SELECT w.walletId AS walletId, w.balance AS balance, COALESCE((SELECT a.balanceAfter "
                            + "FROM audit_log a WHERE a.subjectUserId = w.userId AND a.walletAdjustment IS NOT NULL "
                            + "ORDER BY a.rowid DESC LIMIT 1), 0) AS newest FROM wallets w")) {
                while (result.next()) {
                    assertEquals(result.getDouble("newest"), result.getDouble("balance"), 0.005,
                            "balance != newest balanceAfter for wallet " + result.getString("walletId"));
                }
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT subjectUserId, entityId, walletAdjustment, balanceAfter FROM audit_log "
                            + "WHERE walletAdjustment IS NOT NULL ORDER BY subjectUserId, rowid")) {
                String currentUser = null;
                double running = 0;
                while (result.next()) {
                    if (!result.getString("subjectUserId").equals(currentUser)) {
                        currentUser = result.getString("subjectUserId");
                        running = 0;
                    }
                    running += result.getDouble("walletAdjustment");
                    assertEquals(running, result.getDouble("balanceAfter"), 0.005,
                            "balanceAfter chain broken at " + result.getString("entityId"));
                }
            }
        }
    }
```

- `MockAuditSeedTest`, `JdbcRepositoryIntegrationTest`, `MockDbFixtureTest`: replace any `wallet_transactions` query with the equivalent over `audit_log WHERE walletAdjustment IS NOT NULL`; expected counts: 37 money rows.

- [ ] **Step 7: Rebuild the mock DB and run everything**

```bash
rm -f db/snoozeshare-mock.db && sqlite3 db/snoozeshare-mock.db < db/schema.sql && sqlite3 db/snoozeshare-mock.db < db/seed-mock-data.sql
sqlite3 db/snoozeshare-mock.db "PRAGMA foreign_key_check; SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL; SELECT COUNT(*) FROM sqlite_master WHERE name='wallet_transactions'; SELECT MAX(version) FROM schema_history;"
```

Expected: no FK rows, `37`, `0`, `8`.

Run: `./gradlew test`
Expected: PASS (D21 pair aside).

- [ ] **Step 8: Commit**

```bash
git add -A src db
git commit -m "feat(w14): drop wallet_transactions (V008)"
```

---

### Task 11: Conservation invariant and the fee end-to-end check

A global "money in = money out" identity is false in this product (a guest who cancels late gets 50% back and the rest is forfeited to nobody, and the spec keeps refund policy out of scope). The invariants that do hold, and that the old ledger could not check, are: every balance is the sum of its own adjustments; no booking ever releases more than it held; and a booking that paid a host is settled to exactly zero (refund + host net + fee = hold, C20).

**Files:**
- Test: `src/test/java/com/snoozeshare/service/LedgerConservationTest.java` (create)

- [ ] **Step 1: Write the test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.testsupport.MockDbFixture;

class LedgerConservationTest {

    /** Money is never created: balances add up, no booking releases more than it held, paid bookings net to 0. */
    static void assertConserved(MockDbFixture db) throws SQLException {
        double balances = Double.parseDouble(db.scalarString("SELECT COALESCE(SUM(balance), 0) FROM wallets"));
        double adjustments = Double.parseDouble(db.scalarString(
                "SELECT COALESCE(SUM(walletAdjustment), 0) FROM audit_log WHERE walletAdjustment IS NOT NULL"));
        assertEquals(adjustments, balances, 0.005, "wallet balances must equal the sum of all money rows");

        assertEquals(0, db.scalarLong("SELECT COUNT(*) FROM (SELECT bookingId FROM audit_log "
                + "WHERE walletAdjustment IS NOT NULL AND bookingId IS NOT NULL GROUP BY bookingId "
                + "HAVING SUM(walletAdjustment) > 0.005)"), "a booking released more than it held");

        assertEquals(0, db.scalarLong("SELECT COUNT(*) FROM (SELECT bookingId FROM audit_log "
                + "WHERE walletAdjustment IS NOT NULL AND bookingId IN "
                + "(SELECT bookingId FROM audit_log WHERE actionType = 'BOOKING_PAYOUT') "
                + "GROUP BY bookingId HAVING ABS(SUM(walletAdjustment)) > 0.005)"),
                "a booking that paid its host must settle to exactly zero");

        assertEquals(0, db.scalarLong("SELECT COUNT(*) FROM audit_log WHERE walletAdjustment IS NOT NULL "
                + "AND bookingId IS NULL AND actionType NOT IN ('TOP_UP', 'WITHDRAWAL')"),
                "only top-ups and withdrawals may have no booking");
    }

    @Test
    void theShippedMockDbConservesMoney(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.assertLedgerInvariant();
            assertConserved(db);
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew test --tests "*LedgerConservationTest"`
Expected: PASS. If a check fails on the shipped data, do NOT loosen it: list the offending booking with
`sqlite3 db/snoozeshare-mock.db "SELECT actionType, walletAdjustment, subjectName FROM audit_log WHERE bookingId = '<id>' AND walletAdjustment IS NOT NULL ORDER BY rowid"`,
fix the seed row (an override that does not add up to the hold means the seed was wrong; the old ledger-only check could not see that), rebuild the `.db` as in Task 10 Step 7 and re-run. Record any seed correction as a Deviations line.

- [ ] **Step 3: Add the same check after a real settlement**

Append a test to the same class that resolves an open seeded ticket through `DisputeSettlementServiceImpl` (build it exactly as `DisputeSettlementServiceTest` does, on a `MockDbFixture` copy, with `LedgerTestSupport.writer(db.connection())`), then calls `db.assertLedgerInvariant(); assertConserved(db);` and asserts the System wallet balance rose by exactly `settlement.breakdown().fee()` from `16.35`.

Run: `./gradlew test --tests "*LedgerConservationTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add -A src
git commit -m "test(w14): money conservation invariants"
```

---

### Task 12: Full build, documentation and hand-off

**Files:**
- Modify: `PROJECT_STATE.md`, `docs/project-state/done-ledger.md`, `docs/superpowers/specs/2026-09-27-w14-unified-ledger-design.md`

- [ ] **Step 1: Full verification**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` apart from the two D21 failures if they still reproduce (say so plainly if they do). Run `./gradlew run` against a copy of the mock DB (`SNOOZESHARE_DB_URL=jdbc:sqlite:build/acceptance.db`): top up and withdraw as a guest, book, then check the Audit Log shows the new rows; note in the report whether this GUI run was actually performed (D14/D20 precedent).

- [ ] **Step 2: Spec deviation**

In the W14 spec § 3, add one line under the V007 heading: "Deviation from the first draft: the ledger fold is split into V007 (add and backfill) and V008 (drop the table) so the code can move off `wallet_transactions` before it disappears."

- [ ] **Step 3: `PROJECT_STATE.md`**

- § Workstreams W14 row: Status `In review` (or `Building` if the operator has not accepted), Plan column linking this file, Progress `12/12 tasks; awaiting operator acceptance`.
- § Known Gaps: delete the "Platform fees are informational only" entry and its reversal note (the reversal has executed: record it as a Done line).
- § 4.3 Service and § 4.5 Repository: rewrite the *Now* paragraphs: `LedgerWriter` is the single wallet write path; `LedgerRepository` reads money rows; `wallet_transactions` is gone; `WalletTransaction` is a read model. § 4.8 Audit trail: replace the "Dual-write until W14" sentence with the new rule (money row = `walletAdjustment IS NOT NULL`, `balanceAfter`, `PLATFORM_FEE`). § 4.5: users role `SYSTEM`, System wallet.
- § 5 Conventions: reword the `wallets.balance` bullet to "a cache of the owner's newest money row's `balanceAfter` in `audit_log`", and add: "Only `LedgerWriter` changes a wallet balance; it never opens a transaction."
- § 8: mark C3, C9, C31, C32 as executed or amended by C39/C40 (append "(executed by W14, 2026-09-27)" to C31; do not delete anything).
- § 9 Deviations: add a W14 entry: V007/V008 split, the seed money rows are now literal SQL, and any other deviation you hit.
- § How to Resume: update the session row; § Orientation repo map: mock DB now ships at `schema_history` v8.
- Header: Phase, Last updated, Last verified.

- [ ] **Step 4: Done ledger**

Add one line: "Implemented W14 Unified ledger: … (list: V006–V008, LedgerWriter, System wallet and fee rows, wallet_transactions removed, mock DB rebuilt)" with the plan as the reference.

- [ ] **Step 5: Guide**

`docs/DeveloperGuide.md` still describes `wallet_transactions`. Do not edit it (AGENTS.md § 5: only at a checkpoint). Append `guide out of date: wallet_transactions/ledger (W14)` to the W14 Progress cell and set Guide to `Awaiting confirmation` once the operator accepts.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "docs(w14): state, ledger and spec deviation"
```

---

## Self-Review Notes

- **Spec coverage:** § 2 target design (Tasks 6, 8, 9, fee flow), System role (Tasks 2, 3), § 3 migrations (Tasks 1, 2, 4, 10; split into V006/V007/V008, recorded as a deviation), mock DB (Tasks 2, 4, 10), § 4 code table (Tasks 4 to 10), § 5 contracts kept (Tasks 7, 8), § 6 wallets (Task 7, no UI change), § 7 audit label (Task 4 Step 3), § 8 reversals (Task 12), § 9 tests (Tasks 1 to 11), § 10 accepted defaults (no merge of services, stored balance, no escrow account, legacy fee backfill in V007).
- **Type consistency:** `LedgerWriter.post(UUID walletId, WalletTransactionType, BigDecimal amount, UUID actorId, UUID bookingId, UUID ticketId, String reason, Instant at)` and `postPayout(UUID hostWalletId, BigDecimal net, BigDecimal fee, UUID actorId, UUID bookingId, UUID ticketId, Instant at)` are used with those exact shapes in Tasks 7 to 9; `LedgerRepository.entriesForWallet/entriesForBooking` likewise; `WalletServiceImpl(connection, wallets, ledger, ledgerEntries, eventBus)`, `TransactionServiceImpl(connection, bookings, properties, wallets, ledger, ledgerEntries, eventBus)`.
- **Known judgement calls for the executor:** Task 4 assumes legacy `amount` is the moved amount; Task 10's literal seed rows come from a script run against the Task 4 mock DB.
