# W10 Agent Dispute Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Support Agent a working Disputes queue/detail (assign, notes, chat threads, Accept/Reject/Manual full-escrow settlement) and a Categories admin screen, per the approved W10 spec.

**Architecture:** Service-interface-first, TDD. New JDBC ticket/category repositories; a pure-Java settlement calculator; one atomic `DisputeSettlementServiceImpl` that writes wallets, ledger rows, ticket, booking and audit in a single DB transaction and publishes events after commit; `TicketServiceImpl` for queue/assign/notes/categories; `DisputeQueryServiceImpl` read models for the UI; a temporary `InMemoryMessageService` for chat. JavaFX controllers depend only on `AppContext` service accessors.

**Tech Stack:** Java 25, JavaFX 25 (FXML), SQLite via plain JDBC, JUnit 5 (incl. `@ParameterizedTest`), Gradle + checkstyle.

**Spec:** [`docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md`](../specs/2026-09-25-w10-agent-dispute-resolution-design.md) — read it first. Decisions C16–C23 and deviations D5–D9 are in `PROJECT_STATE.md`.

---

## Read this before Task 1

**Environment.** Windows/PowerShell. Run tests with `.\gradlew test --tests "<fully.qualified.Class>"`; full build with `.\gradlew build` (includes checkstyle on main **and** test sources). Working directory is the repo root — tests use root-relative paths such as `db/snoozeshare-mock.db`.

**Checkstyle rules that bite** (config `config/checkstyle/checkstyle.xml`): 120-column limit; import groups in order `static` → `java.*` → `org.*` → `com.*`/`javafx.*` (blank line between groups); no star imports, no unused imports; braces on every `if`/`for`; `@TempDir` as a **method parameter** (fields must be `private`); `+`/`&&`/`?` at the *start* of a wrapped line, `=` at the *end*; 4-space indent, 8 for continuation; no tabs; file ends with a newline; empty `catch` needs a comment. **No non-ASCII characters in `.java` files** — write em dashes as `—`. FXML files are UTF-8 and may contain them.

**Style of existing code to match.** JDBC adapters take a `Connection` in the constructor, wrap `SQLException` in `IllegalStateException`, and go through `JdbcCodecs`/`RowMappers`. Services throw `IllegalArgumentException` for bad input and `IllegalStateException` for wrong state/role (see `AuthorizationService`). Money is `BigDecimal`; assertions on money read back from SQLite use `compareTo`, never `equals` (D4).

**Commit trailer.** Every commit message ends with the line `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` (shown below as a second `-m`).

**Facts verified while writing this plan** (don't re-derive):

| Fact | Consequence |
|---|---|
| `WalletTransactionRepository.findByBookingId` already exists | No repository addition needed for the escrow-held check. |
| `WalletLedgerWriter` opens its own transaction and computes `balanceAfter = balance + amount − fee`; the architecture doc and mock DB treat `feeAmount` as **informational** (`amount` is already net) | Settlement must **not** use it. `DisputeSettlementServiceImpl` writes wallet + ledger rows itself inside one outer transaction. |
| The mock seed used to store timestamps like `2026-08-28T14:00:00` (no `Z`), which `Instant.parse` rejects; the app itself always writes `Instant.toString()` (with `Z`) | Fixed at the source: all 129 seed timestamps now end in `Z` and the `.db` was rebuilt (S4, 2026-09-25). No codec change. |
| `MigrationRunner` unconditionally runs V001 (plain `CREATE TABLE`) if `schema_history` is absent | Task 3 lets it adopt a pre-provisioned reference DB. |
| V001 and `db/schema.sql` are identical for all 8 relevant tables today | Task 4's parity test should pass at once; if it ever fails, stop and record a deviation. |
| No test in the repo starts the FX toolkit; UI tests are file-content checks | UI logic is kept in pure classes; one guarded toolkit smoke test is added (skips itself if the toolkit cannot start). |
| W3 (`origin/w3`) stubs `TransactionServiceImpl.applyTicketRemedy/manualOverride` as `throw new UnsupportedOperationException("Owned by W10")` | See Task 18: at merge, delegate them or leave; W10 does not touch that file. |
| The admin shell (W1) is a **sidebar** (`Operations / Disputes / Accounts`), not the canvas's tab strip | W10 fills the center pane from the existing `Disputes` item and adds a `Categories` item. `ShellNavigationTest` requires `showOperations/showDisputes/showAccounts` to stay. |
| `theme.css` has the W1 navy palette, not the canvas "Fall Light" tokens | W10 adds classes using the existing palette; adopting the canvas palette is a separate UI-design-system workstream. |

**Spec amendments this plan makes** (applied to the spec in Task 18): (1) `DisputeSettlementService.settle(ticketId, mode, guestRefund, agentId, reason)` — booking is derived from the ticket and `ResolutionMode` replaces `SettlementKind`; (2) new `DisputeQueryService` read-model interface for the UI; (3) `MigrationRunner` adoption of a pre-provisioned DB (D10); (4) UI smoke test uses the FX toolkit directly (no TestFX `ApplicationTest`), and the shell is sidebar-based (D11).

---

## File map

**Create (main)**

| File | Responsibility |
|---|---|
| `domain/enums/AssigneeFilter.java` | `ALL`, `UNASSIGNED`, `MINE` |
| `domain/enums/ResolutionMode.java` | `ACCEPT`, `REJECT`, `MANUAL` |
| `domain/enums/ThreadChannel.java` | `GUEST`, `HOST` |
| `domain/model/Message.java` | Chat message record |
| `domain/settlement/package-info.java` | Package doc |
| `domain/settlement/SettlementBreakdown.java` | Result of splitting escrow |
| `domain/settlement/SettlementCalculator.java` | Pure split math (refund, host gross, 3% fee, net) |
| `domain/settlement/EscrowPolicy.java` | "Is escrow still held?" from a booking's ledger rows |
| `repository/jdbc/JdbcTicketRepository.java` | Ticket persistence + queue query |
| `repository/jdbc/JdbcTicketCategoryRepository.java` | Category persistence |
| `service/requests/ResolutionRequest.java` | UI → service resolution input |
| `service/Settlement.java` | Result of a settlement |
| `service/DisputeSettlementService.java` | Atomic settle interface |
| `service/DisputeQueryService.java`, `DisputeSummary.java`, `DisputeDetail.java` | UI read models |
| `service/MessageService.java` | Chat interface (W13 owns the real impl) |
| `service/impl/DisputeSettlementServiceImpl.java` | Atomic full-escrow settlement |
| `service/impl/TicketServiceImpl.java` | Queue, assign, notes, resolve, categories |
| `service/impl/DisputeQueryServiceImpl.java` | Builds `DisputeSummary` / `DisputeDetail` |
| `service/impl/InMemoryMessageService.java` | Temporary session-only chat |
| `ui/admin/tickets/ResolutionPreview.java` | Pure live-preview math for the dialog |
| `ui/admin/tickets/DisputeQueueController.java` (+ `dispute-queue.fxml`) | Queue screen |
| `ui/admin/tickets/DisputeDetailController.java` (+ `dispute-detail.fxml`) | Detail screen |
| `ui/admin/tickets/ResolutionDialogController.java` (+ `resolution-dialog.fxml`) | Accept/Reject/Manual dialog |
| `ui/admin/categories/CategoryAdminController.java` (+ `category-admin.fxml`) | Categories screen |

**Modify (main)**

| File | Change |
|---|---|
| `domain/statemachine/BookingStateMachine.java` | Allow `AGENT` on `CONFIRMED → COMPLETED` (C23) |
| `repository/jdbc/support/RowMappers.java` | `ticket`, `ticketCategory` mappers |
| `infra/db/migration/MigrationRunner.java` | Adopt a pre-provisioned DB |
| `repository/TicketRepository.java`, `TicketCategoryRepository.java` | Queue/category query methods |
| `service/TicketService.java` | New agent-facing contract |
| `app/AppContext.java`, `app/Main.java` | Wire new services; `create(jdbcUrl)`; `SNOOZESHARE_DB_URL` env var |
| `ui/admin/AdminShellController.java`, `resources/.../admin-shell.fxml` | Load queue/detail/categories into the center pane |
| `resources/.../ui/common/theme.css` | Badge/chip/chat classes |

**Create (test)** — all under `src/test/java/com/snoozeshare/`: `testsupport/{MockIds,MockDbFixture,Fakes}.java`, `domain/statemachine/AgentCompletionTest`, `domain/settlement/{SettlementCalculatorTest,EscrowPolicyTest}`, `repository/jdbc/{JdbcTicketRepositoryTest,JdbcTicketCategoryRepositoryTest}`, `infra/db/{SchemaParityTest,MigrationRunnerReferenceDbTest,MockDbFixtureTest}`, `service/{SettlementFixtures,DisputeSettlementServiceTest,DisputeSettlementAtomicityTest,TicketServiceTest,TicketServiceCategoryTest,TicketServiceIntegrationTest,DisputeQueryServiceTest,InMemoryMessageServiceTest,DisputeFlowEndToEndTest}`, `ui/admin/{ResolutionPreviewTest,AdminFxmlLayoutTest,AdminUiSmokeTest}`.

---

### Task 0: Baseline

**Files:** none.

- [x] **Step 1: Confirm branch and clean tree**

Run: `git branch --show-current; git status --short`
Expected: `agent-dispute-resolution-and-state-overrides`, and no output from `status`.

- [x] **Step 2: Run the whole suite to get a green baseline**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL` (all existing tests pass; checkstyle passes). If it fails **before** any change, stop and report — do not proceed on a red baseline. Note the number of tests reported so later tasks can confirm nothing disappeared.

- [x] **Step 3: Mark the plan in `PROJECT_STATE.md`**

In § Workstreams change W10 `Status` to `Planned`, `Plan` to `[W10 plan](docs/superpowers/plans/2026-09-25-w10-agent-dispute-resolution.md)`, `Progress` to `Task 0/18 baseline green`. Update session row S4 `Doing` to "Executing W10 plan".

- [x] **Step 4: Commit**

```powershell
git add PROJECT_STATE.md docs/superpowers/plans/2026-09-25-w10-agent-dispute-resolution.md
git commit -m "docs: add W10 implementation plan" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 1: Agent may complete a confirmed booking (C23)

**Files:**
- Modify: `src/main/java/com/snoozeshare/domain/statemachine/BookingStateMachine.java` (the `case COMPLETED` line inside `case CONFIRMED`)
- Test: `src/test/java/com/snoozeshare/domain/statemachine/AgentCompletionTest.java`

- [x] **Step 1: Write the failing test**

```java
package com.snoozeshare.domain.statemachine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.Role;

class AgentCompletionTest {

    @Test
    void agentCanCompleteAConfirmedBooking() {
        assertTrue(BookingStateMachine.canTransition(
                BookingStatus.CONFIRMED, BookingStatus.COMPLETED, Role.AGENT));
    }

    @Test
    void agentCannotCompleteAPendingBooking() {
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.PENDING, BookingStatus.COMPLETED, Role.AGENT));
    }

    @Test
    void hostStillCompletesAndGuestStillCannot() {
        assertTrue(BookingStateMachine.canTransition(
                BookingStatus.CONFIRMED, BookingStatus.COMPLETED, Role.HOST));
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.CONFIRMED, BookingStatus.COMPLETED, Role.GUEST));
    }

    @Test
    void completedIsTerminalEvenForAnAgent() {
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.COMPLETED, BookingStatus.FORCE_CANCELLED, Role.AGENT));
        assertFalse(BookingStateMachine.canTransition(
                BookingStatus.COMPLETED, BookingStatus.COMPLETED, Role.AGENT));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `.\gradlew test --tests "com.snoozeshare.domain.statemachine.AgentCompletionTest"`
Expected: `agentCanCompleteAConfirmedBooking` FAILS (`expected: <true> but was: <false>`); the other three pass.

- [x] **Step 3: Minimal implementation**

In `BookingStateMachine.java`, inside the `case CONFIRMED -> switch (to) { ... }` block change

```java
                case COMPLETED -> actingRole == Role.HOST;
```

to

```java
                case COMPLETED -> actingRole == Role.HOST || actingRole == Role.AGENT;
```

- [x] **Step 4: Run the state-machine tests**

Run: `.\gradlew test --tests "com.snoozeshare.domain.statemachine.*"`
Expected: PASS (both `AgentCompletionTest` and the existing `StateMachineTest`).

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/snoozeshare/domain/statemachine/BookingStateMachine.java src/test/java/com/snoozeshare/domain/statemachine/AgentCompletionTest.java
git commit -m "feat: allow agent CONFIRMED->COMPLETED in BookingStateMachine (C23)" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Pure settlement math and escrow policy

**Files:**
- Create: `src/main/java/com/snoozeshare/domain/settlement/package-info.java`, `SettlementBreakdown.java`, `SettlementCalculator.java`, `EscrowPolicy.java`
- Test: `src/test/java/com/snoozeshare/domain/settlement/SettlementCalculatorTest.java`, `EscrowPolicyTest.java`

- [x] **Step 1: Write the failing calculator test**

```java
package com.snoozeshare.domain.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    @ParameterizedTest
    @CsvSource({
        "480.00, 100.00, 100.00, 380.00, 11.40, 368.60",
        "330.00, 165.00, 165.00, 165.00, 4.95, 160.05",
        "875.00, 0, 0.00, 875.00, 26.25, 848.75",
        "210.00, 210.00, 210.00, 0.00, 0.00, 0.00",
        "100.01, 0, 0.00, 100.01, 3.00, 97.01",
        "0.50, 0, 0.00, 0.50, 0.02, 0.48"
    })
    void splitsEscrowIntoGuestRefundAndHostPayoutNetOfThreePercent(
            String escrow, String refund, String expectedRefund, String expectedGross,
            String expectedFee, String expectedNet) {
        SettlementBreakdown split = SettlementCalculator.split(
                new BigDecimal(escrow), new BigDecimal(refund));

        assertEquals(0, new BigDecimal(expectedRefund).compareTo(split.guestRefund()));
        assertEquals(0, new BigDecimal(expectedGross).compareTo(split.hostGross()));
        assertEquals(0, new BigDecimal(expectedFee).compareTo(split.fee()));
        assertEquals(0, new BigDecimal(expectedNet).compareTo(split.hostNet()));
        assertEquals(0, split.escrow().compareTo(split.guestRefund().add(split.hostGross())));
    }

    @Test
    void rejectsRefundAboveEscrow() {
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                new BigDecimal("210.00"), new BigDecimal("210.01")));
    }

    @Test
    void rejectsNegativeRefundAndNonPositiveEscrow() {
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                new BigDecimal("210.00"), new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                new BigDecimal("210.00"), null));
    }

    @Test
    void rejectsRefundWithMoreThanTwoDecimalPlaces() {
        assertThrows(IllegalArgumentException.class, () -> SettlementCalculator.split(
                new BigDecimal("210.00"), new BigDecimal("10.005")));
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimalPlaces() {
        SettlementBreakdown split = SettlementCalculator.split(
                new BigDecimal("210.00"), new BigDecimal("10.500"));

        assertEquals(0, new BigDecimal("10.50").compareTo(split.guestRefund()));
    }
}
```

Import order note: `org.junit.jupiter.api.Test` sorts **before** `org.junit.jupiter.params...`. Put the imports in this order to satisfy checkstyle: `org.junit.jupiter.api.Test;` then `org.junit.jupiter.params.ParameterizedTest;` then `org.junit.jupiter.params.provider.CsvSource;`.

- [x] **Step 2: Run to verify it fails**

Run: `.\gradlew test --tests "com.snoozeshare.domain.settlement.SettlementCalculatorTest"`
Expected: FAIL — compilation error, `cannot find symbol: class SettlementCalculator`.

- [x] **Step 3: Implement**

`package-info.java`:

```java
/**
 * Pure escrow-settlement rules for dispute resolution: split math and the "escrow held" test.
 */
package com.snoozeshare.domain.settlement;
```

`SettlementBreakdown.java`:

```java
package com.snoozeshare.domain.settlement;

import java.math.BigDecimal;

/**
 * How a booking's held escrow is divided: a fee-free guest refund and a host share net of the platform fee.
 */
public record SettlementBreakdown(
        BigDecimal escrow,
        BigDecimal guestRefund,
        BigDecimal hostGross,
        BigDecimal fee,
        BigDecimal hostNet
) {
}
```

`SettlementCalculator.java`:

```java
package com.snoozeshare.domain.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.snoozeshare.domain.validation.DomainValidation;

public final class SettlementCalculator {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");

    private SettlementCalculator() {
    }

    /**
     * Splits the held escrow. The guest refund carries no fee; the platform takes 3% of the host share only.
     */
    public static SettlementBreakdown split(BigDecimal escrow, BigDecimal guestRefund) {
        DomainValidation.requirePositive(escrow, "escrow");
        DomainValidation.requireNonNegative(guestRefund, "guestRefund");
        if (guestRefund.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Guest refund must have at most two decimal places");
        }
        if (guestRefund.compareTo(escrow) > 0) {
            throw new IllegalArgumentException("Guest refund cannot exceed the escrow held");
        }
        BigDecimal held = escrow.setScale(2, RoundingMode.HALF_UP);
        BigDecimal refund = guestRefund.setScale(2, RoundingMode.HALF_UP);
        BigDecimal hostGross = held.subtract(refund);
        BigDecimal fee = hostGross.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        return new SettlementBreakdown(held, refund, hostGross, fee, hostGross.subtract(fee));
    }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `.\gradlew test --tests "com.snoozeshare.domain.settlement.SettlementCalculatorTest"`
Expected: PASS (all 9 invocations).

- [x] **Step 5: Write the failing escrow-policy test**

```java
package com.snoozeshare.domain.settlement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.WalletTransaction;

class EscrowPolicyTest {

    private static WalletTransaction tx(WalletTransactionType type) {
        return new WalletTransaction(UUID.randomUUID(), UUID.randomUUID(), type,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, UUID.randomUUID(), null, null,
                Instant.parse("2026-09-25T00:00:00Z"));
    }

    @Test
    void escrowIsHeldWhenOnlyAHoldExists() {
        assertTrue(EscrowPolicy.isHeld(List.of(tx(WalletTransactionType.ESCROW_HOLD))));
    }

    @Test
    void escrowIsNotHeldWithNoHold() {
        assertFalse(EscrowPolicy.isHeld(List.of()));
        assertFalse(EscrowPolicy.isHeld(List.of(tx(WalletTransactionType.TOP_UP))));
    }

    @Test
    void anyReleasingRowMeansEscrowIsNoLongerHeld() {
        for (WalletTransactionType releasing : List.of(WalletTransactionType.ESCROW_REFUND,
                WalletTransactionType.BOOKING_PAYOUT, WalletTransactionType.TICKET_REMEDY,
                WalletTransactionType.AGENT_OVERRIDE)) {
            assertFalse(EscrowPolicy.isHeld(List.of(tx(WalletTransactionType.ESCROW_HOLD), tx(releasing))),
                    releasing.name());
        }
    }
}
```

- [x] **Step 6: Run to verify it fails**

Run: `.\gradlew test --tests "com.snoozeshare.domain.settlement.EscrowPolicyTest"`
Expected: FAIL — `cannot find symbol: class EscrowPolicy`.

- [x] **Step 7: Implement `EscrowPolicy`**

```java
package com.snoozeshare.domain.settlement;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.WalletTransaction;

public final class EscrowPolicy {

    private static final Set<WalletTransactionType> RELEASING = EnumSet.of(
            WalletTransactionType.ESCROW_REFUND, WalletTransactionType.BOOKING_PAYOUT,
            WalletTransactionType.TICKET_REMEDY, WalletTransactionType.AGENT_OVERRIDE);

    private EscrowPolicy() {
    }

    /**
     * Escrow is held when a booking has an ESCROW_HOLD row and nothing has released it yet.
     */
    public static boolean isHeld(List<WalletTransaction> bookingTransactions) {
        boolean held = false;
        for (WalletTransaction transaction : bookingTransactions) {
            if (RELEASING.contains(transaction.type())) {
                return false;
            }
            if (transaction.type() == WalletTransactionType.ESCROW_HOLD) {
                held = true;
            }
        }
        return held;
    }
}
```

- [x] **Step 8: Run both test classes**

Run: `.\gradlew test --tests "com.snoozeshare.domain.settlement.*"`
Expected: PASS.

- [x] **Step 9: Commit**

```powershell
git add src/main/java/com/snoozeshare/domain/settlement src/test/java/com/snoozeshare/domain/settlement
git commit -m "feat: add pure settlement calculator and escrow policy" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Read the mock DB — adoptable schema and test fixture

The mock DB has no `schema_history` table, so `MigrationRunner` would try to re-create its tables. (Its timestamps already end in `Z`, matching what the app writes, so no codec change is needed.)

**Files:**
- Modify: `src/main/java/com/snoozeshare/infra/db/migration/MigrationRunner.java`
- Create (test support, needed here): `src/test/java/com/snoozeshare/testsupport/MockIds.java`, `MockDbFixture.java`
- Test: `src/test/java/com/snoozeshare/infra/db/MockDbFixtureTest.java`, `src/test/java/com/snoozeshare/infra/db/MigrationRunnerReferenceDbTest.java`

- [x] **Step 1: Create the shared test support** — `MockIds.java`

```java
package com.snoozeshare.testsupport;

import java.util.UUID;

/**
 * Identifiers from the committed mock DB (db/seed-mock-data.sql).
 */
public final class MockIds {

    public static final UUID AGENT_AMY = id("a0000000-0000-0000-0000-000000000001");
    public static final UUID AGENT_BEN = id("a0000000-0000-0000-0000-000000000002");
    public static final UUID AGENT_CHEN = id("a0000000-0000-0000-0000-000000000003");

    public static final UUID TICKET_1 = id("d0000000-0000-0000-0000-000000000001");
    public static final UUID TICKET_2 = id("d0000000-0000-0000-0000-000000000002");
    public static final UUID TICKET_3 = id("d0000000-0000-0000-0000-000000000003");
    public static final UUID TICKET_4 = id("d0000000-0000-0000-0000-000000000004");
    public static final UUID TICKET_5 = id("d0000000-0000-0000-0000-000000000005");
    public static final UUID TICKET_6 = id("d0000000-0000-0000-0000-000000000006");

    public static final UUID BOOKING_9 = id("20000000-0000-0000-0000-000000000009");
    public static final UUID BOOKING_10 = id("20000000-0000-0000-0000-000000000010");
    public static final UUID BOOKING_11 = id("20000000-0000-0000-0000-000000000011");
    public static final UUID BOOKING_13 = id("20000000-0000-0000-0000-000000000013");

    public static final UUID GUEST_ARIA = id("c0000000-0000-0000-0000-000000000003");
    public static final UUID GUEST_SOPHIA = id("c0000000-0000-0000-0000-000000000005");
    public static final UUID HOST_PRIYA = id("b0000000-0000-0000-0000-000000000003");
    public static final UUID HOST_DIEGO = id("b0000000-0000-0000-0000-000000000004");

    public static final UUID WALLET_ARIA = id("30000000-0000-0000-0000-000000000003");
    public static final UUID WALLET_SOPHIA = id("30000000-0000-0000-0000-000000000005");
    public static final UUID WALLET_PRIYA = id("30000000-0000-0000-0000-000000000010");
    public static final UUID WALLET_DIEGO = id("30000000-0000-0000-0000-000000000011");

    public static final UUID CATEGORY_CLEANLINESS = id("70000000-0000-0000-0000-000000000001");

    private MockIds() {
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }
}
```

`MockDbFixture.java`:

```java
package com.snoozeshare.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import com.snoozeshare.infra.db.ConnectionFactory;

/**
 * A throw-away copy of the committed mock DB. The copy exists only so mutating tests never write to
 * the committed file; the data is used exactly as the team sees it.
 */
public final class MockDbFixture implements AutoCloseable {

    public static final Path COMMITTED_DB = Path.of("db/snoozeshare-mock.db");

    private final Path copy;
    private final Connection connection;

    private MockDbFixture(Path copy) throws SQLException {
        this.copy = copy;
        this.connection = ConnectionFactory.open(urlFor(copy));
    }

    public static MockDbFixture open(Path directory) throws IOException, SQLException {
        Path copy = directory.resolve("mock-copy.db");
        Files.copy(COMMITTED_DB, copy);
        return new MockDbFixture(copy);
    }

    private static String urlFor(Path path) {
        return "jdbc:sqlite:" + path.toAbsolutePath();
    }

    public Connection connection() {
        return connection;
    }

    public String jdbcUrl() {
        return urlFor(copy);
    }

    public long scalarLong(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    public String scalarString(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    public void execute(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    public BigDecimal walletBalance(UUID walletId) throws SQLException {
        return new BigDecimal(scalarString(
                "SELECT balance FROM wallets WHERE walletId = ?", walletId));
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i] instanceof UUID ? params[i].toString() : params[i];
            statement.setObject(i + 1, value);
        }
    }

    /**
     * Every wallet balance equals the sum of its transactions, and every row's balanceAfter equals the
     * chronological running sum.
     */
    public void assertLedgerInvariant() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(
                    "SELECT w.walletId AS walletId, w.balance AS balance, "
                            + "COALESCE(SUM(t.amount), 0) AS total FROM wallets w "
                            + "LEFT JOIN wallet_transactions t ON t.walletId = w.walletId "
                            + "GROUP BY w.walletId")) {
                while (result.next()) {
                    assertEquals(result.getDouble("total"), result.getDouble("balance"), 0.005,
                            "balance != sum(transactions) for wallet " + result.getString("walletId"));
                }
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT walletId, transactionId, amount, balanceAfter FROM wallet_transactions "
                            + "ORDER BY walletId, createdAt, transactionId")) {
                String currentWallet = null;
                double running = 0;
                while (result.next()) {
                    if (!result.getString("walletId").equals(currentWallet)) {
                        currentWallet = result.getString("walletId");
                        running = 0;
                    }
                    running += result.getDouble("amount");
                    assertEquals(running, result.getDouble("balanceAfter"), 0.005,
                            "balanceAfter chain broken at " + result.getString("transactionId"));
                }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
```

- [x] **Step 2: Write the failing fixture and migration tests**

`MockDbFixtureTest.java`:

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class MockDbFixtureTest {

    @Test
    void theCommittedMockDbSatisfiesTheLedgerInvariantAndForeignKeys(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.assertLedgerInvariant();
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM pragma_foreign_key_check"));
        }
    }

    @Test
    void mutatingTheCopyNeverTouchesTheCommittedFile(@TempDir Path directory) throws Exception {
        byte[] before = Files.readAllBytes(MockDbFixture.COMMITTED_DB);
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("UPDATE wallets SET balance = 1 WHERE walletId = ?", MockIds.WALLET_ARIA);
            assertNotEquals(0, db.walletBalance(MockIds.WALLET_ARIA).compareTo(new BigDecimal("525")));
        }
        assertArrayEquals(before, Files.readAllBytes(MockDbFixture.COMMITTED_DB));
    }
}
```

`MigrationRunnerReferenceDbTest.java`:

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.testsupport.MockDbFixture;

class MigrationRunnerReferenceDbTest {

    @Test
    void adoptsAPreProvisionedReferenceDatabaseWithoutReapplyingTheFoundation(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MigrationRunner.migrate(db.connection());
            MigrationRunner.migrate(db.connection());

            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM schema_history WHERE version = 1"));
            assertEquals(16L, db.scalarLong("SELECT COUNT(*) FROM users"));
        }
    }
}
```

- [x] **Step 3: Run to verify they fail**

Run: `.\gradlew test --tests "com.snoozeshare.infra.db.MockDbFixtureTest" --tests "com.snoozeshare.infra.db.MigrationRunnerReferenceDbTest"`
Expected: `MockDbFixtureTest` PASSES (it only needs Step 1); `MigrationRunnerReferenceDbTest` FAILS with `table users already exists`.

- [x] **Step 4: Implement the adoption in `MigrationRunner.migrate`**

Replace the body of the `if (!migrationApplied(...))` block:

```java
            if (!migrationApplied(connection, FOUNDATION_VERSION)) {
                if (!tableExists(connection, "users")) {
                    applyFoundationMigration(connection);
                }
                // else: a pre-provisioned reference database (db/snoozeshare-mock.db) already has the schema.
                recordMigration(connection, FOUNDATION_VERSION);
            }
```

and add the helper next to `migrationApplied`:

```java
    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
```

- [x] **Step 5: Run the whole suite**

Run: `.\gradlew test`
Expected: PASS. (`DatabaseBootstrapTest` still passes — a fresh in-memory DB has no `users` table, so the foundation is applied as before.)

- [x] **Step 6: Commit**

```powershell
git add src/main src/test
git commit -m "feat: adopt pre-provisioned DBs in MigrationRunner; add mock-DB test fixture" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Schema parity test

Guards the hand-written mock DB schema against drifting from the app's migration (D6).

**Files:**
- Test: `src/test/java/com/snoozeshare/infra/db/SchemaParityTest.java`

- [x] **Step 1: Write the test**

```java
package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SchemaParityTest {

    private static final List<String> TABLES = List.of("users", "properties", "bookings", "wallets",
            "wallet_transactions", "tickets", "ticket_categories", "audit_log");

    @Test
    void referenceSchemaMatchesTheFoundationMigrationForEveryTableW10Uses() throws Exception {
        try (Connection migration = DatabaseTestSupport.openIsolatedDatabase();
             Connection reference = DatabaseTestSupport.openIsolatedDatabase()) {
            apply(migration, "src/main/resources/db/migration/V001__foundation.sql");
            apply(reference, "db/schema.sql");

            for (String table : TABLES) {
                assertEquals(columns(migration, table), columns(reference, table), table);
            }
        }
    }

    private static void apply(Connection connection, String file) throws Exception {
        String sql = Files.readString(Path.of(file)).lines()
                .map(line -> line.replaceAll("--.*", ""))
                .reduce("", (left, right) -> left + "\n" + right);
        for (String statementSql : sql.split(";")) {
            if (!statementSql.isBlank()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(statementSql);
                }
            }
        }
    }

    private static List<String> columns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                columns.add(result.getInt("cid") + "|" + result.getString("name") + "|"
                        + result.getString("type") + "|" + result.getInt("notnull") + "|"
                        + result.getString("dflt_value") + "|" + result.getInt("pk"));
            }
        }
        return columns;
    }
}
```

- [x] **Step 2: Run**

Run: `.\gradlew test --tests "com.snoozeshare.infra.db.SchemaParityTest"`
Expected: PASS (verified equal when this plan was written). **If it fails:** the two schemas have drifted — do not "fix" either silently; record the difference as a deviation in `PROJECT_STATE.md` § Deviations and ask the operator which side is right.

- [x] **Step 3: Commit**

```powershell
git add src/test/java/com/snoozeshare/infra/db/SchemaParityTest.java
git commit -m "test: guard mock-DB schema against migration drift" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Ticket and category persistence

**Files:**
- Create: `src/main/java/com/snoozeshare/domain/enums/AssigneeFilter.java`
- Modify: `repository/TicketRepository.java`, `repository/TicketCategoryRepository.java`, `repository/jdbc/support/RowMappers.java`
- Create: `repository/jdbc/JdbcTicketRepository.java`, `repository/jdbc/JdbcTicketCategoryRepository.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcTicketRepositoryTest.java`, `JdbcTicketCategoryRepositoryTest.java`

- [x] **Step 1: Add the enum and extend the repository interfaces**

`AssigneeFilter.java`:

```java
package com.snoozeshare.domain.enums;

public enum AssigneeFilter {
    ALL,
    UNASSIGNED,
    MINE
}
```

`TicketRepository.java` — add imports `com.snoozeshare.domain.enums.AssigneeFilter` and this method:

```java
    /**
     * Tickets oldest first. A null status means every status; MINE requires a non-null agentId.
     */
    List<Ticket> findQueue(TicketStatus status, AssigneeFilter assignee, UUID agentId);
```

`TicketCategoryRepository.java` — add imports `java.util.Optional`, `java.util.UUID` and:

```java
    List<TicketCategory> findAll();

    Optional<TicketCategory> findById(UUID categoryId);

    /**
     * Case-insensitive label check; excludeCategoryId (nullable) is ignored so a rename to itself is allowed.
     */
    boolean labelInUse(String label, UUID excludeCategoryId);
```

- [x] **Step 2: Write the failing ticket repository test**

```java
package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class JdbcTicketRepositoryTest {

    @Test
    void readsEveryFieldOfAnOpenMockTicket(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            Ticket ticket = new JdbcTicketRepository(db.connection())
                    .findById(MockIds.TICKET_2).orElseThrow();

            assertEquals(MockIds.BOOKING_9, ticket.bookingId());
            assertEquals(MockIds.GUEST_ARIA, ticket.raisedByUserId());
            assertEquals(Role.GUEST, ticket.raisedByRole());
            assertEquals("Property Mismatch", ticket.category());
            assertEquals("Missing promised beach access", ticket.title());
            assertEquals(RemedyType.OTHER, ticket.requestedRemedy());
            assertNull(ticket.supportingText());
            assertEquals(TicketStatus.OPEN, ticket.status());
            assertNull(ticket.assignedAgentId());
            assertNull(ticket.agentNotes());
            assertNull(ticket.resolutionReason());
            assertEquals(Instant.parse("2026-08-10T09:00:00Z"), ticket.createdAt());
            assertNull(ticket.resolvedAt());
        }
    }

    @Test
    void queueIsOldestFirst(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            List<UUID> ids = new JdbcTicketRepository(db.connection())
                    .findQueue(null, AssigneeFilter.ALL, null).stream().map(Ticket::ticketId).toList();

            assertEquals(List.of(MockIds.TICKET_4, MockIds.TICKET_2, MockIds.TICKET_1,
                    MockIds.TICKET_6, MockIds.TICKET_3, MockIds.TICKET_5), ids);
        }
    }

    @Test
    void queueFiltersByAssigneeAndStatus(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketRepository repository = new JdbcTicketRepository(db.connection());

            assertEquals(List.of(MockIds.TICKET_2), ids(repository.findQueue(
                    null, AssigneeFilter.UNASSIGNED, null)));
            assertEquals(List.of(MockIds.TICKET_3), ids(repository.findQueue(
                    null, AssigneeFilter.MINE, MockIds.AGENT_BEN)));
            assertEquals(List.of(MockIds.TICKET_6, MockIds.TICKET_5), ids(repository.findQueue(
                    null, AssigneeFilter.MINE, MockIds.AGENT_CHEN)));
            assertEquals(List.of(MockIds.TICKET_2), ids(repository.findQueue(
                    TicketStatus.OPEN, AssigneeFilter.ALL, null)));
            assertEquals(List.of(MockIds.TICKET_3), ids(repository.findQueue(
                    TicketStatus.UNDER_REVIEW, AssigneeFilter.ALL, null)));
        }
    }

    @Test
    void mineWithoutAnAgentIdIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketRepository repository = new JdbcTicketRepository(db.connection());

            assertThrows(IllegalArgumentException.class,
                    () -> repository.findQueue(null, AssigneeFilter.MINE, null));
        }
    }

    @Test
    void saveUpdatesStatusAssigneeNotesAndResolution(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketRepository repository = new JdbcTicketRepository(db.connection());
            Ticket open = repository.findById(MockIds.TICKET_2).orElseThrow();
            Instant resolvedAt = Instant.parse("2026-09-25T04:00:00Z");

            repository.save(new Ticket(open.ticketId(), open.bookingId(), open.raisedByUserId(),
                    open.raisedByRole(), open.category(), open.title(), open.description(),
                    open.requestedRemedy(), open.supportingText(), TicketStatus.RESOLVED_APPROVED,
                    MockIds.AGENT_AMY, "checked photos", "gate was locked", open.createdAt(), resolvedAt));

            Ticket reread = repository.findById(MockIds.TICKET_2).orElseThrow();
            assertEquals(TicketStatus.RESOLVED_APPROVED, reread.status());
            assertEquals(MockIds.AGENT_AMY, reread.assignedAgentId());
            assertEquals("checked photos", reread.agentNotes());
            assertEquals("gate was locked", reread.resolutionReason());
            assertEquals(resolvedAt, reread.resolvedAt());
            assertEquals(open.title(), reread.title());
        }
    }

    private static List<UUID> ids(List<Ticket> tickets) {
        return tickets.stream().map(Ticket::ticketId).toList();
    }
}
```

- [x] **Step 3: Run to verify it fails**

Run: `.\gradlew test --tests "com.snoozeshare.repository.jdbc.JdbcTicketRepositoryTest"`
Expected: FAIL — `cannot find symbol: class JdbcTicketRepository`.

- [x] **Step 4: Implement mappers and `JdbcTicketRepository`**

In `RowMappers.java` add imports `com.snoozeshare.domain.enums.RemedyType`, `TicketStatus`, `com.snoozeshare.domain.model.Ticket`, `TicketCategory` (alphabetical within their groups) and two methods:

```java
    public static Ticket ticket(ResultSet result) throws SQLException {
        return new Ticket(
                JdbcCodecs.uuid(result.getString("ticketId")),
                JdbcCodecs.uuid(result.getString("bookingId")),
                JdbcCodecs.uuid(result.getString("raisedByUserId")),
                Role.valueOf(result.getString("raisedByRole")),
                result.getString("category"),
                result.getString("title"),
                result.getString("description"),
                RemedyType.valueOf(result.getString("requestedRemedy")),
                result.getString("supportingText"),
                TicketStatus.valueOf(result.getString("status")),
                JdbcCodecs.uuid(result.getString("assignedAgentId")),
                result.getString("agentNotes"),
                result.getString("resolutionReason"),
                JdbcCodecs.instant(result.getString("createdAt")),
                JdbcCodecs.instant(result.getString("resolvedAt")));
    }

    public static TicketCategory ticketCategory(ResultSet result) throws SQLException {
        return new TicketCategory(
                JdbcCodecs.uuid(result.getString("categoryId")),
                result.getString("label"),
                result.getInt("active") != 0);
    }
```

`JdbcTicketRepository.java`:

```java
package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcTicketRepository implements TicketRepository {

    private final Connection connection;

    public JdbcTicketRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Ticket> findById(UUID ticketId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE ticketId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(ticketId));
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(RowMappers.ticket(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query ticket", exception);
        }
    }

    @Override
    public List<Ticket> findByStatus(TicketStatus status) {
        return findQueue(status, AssigneeFilter.ALL, null);
    }

    @Override
    public List<Ticket> findQueue(TicketStatus status, AssigneeFilter assignee, UUID agentId) {
        AssigneeFilter filter = assignee == null ? AssigneeFilter.ALL : assignee;
        if (filter == AssigneeFilter.MINE && agentId == null) {
            throw new IllegalArgumentException("AgentId is required for the MINE filter");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM tickets WHERE 1 = 1");
        List<String> params = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        if (filter == AssigneeFilter.UNASSIGNED) {
            sql.append(" AND assignedAgentId IS NULL");
        } else if (filter == AssigneeFilter.MINE) {
            sql.append(" AND assignedAgentId = ?");
            params.add(JdbcCodecs.uuid(agentId));
        }
        sql.append(" ORDER BY createdAt, ticketId");
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (var result = statement.executeQuery()) {
                List<Ticket> tickets = new ArrayList<>();
                while (result.next()) {
                    tickets.add(RowMappers.ticket(result));
                }
                return tickets;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query tickets", exception);
        }
    }

    @Override
    public Ticket save(Ticket ticket) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO tickets (ticketId, bookingId, raisedByUserId, raisedByRole, category, "
                        + "title, description, requestedRemedy, supportingText, status, "
                        + "assignedAgentId, agentNotes, resolutionReason, createdAt, resolvedAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(ticketId) DO UPDATE SET status = excluded.status, "
                        + "assignedAgentId = excluded.assignedAgentId, "
                        + "agentNotes = excluded.agentNotes, "
                        + "resolutionReason = excluded.resolutionReason, "
                        + "resolvedAt = excluded.resolvedAt")) {
            statement.setString(1, JdbcCodecs.uuid(ticket.ticketId()));
            statement.setString(2, JdbcCodecs.uuid(ticket.bookingId()));
            statement.setString(3, JdbcCodecs.uuid(ticket.raisedByUserId()));
            statement.setString(4, ticket.raisedByRole().name());
            statement.setString(5, ticket.category());
            statement.setString(6, ticket.title());
            statement.setString(7, ticket.description());
            statement.setString(8, ticket.requestedRemedy().name());
            statement.setString(9, ticket.supportingText());
            statement.setString(10, ticket.status().name());
            statement.setString(11, JdbcCodecs.uuid(ticket.assignedAgentId()));
            statement.setString(12, ticket.agentNotes());
            statement.setString(13, ticket.resolutionReason());
            statement.setString(14, JdbcCodecs.instant(ticket.createdAt()));
            statement.setString(15, JdbcCodecs.instant(ticket.resolvedAt()));
            statement.executeUpdate();
            return ticket;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save ticket", exception);
        }
    }
}
```

- [x] **Step 5: Run to verify it passes**

Run: `.\gradlew test --tests "com.snoozeshare.repository.jdbc.JdbcTicketRepositoryTest"`
Expected: PASS (5 tests). If `readsEveryFieldOfAnOpenMockTicket` fails on `createdAt`, re-check that the seed timestamps end in `Z`.

- [x] **Step 6: Write the failing category repository test**

```java
package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class JdbcTicketCategoryRepositoryTest {

    @Test
    void listsActiveCategoriesAlphabetically(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            List<String> labels = new JdbcTicketCategoryRepository(db.connection())
                    .findActive().stream().map(TicketCategory::label).toList();

            assertEquals(List.of("Cancellation Dispute", "Cleanliness", "Damage Dispute",
                    "Host Unresponsive", "Other", "Property Mismatch"), labels);
        }
    }

    @Test
    void deactivatedCategoriesLeaveTheActiveListButStayInFindAll(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketCategoryRepository repository = new JdbcTicketCategoryRepository(db.connection());
            TicketCategory cleanliness = repository.findById(MockIds.CATEGORY_CLEANLINESS).orElseThrow();

            repository.save(new TicketCategory(cleanliness.categoryId(), cleanliness.label(), false));

            assertEquals(5, repository.findActive().size());
            assertEquals(6, repository.findAll().size());
            assertFalse(repository.findById(MockIds.CATEGORY_CLEANLINESS).orElseThrow().active());
        }
    }

    @Test
    void savesNewAndRenamedCategories(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketCategoryRepository repository = new JdbcTicketCategoryRepository(db.connection());
            UUID noiseId = UUID.randomUUID();

            repository.save(new TicketCategory(noiseId, "Noise", true));
            repository.save(new TicketCategory(noiseId, "Noise complaint", true));

            assertEquals("Noise complaint", repository.findById(noiseId).orElseThrow().label());
            assertEquals(7, repository.findAll().size());
        }
    }

    @Test
    void labelInUseIsCaseInsensitiveAndCanExcludeTheCategoryBeingRenamed(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketCategoryRepository repository = new JdbcTicketCategoryRepository(db.connection());

            assertTrue(repository.labelInUse("cleanliness", null));
            assertFalse(repository.labelInUse("cleanliness", MockIds.CATEGORY_CLEANLINESS));
            assertFalse(repository.labelInUse("Noise", null));
        }
    }
}
```

- [x] **Step 7: Run to verify it fails**, then **implement** `JdbcTicketCategoryRepository`

Run: `.\gradlew test --tests "com.snoozeshare.repository.jdbc.JdbcTicketCategoryRepositoryTest"` → FAIL (`cannot find symbol`).

```java
package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.repository.TicketCategoryRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcTicketCategoryRepository implements TicketCategoryRepository {

    private final Connection connection;

    public JdbcTicketCategoryRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<TicketCategory> findActive() {
        return query("SELECT * FROM ticket_categories WHERE active = 1 "
                + "ORDER BY label COLLATE NOCASE");
    }

    @Override
    public List<TicketCategory> findAll() {
        return query("SELECT * FROM ticket_categories ORDER BY label COLLATE NOCASE");
    }

    @Override
    public Optional<TicketCategory> findById(UUID categoryId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM ticket_categories WHERE categoryId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(categoryId));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(RowMappers.ticketCategory(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query ticket category", exception);
        }
    }

    @Override
    public boolean labelInUse(String label, UUID excludeCategoryId) {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM ticket_categories WHERE LOWER(label) = LOWER(?) "
                        + "AND (? IS NULL OR categoryId <> ?)")) {
            String excluded = JdbcCodecs.uuid(excludeCategoryId);
            statement.setString(1, label);
            statement.setString(2, excluded);
            statement.setString(3, excluded);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check ticket category label", exception);
        }
    }

    @Override
    public TicketCategory save(TicketCategory category) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO ticket_categories (categoryId, label, active) VALUES (?, ?, ?) "
                        + "ON CONFLICT(categoryId) DO UPDATE SET label = excluded.label, "
                        + "active = excluded.active")) {
            statement.setString(1, JdbcCodecs.uuid(category.categoryId()));
            statement.setString(2, category.label());
            statement.setInt(3, category.active() ? 1 : 0);
            statement.executeUpdate();
            return category;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save ticket category", exception);
        }
    }

    private List<TicketCategory> query(String sql) {
        try (var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            List<TicketCategory> categories = new ArrayList<>();
            while (result.next()) {
                categories.add(RowMappers.ticketCategory(result));
            }
            return categories;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query ticket categories", exception);
        }
    }
}
```

- [x] **Step 8: Run all repository tests**

Run: `.\gradlew test --tests "com.snoozeshare.repository.*"`
Expected: PASS.

- [x] **Step 9: Commit**

```powershell
git add src/main src/test
git commit -m "feat: add JDBC ticket and ticket-category repositories" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Messaging interface and temporary in-memory implementation

**Files:**
- Create: `domain/enums/ThreadChannel.java`, `domain/model/Message.java`, `service/MessageService.java`, `service/impl/InMemoryMessageService.java`
- Test: `src/test/java/com/snoozeshare/service/InMemoryMessageServiceTest.java`

- [x] **Step 1: Create the types**

`ThreadChannel.java`:

```java
package com.snoozeshare.domain.enums;

public enum ThreadChannel {
    GUEST,
    HOST
}
```

`Message.java`:

```java
package com.snoozeshare.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;

public record Message(
        UUID messageId,
        UUID ticketId,
        ThreadChannel channel,
        UUID authorId,
        Role authorRole,
        String body,
        Instant sentAt
) {
}
```

`MessageService.java` (interface owned by W13; W10 declares it so it can compile against it):

```java
package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;

/**
 * Ticket chat. Contract owned by workstream W13 (Messaging); W10 consumes it.
 */
public interface MessageService {
    List<Message> thread(UUID ticketId, ThreadChannel channel);

    Message post(UUID ticketId, ThreadChannel channel, UUID authorId, Role authorRole, String body);
}
```

- [x] **Step 2: Write the failing test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.service.impl.InMemoryMessageService;

class InMemoryMessageServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC);
    private final MessageService service = new InMemoryMessageService(clock);
    private final UUID ticket = UUID.randomUUID();
    private final UUID agent = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();

    @Test
    void threadsAreSeparatePerChannelAndChronological() {
        service.post(ticket, ThreadChannel.GUEST, guest, Role.GUEST, "It was loud");
        service.post(ticket, ThreadChannel.GUEST, agent, Role.AGENT, "Looking into it");
        service.post(ticket, ThreadChannel.HOST, agent, Role.AGENT, "Please respond");

        assertEquals(2, service.thread(ticket, ThreadChannel.GUEST).size());
        assertEquals("It was loud", service.thread(ticket, ThreadChannel.GUEST).get(0).body());
        assertEquals(1, service.thread(ticket, ThreadChannel.HOST).size());
        assertTrue(service.thread(UUID.randomUUID(), ThreadChannel.GUEST).isEmpty());
    }

    @Test
    void postedMessageCarriesAuthorAndTimestamp() {
        Message message = service.post(ticket, ThreadChannel.GUEST, agent, Role.AGENT, "Hello");

        assertEquals(Role.AGENT, message.authorRole());
        assertEquals(agent, message.authorId());
        assertEquals(Instant.parse("2026-09-25T04:00:00Z"), message.sentAt());
    }

    @Test
    void rejectsBlankBodiesAndWrongChannelForTheRole() {
        assertThrows(IllegalArgumentException.class,
                () -> service.post(ticket, ThreadChannel.GUEST, agent, Role.AGENT, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> service.post(ticket, ThreadChannel.HOST, guest, Role.GUEST, "wrong thread"));
    }

    @Test
    void returnedThreadCannotBeUsedToMutateTheStore() {
        service.post(ticket, ThreadChannel.GUEST, guest, Role.GUEST, "one");

        assertThrows(UnsupportedOperationException.class,
                () -> service.thread(ticket, ThreadChannel.GUEST).clear());
    }
}
```

- [x] **Step 3: Run to verify it fails** — `.\gradlew test --tests "com.snoozeshare.service.InMemoryMessageServiceTest"` → FAIL (`cannot find symbol: class InMemoryMessageService`).

- [x] **Step 4: Implement**

```java
package com.snoozeshare.service.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.service.MessageService;

/**
 * Temporary, session-only chat. Workstream W13 replaces it with a persistent implementation.
 */
public final class InMemoryMessageService implements MessageService {

    private final Clock clock;
    private final Map<String, List<Message>> threads = new ConcurrentHashMap<>();

    public InMemoryMessageService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<Message> thread(UUID ticketId, ThreadChannel channel) {
        return Collections.unmodifiableList(new ArrayList<>(
                threads.getOrDefault(key(ticketId, channel), List.of())));
    }

    @Override
    public Message post(UUID ticketId, ThreadChannel channel, UUID authorId, Role authorRole,
                        String body) {
        DomainValidation.requireText(body, "body");
        if (authorRole == Role.GUEST && channel != ThreadChannel.GUEST
                || authorRole == Role.HOST && channel != ThreadChannel.HOST) {
            throw new IllegalArgumentException("Author cannot post in this thread");
        }
        Message message = new Message(UUID.randomUUID(), ticketId, channel, authorId, authorRole,
                body.trim(), clock.instant());
        threads.computeIfAbsent(key(ticketId, channel), unused -> new CopyOnWriteArrayList<>())
                .add(message);
        return message;
    }

    private static String key(UUID ticketId, ThreadChannel channel) {
        return ticketId + "|" + channel;
    }
}
```

- [x] **Step 5: Run to verify it passes** — same command → PASS (4 tests).

- [x] **Step 6: Commit**

```powershell
git add src/main src/test
git commit -m "feat: add MessageService contract and temporary in-memory chat (C21)" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Service contracts and DTOs

Compile-only task: the interfaces and records the next tasks implement. Nothing to test yet; the existing suite proves nothing else broke.

**Files:**
- Create: `domain/enums/ResolutionMode.java`, `service/requests/ResolutionRequest.java`, `service/Settlement.java`, `service/DisputeSettlementService.java`, `service/DisputeSummary.java`, `service/DisputeDetail.java`, `service/DisputeQueryService.java`
- Modify: `service/TicketService.java` (full replacement)

- [x] **Step 1: Create the files**

`ResolutionMode.java`:

```java
package com.snoozeshare.domain.enums;

public enum ResolutionMode {
    ACCEPT,
    REJECT,
    MANUAL
}
```

`service/requests/ResolutionRequest.java`:

```java
package com.snoozeshare.service.requests;

import java.math.BigDecimal;

import com.snoozeshare.domain.enums.ResolutionMode;

/**
 * An agent's resolution. guestRefund is required for MANUAL and for ACCEPT of a PARTIAL_REFUND/OTHER
 * request; it is ignored for REJECT and derived for the other ACCEPT remedies.
 */
public record ResolutionRequest(ResolutionMode mode, BigDecimal guestRefund, String reason) {
}
```

`service/Settlement.java`:

```java
package com.snoozeshare.service;

import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.settlement.SettlementBreakdown;

/**
 * The committed outcome of a settlement. Either transaction is null when its amount was zero.
 */
public record Settlement(
        Ticket ticket,
        Booking booking,
        SettlementBreakdown breakdown,
        WalletTransaction guestTransaction,
        WalletTransaction hostTransaction
) {
}
```

`service/DisputeSettlementService.java`:

```java
package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.UUID;

import com.snoozeshare.domain.enums.ResolutionMode;

/**
 * The only class that moves money for agent dispute resolution (C20). One atomic transaction.
 */
public interface DisputeSettlementService {
    Settlement settle(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId,
                      String reason);
}
```

`service/DisputeSummary.java`:

```java
package com.snoozeshare.service;

import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.TicketStatus;

public record DisputeSummary(
        UUID ticketId,
        String ticketLabel,
        String title,
        String category,
        String listingTitle,
        String guestName,
        String hostName,
        String assignedAgentName,
        TicketStatus status,
        Instant createdAt
) {
}
```

`service/DisputeDetail.java`:

```java
package com.snoozeshare.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Ticket;

public record DisputeDetail(
        Ticket ticket,
        String ticketLabel,
        String listingTitle,
        LocalDate startDate,
        LocalDate endDate,
        String guestName,
        String hostName,
        String raisedByName,
        String assignedAgentName,
        BookingStatus bookingStatus,
        BigDecimal escrowAmount,
        boolean escrowHeld,
        String phaseLabel
) {
}
```

`service/DisputeQueryService.java`:

```java
package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.TicketStatus;

/**
 * Read models for the agent dispute screens (controllers may not touch repositories).
 */
public interface DisputeQueryService {
    List<DisputeSummary> queue(TicketStatus status, AssigneeFilter assignee, UUID agentId);

    DisputeDetail detail(UUID ticketId);
}
```

`service/TicketService.java` — replace the whole file:

```java
package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.service.requests.NewTicketRequest;
import com.snoozeshare.service.requests.ResolutionRequest;

public interface TicketService {
    /** Owned by W4 (guest filing). */
    Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole);

    /** Active categories only (what guests see when filing). */
    List<TicketCategory> listCategories();

    List<TicketCategory> listAllCategories();

    TicketCategory createCategory(String label, UUID agentId);

    TicketCategory renameCategory(UUID categoryId, String label, UUID agentId);

    TicketCategory setCategoryActive(UUID categoryId, boolean active, UUID agentId);

    /** Oldest first. */
    List<Ticket> queueForAgent(TicketStatus statusFilter, AssigneeFilter assignee, UUID agentId);

    Ticket assignToMe(UUID ticketId, UUID agentId);

    Ticket addAgentNote(UUID ticketId, String note, UUID agentId);

    /** Superseded by MessageService (C21); not implemented. */
    @Deprecated
    Ticket addHostResponse(UUID ticketId, String responseText, UUID hostId);

    Ticket resolve(UUID ticketId, ResolutionRequest request, UUID agentId);
}
```

- [x] **Step 2: Compile and run the whole suite**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`. (No class implemented `TicketService` before, so nothing else needed changing. If the build reports a class that does, adapt it minimally and note it.)

- [x] **Step 3: Commit**

```powershell
git add src/main
git commit -m "feat: define W10 service contracts (TicketService, settlement, query, DTOs)" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: Atomic dispute settlement

The core of W10. Writes wallets, ledger rows, ticket, booking and audit in **one** transaction; publishes events only after commit.

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/DisputeSettlementServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/SettlementFixtures.java`, `DisputeSettlementServiceTest.java`, `DisputeSettlementAtomicityTest.java`

Reference numbers (mock DB, verified): ticket 3 is `UNDER_REVIEW`, assigned to Ben, requested `FULL_REFUND`, on booking 11 (escrow **210.00**, guest Sophia wallet **790.00**, host Diego wallet **150.00**, status `CONFIRMED`).

- [x] **Step 1: Test support** — `SettlementFixtures.java`

```java
package com.snoozeshare.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.DisputeSettlementServiceImpl;
import com.snoozeshare.testsupport.MockDbFixture;

final class SettlementFixtures {

    static final Instant NOW = Instant.parse("2026-09-25T04:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private SettlementFixtures() {
    }

    static DisputeSettlementServiceImpl settlement(MockDbFixture db, EventBus bus,
                                                    WalletTransactionRepository transactions) {
        var connection = db.connection();
        return new DisputeSettlementServiceImpl(connection, new JdbcTicketRepository(connection),
                new JdbcBookingRepository(connection), new JdbcPropertyRepository(connection),
                new JdbcUserRepository(connection), new JdbcWalletRepository(connection),
                transactions, new AuditServiceImpl(new JdbcAuditLogRepository(connection)), bus, CLOCK);
    }
}
```

- [x] **Step 2: Write the failing settlement test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.infra.events.DomainEvent;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.DisputeSettlementServiceImpl;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeSettlementServiceTest {

    private static final UUID BEN = MockIds.AGENT_BEN;

    private static DisputeSettlementServiceImpl service(MockDbFixture db, InProcessEventBus bus) {
        return SettlementFixtures.settlement(db, bus,
                new JdbcWalletTransactionRepository(db.connection()));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " vs " + actual);
    }

    @Test
    void rejectPaysTheHostInFullNetOfTheFeeAndRefundsNothing(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            InProcessEventBus bus = new InProcessEventBus();
            List<DomainEvent> events = new ArrayList<>();
            bus.subscribe(TicketResolvedEvent.class, events::add);
            bus.subscribe(WalletTransactionRecordedEvent.class, events::add);

            Settlement result = service(db, bus).settle(MockIds.TICKET_3, ResolutionMode.REJECT,
                    BigDecimal.ZERO, BEN, "No evidence of a violation");

            assertNull(result.guestTransaction());
            assertEquals(WalletTransactionType.BOOKING_PAYOUT, result.hostTransaction().type());
            assertMoney("203.70", result.hostTransaction().amount());
            assertMoney("6.30", result.hostTransaction().feeAmount());
            assertMoney("353.70", result.hostTransaction().balanceAfter());
            assertEquals(MockIds.TICKET_3, result.hostTransaction().relatedTicketId());
            assertEquals(BEN, result.hostTransaction().initiatedBy());
            assertMoney("353.70", db.walletBalance(MockIds.WALLET_DIEGO));
            assertMoney("790", db.walletBalance(MockIds.WALLET_SOPHIA));
            assertEquals(TicketStatus.RESOLVED_REJECTED, result.ticket().status());
            assertEquals("No evidence of a violation", result.ticket().resolutionReason());
            assertEquals(SettlementFixtures.NOW, result.ticket().resolvedAt());
            assertEquals(BookingStatus.COMPLETED, result.booking().status());
            assertEquals(SettlementFixtures.NOW, result.booking().completedAt());
            assertEquals("COMPLETED", db.scalarString(
                    "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_11));
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'TICKET_RESOLVED' AND entityId = ?", MockIds.TICKET_3));
            assertEquals(2, events.size());
            db.assertLedgerInvariant();
        }
    }

    @Test
    void acceptOfAFullRefundReturnsTheWholeEscrowToTheGuestWithNoHostRow(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            Settlement result = service(db, new InProcessEventBus()).settle(MockIds.TICKET_3,
                    ResolutionMode.ACCEPT, new BigDecimal("210.00"), BEN, "Host never responded");

            assertNull(result.hostTransaction());
            assertEquals(WalletTransactionType.TICKET_REMEDY, result.guestTransaction().type());
            assertMoney("210", result.guestTransaction().amount());
            assertMoney("0", result.guestTransaction().feeAmount());
            assertMoney("1000", result.guestTransaction().balanceAfter());
            assertMoney("1000", db.walletBalance(MockIds.WALLET_SOPHIA));
            assertMoney("150", db.walletBalance(MockIds.WALLET_DIEGO));
            assertEquals(TicketStatus.RESOLVED_APPROVED, result.ticket().status());
            db.assertLedgerInvariant();
        }
    }

    @Test
    void manualCustomSplitSettlesBothWalletsAndRecordsAnAgentOverride(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            InProcessEventBus bus = new InProcessEventBus();
            List<DomainEvent> events = new ArrayList<>();
            bus.subscribe(TicketResolvedEvent.class, events::add);
            bus.subscribe(WalletTransactionRecordedEvent.class, events::add);

            Settlement result = service(db, bus).settle(MockIds.TICKET_3, ResolutionMode.MANUAL,
                    new BigDecimal("60.00"), BEN, "Partial credit agreed");

            assertEquals(WalletTransactionType.AGENT_OVERRIDE, result.guestTransaction().type());
            assertMoney("60", result.guestTransaction().amount());
            assertMoney("850", result.guestTransaction().balanceAfter());
            assertEquals(WalletTransactionType.BOOKING_PAYOUT, result.hostTransaction().type());
            assertMoney("145.50", result.hostTransaction().amount());
            assertMoney("4.50", result.hostTransaction().feeAmount());
            assertMoney("295.50", result.hostTransaction().balanceAfter());
            assertMoney("850", db.walletBalance(MockIds.WALLET_SOPHIA));
            assertMoney("295.50", db.walletBalance(MockIds.WALLET_DIEGO));
            assertEquals(TicketStatus.RESOLVED_APPROVED, result.ticket().status());
            assertEquals(3, events.size());
            db.assertLedgerInvariant();
        }
    }

    @Test
    void manualFullPayoutIsARejectedTicketThatPaysTheHost(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            Settlement result = service(db, new InProcessEventBus()).settle(MockIds.TICKET_3,
                    ResolutionMode.MANUAL, BigDecimal.ZERO, BEN, "Guest claim unfounded");

            assertNull(result.guestTransaction());
            assertNotNull(result.hostTransaction());
            assertMoney("203.70", result.hostTransaction().amount());
            assertEquals(TicketStatus.RESOLVED_REJECTED, result.ticket().status());
        }
    }

    @Test
    void refundAboveTheEscrowIsRejectedAndNothingChanges(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalArgumentException.class, () -> service.settle(MockIds.TICKET_3,
                    ResolutionMode.MANUAL, new BigDecimal("210.01"), BEN, "too much"));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void aBlankReasonIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalArgumentException.class, () -> service.settle(MockIds.TICKET_3,
                    ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "   "));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void onlyAnAgentAssignedToTheTicketMaySettleIt(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalStateException.class, () -> service.settle(MockIds.TICKET_3,
                    ResolutionMode.REJECT, BigDecimal.ZERO, MockIds.AGENT_AMY, "not mine"));
            assertThrows(IllegalStateException.class, () -> service.settle(MockIds.TICKET_3,
                    ResolutionMode.REJECT, BigDecimal.ZERO, MockIds.HOST_DIEGO, "a host"));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void anAlreadyResolvedTicketCannotBeSettledAgain(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            service.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "first");
            long afterFirst = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalStateException.class, () -> service.settle(MockIds.TICKET_3,
                    ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "second"));

            assertEquals(afterFirst, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"));
        }
    }

    @Test
    void aBookingThatIsNoLongerConfirmedCannotBeSettled(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("UPDATE bookings SET status = 'CANCELLED_BY_HOST' WHERE bookingId = ?",
                    MockIds.BOOKING_11);
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalStateException.class, () -> service.settle(MockIds.TICKET_3,
                    ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "cancelled meanwhile"));

            assertUnchanged(db, before, "CANCELLED_BY_HOST");
        }
    }

    @Test
    void escrowThatWasAlreadyReleasedCannotBeSettled(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("INSERT INTO wallet_transactions (transactionId, walletId, type, amount, "
                    + "feeAmount, balanceAfter, relatedBookingId, relatedTicketId, initiatedBy, "
                    + "createdAt) VALUES ('f0000000-0000-0000-0000-000000000001', ?, "
                    + "'ESCROW_REFUND', 0.01, NULL, 790.01, ?, NULL, NULL, '2026-09-24T00:00:00')",
                    MockIds.WALLET_SOPHIA, MockIds.BOOKING_11);
            db.execute("UPDATE wallets SET balance = 790.01 WHERE walletId = ?", MockIds.WALLET_SOPHIA);
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalStateException.class, () -> service.settle(MockIds.TICKET_3,
                    ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "already refunded"));

            assertUnchanged(db, before, "CONFIRMED");
        }
    }

    @Test
    void anUnknownTicketIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeSettlementServiceImpl service = service(db, new InProcessEventBus());

            assertThrows(IllegalArgumentException.class, () -> service.settle(UUID.randomUUID(),
                    ResolutionMode.REJECT, BigDecimal.ZERO, BEN, "who?"));
        }
    }

    private static void assertUnchanged(MockDbFixture db, long transactionCount, String bookingStatus)
            throws Exception {
        assertEquals(transactionCount, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"));
        assertEquals("UNDER_REVIEW", db.scalarString(
                "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
        assertEquals(bookingStatus, db.scalarString(
                "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_11));
        assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                + "WHERE actionType = 'TICKET_RESOLVED' AND entityId = ?", MockIds.TICKET_3));
    }
}
```

- [x] **Step 3: Run to verify it fails** — `.\gradlew test --tests "com.snoozeshare.service.DisputeSettlementServiceTest"` → FAIL (`cannot find symbol: class DisputeSettlementServiceImpl`).

- [x] **Step 4: Implement**

```java
package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.domain.settlement.EscrowPolicy;
import com.snoozeshare.domain.settlement.SettlementBreakdown;
import com.snoozeshare.domain.settlement.SettlementCalculator;
import com.snoozeshare.domain.statemachine.BookingStateMachine;
import com.snoozeshare.domain.statemachine.TicketStateMachine;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.infra.db.TransactionManager;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.WalletRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.DisputeSettlementService;
import com.snoozeshare.service.Settlement;

/**
 * Settles the whole held escrow when an agent resolves a dispute (C17, C20). Everything is written in one
 * transaction; wallets are updated directly (not through WalletLedgerWriter, which opens its own
 * transaction and treats the fee as a deduction) because feeAmount is informational on payout rows.
 */
public final class DisputeSettlementServiceImpl implements DisputeSettlementService {

    private final Connection connection;
    private final TicketRepository tickets;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final AuditService audit;
    private final EventBus eventBus;
    private final Clock clock;

    public DisputeSettlementServiceImpl(Connection connection, TicketRepository tickets,
                                        BookingRepository bookings, PropertyRepository properties,
                                        UserRepository users, WalletRepository wallets,
                                        WalletTransactionRepository transactions, AuditService audit,
                                        EventBus eventBus, Clock clock) {
        this.connection = connection;
        this.tickets = tickets;
        this.bookings = bookings;
        this.properties = properties;
        this.users = users;
        this.wallets = wallets;
        this.transactions = transactions;
        this.audit = audit;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    @Override
    public Settlement settle(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId,
                             String reason) {
        DomainValidation.requireText(reason, "reason");
        User agent = agentId == null ? null : users.findById(agentId).orElse(null);
        AuthorizationService.requireRole(Role.AGENT, agent);
        Instant now = clock.instant();
        Settlement settlement;
        try {
            settlement = new TransactionManager(connection).inTransaction(
                    current -> apply(ticketId, mode, guestRefund, agentId, reason.trim(), now));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to settle dispute", exception);
        }
        eventBus.publish(new TicketResolvedEvent(ticketId, agentId, now));
        publish(settlement.guestTransaction());
        publish(settlement.hostTransaction());
        return settlement;
    }

    private Settlement apply(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId,
                             String reason, Instant now) {
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket does not exist"));
        if (ticket.status() != TicketStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Ticket is not under review");
        }
        if (!agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is not assigned to this agent");
        }
        Booking booking = bookings.findById(ticket.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        if (booking.status() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking is not confirmed");
        }
        if (!EscrowPolicy.isHeld(transactions.findByBookingId(booking.bookingId()))) {
            throw new IllegalStateException("Escrow is not held for this booking");
        }
        SettlementBreakdown split = SettlementCalculator.split(booking.totalAmount(), guestRefund);
        TicketStatus resolved = statusFor(mode, split);
        if (!TicketStateMachine.canTransition(ticket.status(), resolved, Role.AGENT)
                || !BookingStateMachine.canTransition(booking.status(), BookingStatus.COMPLETED,
                        Role.AGENT)) {
            throw new IllegalStateException("Transition is not allowed");
        }
        Wallet guestWallet = wallets.findByUserId(booking.guestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest wallet does not exist"));
        Property property = properties.findById(booking.listingId())
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
        Wallet hostWallet = wallets.findByUserId(property.hostId())
                .orElseThrow(() -> new IllegalArgumentException("Host wallet does not exist"));

        WalletTransaction guestTransaction = null;
        WalletTransaction hostTransaction = null;
        if (split.guestRefund().signum() > 0) {
            WalletTransactionType type = mode == ResolutionMode.MANUAL
                    ? WalletTransactionType.AGENT_OVERRIDE : WalletTransactionType.TICKET_REMEDY;
            guestTransaction = credit(guestWallet, type, split.guestRefund(), BigDecimal.ZERO, booking,
                    ticket, agentId, now);
        }
        if (split.hostGross().signum() > 0) {
            hostTransaction = credit(hostWallet, WalletTransactionType.BOOKING_PAYOUT, split.hostNet(),
                    split.fee(), booking, ticket, agentId, now);
        }

        Ticket updatedTicket = tickets.save(new Ticket(ticket.ticketId(), ticket.bookingId(),
                ticket.raisedByUserId(), ticket.raisedByRole(), ticket.category(), ticket.title(),
                ticket.description(), ticket.requestedRemedy(), ticket.supportingText(), resolved,
                ticket.assignedAgentId(), ticket.agentNotes(), reason, ticket.createdAt(), now));
        Booking updatedBooking = bookings.save(new Booking(booking.bookingId(), booking.listingId(),
                booking.guestId(), booking.startDate(), booking.endDate(), BookingStatus.COMPLETED,
                booking.nightlyRateSnapshot(), booking.totalAmount(), booking.createdAt(),
                booking.decidedAt(), now));
        audit.record(agentId, "TICKET_RESOLVED", "Ticket", ticketId,
                json("status", ticket.status().name()),
                json("status", resolved.name(), "mode", mode.name(),
                        "guestRefund", split.guestRefund().toPlainString(),
                        "hostPayout", split.hostNet().toPlainString(),
                        "fee", split.fee().toPlainString(), "reason", reason));
        return new Settlement(updatedTicket, updatedBooking, split, guestTransaction, hostTransaction);
    }

    private WalletTransaction credit(Wallet wallet, WalletTransactionType type, BigDecimal amount,
                                     BigDecimal fee, Booking booking, Ticket ticket, UUID agentId,
                                     Instant now) {
        BigDecimal balanceAfter = wallet.balance().add(amount);
        wallets.save(new Wallet(wallet.walletId(), wallet.userId(), balanceAfter, wallet.currency(), now));
        return transactions.save(new WalletTransaction(UUID.randomUUID(), wallet.walletId(), type,
                amount, fee, balanceAfter, booking.bookingId(), ticket.ticketId(), agentId, now));
    }

    private static TicketStatus statusFor(ResolutionMode mode, SettlementBreakdown split) {
        return switch (mode) {
            case ACCEPT -> TicketStatus.RESOLVED_APPROVED;
            case REJECT -> TicketStatus.RESOLVED_REJECTED;
            case MANUAL -> split.guestRefund().signum() > 0
                    ? TicketStatus.RESOLVED_APPROVED : TicketStatus.RESOLVED_REJECTED;
        };
    }

    private void publish(WalletTransaction transaction) {
        if (transaction != null) {
            eventBus.publish(new WalletTransactionRecordedEvent(transaction.transactionId(),
                    transaction.walletId(), transaction.createdAt()));
        }
    }

    private static String json(String... pairs) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(pairs[i]).append("\":\"")
                    .append(pairs[i + 1].replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return builder.append('}').toString();
    }
}
```

- [x] **Step 5: Run to verify it passes** — `.\gradlew test --tests "com.snoozeshare.service.DisputeSettlementServiceTest"` → PASS (10 tests). If money assertions are off by a cent, re-check `SettlementCalculator`; if `walletBalance` differs in scale, the test uses `compareTo` already.

- [x] **Step 6: Write the failing atomicity test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.events.DomainEvent;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeSettlementAtomicityTest {

    /** Delegates to the real repository but fails on the Nth save, simulating a mid-flight crash. */
    private static final class FailingOnSave implements WalletTransactionRepository {
        private final WalletTransactionRepository delegate;
        private final int failingSave;
        private int saves;

        FailingOnSave(WalletTransactionRepository delegate, int failingSave) {
            this.delegate = delegate;
            this.failingSave = failingSave;
        }

        @Override
        public WalletTransaction save(WalletTransaction transaction) {
            saves++;
            if (saves == failingSave) {
                throw new IllegalStateException("Injected failure");
            }
            return delegate.save(transaction);
        }

        @Override
        public List<WalletTransaction> findByWalletId(UUID walletId) {
            return delegate.findByWalletId(walletId);
        }

        @Override
        public List<WalletTransaction> findByBookingId(UUID bookingId) {
            return delegate.findByBookingId(bookingId);
        }
    }

    @Test
    void aFailureAfterTheGuestRowRollsEverythingBackAndPublishesNothing(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            InProcessEventBus bus = new InProcessEventBus();
            List<DomainEvent> events = new ArrayList<>();
            bus.subscribe(TicketResolvedEvent.class, events::add);
            bus.subscribe(WalletTransactionRecordedEvent.class, events::add);
            var service = SettlementFixtures.settlement(db, bus,
                    new FailingOnSave(new JdbcWalletTransactionRepository(db.connection()), 2));
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalStateException.class, () -> service.settle(MockIds.TICKET_3,
                    ResolutionMode.MANUAL, new BigDecimal("60.00"), MockIds.AGENT_BEN, "split"));

            assertEquals(before, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"));
            assertEquals(0, new BigDecimal("790").compareTo(db.walletBalance(MockIds.WALLET_SOPHIA)));
            assertEquals(0, new BigDecimal("150").compareTo(db.walletBalance(MockIds.WALLET_DIEGO)));
            assertEquals("UNDER_REVIEW", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
            assertEquals("CONFIRMED", db.scalarString(
                    "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_11));
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM audit_log "
                    + "WHERE actionType = 'TICKET_RESOLVED' AND entityId = ?", MockIds.TICKET_3));
            assertTrue(events.isEmpty());
            db.assertLedgerInvariant();
        }
    }

    @Test
    void theConnectionIsUsableAfterARollback(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            var failing = SettlementFixtures.settlement(db, new InProcessEventBus(),
                    new FailingOnSave(new JdbcWalletTransactionRepository(db.connection()), 1));
            assertThrows(IllegalStateException.class, () -> failing.settle(MockIds.TICKET_3,
                    ResolutionMode.REJECT, BigDecimal.ZERO, MockIds.AGENT_BEN, "boom"));

            var healthy = SettlementFixtures.settlement(db, new InProcessEventBus(),
                    new JdbcWalletTransactionRepository(db.connection()));
            healthy.settle(MockIds.TICKET_3, ResolutionMode.REJECT, BigDecimal.ZERO,
                    MockIds.AGENT_BEN, "retry succeeds");

            assertEquals("RESOLVED_REJECTED", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_3));
            db.assertLedgerInvariant();
        }
    }
}
```

- [x] **Step 7: Run** — `.\gradlew test --tests "com.snoozeshare.service.DisputeSettlementAtomicityTest"` → PASS (the implementation already provides atomicity; this test is the proof). If it FAILS with a partial write, the transaction is not spanning all repositories — check that every repository in `SettlementFixtures` was built from the same `db.connection()`.

- [x] **Step 8: Commit**

```powershell
git add src/main src/test
git commit -m "feat: atomic full-escrow dispute settlement (C17, C20, C23)" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: `TicketServiceImpl` — queue, assign, notes, resolve

Unit tests use in-memory fakes (test type "Unit — service (fakes)"); an integration test on the mock DB follows in Step 8.

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/TicketServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/testsupport/Fakes.java`, `src/test/java/com/snoozeshare/service/TicketServiceTest.java`, `TicketServiceIntegrationTest.java`

- [x] **Step 1: Test fakes** — `Fakes.java`

```java
package com.snoozeshare.testsupport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.repository.TicketCategoryRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.DisputeSettlementService;
import com.snoozeshare.service.Settlement;
import com.snoozeshare.domain.model.AuditLogEntry;

/**
 * Small in-memory stand-ins so service unit tests need no database.
 */
public final class Fakes {

    private Fakes() {
    }

    public static final class InMemoryTickets implements TicketRepository {
        private final Map<UUID, Ticket> store = new HashMap<>();

        @Override
        public Optional<Ticket> findById(UUID ticketId) {
            return Optional.ofNullable(store.get(ticketId));
        }

        @Override
        public List<Ticket> findByStatus(TicketStatus status) {
            return findQueue(status, AssigneeFilter.ALL, null);
        }

        @Override
        public List<Ticket> findQueue(TicketStatus status, AssigneeFilter assignee, UUID agentId) {
            return store.values().stream()
                    .filter(t -> status == null || t.status() == status)
                    .filter(t -> assignee == null || assignee == AssigneeFilter.ALL
                            || assignee == AssigneeFilter.UNASSIGNED && t.assignedAgentId() == null
                            || assignee == AssigneeFilter.MINE && agentId.equals(t.assignedAgentId()))
                    .sorted(Comparator.comparing(Ticket::createdAt))
                    .toList();
        }

        @Override
        public Ticket save(Ticket ticket) {
            store.put(ticket.ticketId(), ticket);
            return ticket;
        }
    }

    public static final class InMemoryCategories implements TicketCategoryRepository {
        private final Map<UUID, TicketCategory> store = new HashMap<>();

        @Override
        public List<TicketCategory> findActive() {
            return findAll().stream().filter(TicketCategory::active).toList();
        }

        @Override
        public List<TicketCategory> findAll() {
            return store.values().stream()
                    .sorted(Comparator.comparing(c -> c.label().toLowerCase())).toList();
        }

        @Override
        public Optional<TicketCategory> findById(UUID categoryId) {
            return Optional.ofNullable(store.get(categoryId));
        }

        @Override
        public boolean labelInUse(String label, UUID excludeCategoryId) {
            return store.values().stream().anyMatch(c -> c.label().equalsIgnoreCase(label)
                    && !c.categoryId().equals(excludeCategoryId));
        }

        @Override
        public TicketCategory save(TicketCategory category) {
            store.put(category.categoryId(), category);
            return category;
        }
    }

    public static final class StubUsers implements UserRepository {
        private final Map<UUID, User> store = new HashMap<>();

        public StubUsers with(User user) {
            store.put(user.userId(), user);
            return this;
        }

        @Override
        public Optional<User> findById(UUID userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return store.values().stream().filter(u -> u.email().equals(email)).findFirst();
        }

        @Override
        public List<User> findByRole(Role role) {
            return store.values().stream().filter(u -> u.role() == role).toList();
        }

        @Override
        public User save(User user) {
            store.put(user.userId(), user);
            return user;
        }
    }

    public static final class RecordingAudit implements AuditService {
        private final List<String> actions = new ArrayList<>();

        public List<String> actions() {
            return actions;
        }

        @Override
        public void record(UUID actorId, String actionType, String entityType, UUID entityId,
                           Object before, Object after) {
            actions.add(actionType);
        }

        @Override
        public List<AuditLogEntry> query(UUID userId, UUID bookingId, String actionType) {
            return List.of();
        }
    }

    public static final class RecordingSettlement implements DisputeSettlementService {
        private ResolutionMode mode;
        private BigDecimal refund;
        private String reason;
        private int calls;

        public ResolutionMode mode() {
            return mode;
        }

        public BigDecimal refund() {
            return refund;
        }

        public String reason() {
            return reason;
        }

        public int calls() {
            return calls;
        }

        @Override
        public Settlement settle(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId,
                                 String reason) {
            this.calls++;
            this.mode = mode;
            this.refund = guestRefund;
            this.reason = reason;
            return new Settlement(null, null, null, null, null);
        }
    }
}
```

Fix the import order when saving: put `com.snoozeshare.domain.model.AuditLogEntry` with the other `domain.model` imports (alphabetical: `AuditLogEntry`, `Ticket`, `TicketCategory`, `User`).

- [x] **Step 2: Write the failing unit test** — `TicketServiceTest.java`

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.service.impl.TicketServiceImpl;
import com.snoozeshare.service.requests.ResolutionRequest;
import com.snoozeshare.testsupport.Fakes;

class TicketServiceTest {

    private final Fakes.InMemoryTickets tickets = new Fakes.InMemoryTickets();
    private final Fakes.InMemoryCategories categories = new Fakes.InMemoryCategories();
    private final Fakes.RecordingAudit audit = new Fakes.RecordingAudit();
    private final Fakes.RecordingSettlement settlement = new Fakes.RecordingSettlement();
    private final UUID amy = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();
    private final UUID host = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private TicketService service;

    private static User user(UUID id, Role role, String name) {
        return new User(id, role, name, name + "@x.test", AccountStatus.ACTIVE, null,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static Booking booking(UUID id) {
        return new Booking(id, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 4), BookingStatus.CONFIRMED, new BigDecimal("70.00"),
                new BigDecimal("210.00"), Instant.parse("2026-08-25T10:00:00Z"), null, null);
    }

    private Ticket ticket(TicketStatus status, UUID assigned, RemedyType remedy, String createdAt) {
        return tickets.save(new Ticket(UUID.randomUUID(), bookingId, UUID.randomUUID(), Role.GUEST,
                "Cleanliness", "title", "description", remedy, null, status, assigned, null, null,
                Instant.parse(createdAt), null));
    }

    @BeforeEach
    void setUp() {
        var users = new Fakes.StubUsers().with(user(amy, Role.AGENT, "Amy"))
                .with(user(ben, Role.AGENT, "Ben")).with(user(host, Role.HOST, "Hugo"));
        BookingRepository bookings = new BookingRepository() {
            @Override
            public Optional<Booking> findById(UUID id) {
                return Optional.of(booking(id));
            }

            @Override
            public List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end) {
                return List.of();
            }

            @Override
            public List<Booking> findByGuest(UUID guestId) {
                return List.of();
            }

            @Override
            public List<Booking> findByHostPending(UUID hostId) {
                return List.of();
            }

            @Override
            public Booking save(Booking booking) {
                return booking;
            }
        };
        service = new TicketServiceImpl(tickets, categories, bookings, users, settlement, audit,
                Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void queueIsOldestFirstAndFiltersByAssignee() {
        Ticket newer = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-10T00:00:00Z");
        Ticket older = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-01T00:00:00Z");
        Ticket mine = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.OTHER, "2026-09-05T00:00:00Z");

        assertEquals(List.of(older.ticketId(), mine.ticketId(), newer.ticketId()), ids(
                service.queueForAgent(null, AssigneeFilter.ALL, amy)));
        assertEquals(List.of(older.ticketId(), newer.ticketId()), ids(
                service.queueForAgent(null, AssigneeFilter.UNASSIGNED, amy)));
        assertEquals(List.of(mine.ticketId()), ids(
                service.queueForAgent(null, AssigneeFilter.MINE, amy)));
        assertEquals(List.of(mine.ticketId()), ids(
                service.queueForAgent(TicketStatus.UNDER_REVIEW, AssigneeFilter.ALL, amy)));
    }

    @Test
    void assignToMeMovesAnOpenTicketUnderReviewAndAuditsIt() {
        Ticket open = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-01T00:00:00Z");

        Ticket assigned = service.assignToMe(open.ticketId(), amy);

        assertEquals(TicketStatus.UNDER_REVIEW, assigned.status());
        assertEquals(amy, assigned.assignedAgentId());
        assertEquals(List.of("TICKET_ASSIGNED"), audit.actions());
    }

    @Test
    void assignRejectsNonAgentsNonOpenTicketsAndTicketsTakenByAnotherAgent() {
        Ticket open = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-01T00:00:00Z");
        Ticket taken = ticket(TicketStatus.OPEN, ben, RemedyType.OTHER, "2026-09-02T00:00:00Z");
        Ticket reviewing = ticket(TicketStatus.UNDER_REVIEW, ben, RemedyType.OTHER, "2026-09-03T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> service.assignToMe(open.ticketId(), host));
        assertThrows(IllegalStateException.class, () -> service.assignToMe(taken.ticketId(), amy));
        assertThrows(IllegalStateException.class, () -> service.assignToMe(reviewing.ticketId(), amy));
        assertThrows(IllegalArgumentException.class, () -> service.assignToMe(UUID.randomUUID(), amy));
        assertTrue(audit.actions().isEmpty());
    }

    @Test
    void notesAreAppendedWithAuthorAndTimestampOnlyByTheAssignedAgent() {
        Ticket mine = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.OTHER, "2026-09-01T00:00:00Z");

        service.addAgentNote(mine.ticketId(), "Requested photos", amy);
        Ticket updated = service.addAgentNote(mine.ticketId(), "Photos received", amy);

        assertEquals("[2026-09-25T04:00:00Z] Amy: Requested photos\n"
                + "[2026-09-25T04:00:00Z] Amy: Photos received", updated.agentNotes());
        assertThrows(IllegalStateException.class, () -> service.addAgentNote(mine.ticketId(), "x", ben));
        assertThrows(IllegalArgumentException.class, () -> service.addAgentNote(mine.ticketId(), " ", amy));
    }

    @Test
    void notesRequireAnUnderReviewTicket() {
        Ticket open = ticket(TicketStatus.OPEN, null, RemedyType.OTHER, "2026-09-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> service.addAgentNote(open.ticketId(), "x", amy));
    }

    @Test
    void acceptDerivesTheRefundFromTheRequestedRemedy() {
        Ticket full = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.FULL_REFUND, "2026-09-01T00:00:00Z");
        Ticket payout = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.HOST_PAYOUT, "2026-09-01T00:00:00Z");

        service.resolve(full.ticketId(), new ResolutionRequest(ResolutionMode.ACCEPT, null, "ok"), amy);
        assertEquals(0, new BigDecimal("210.00").compareTo(settlement.refund()));
        assertEquals(ResolutionMode.ACCEPT, settlement.mode());

        service.resolve(payout.ticketId(), new ResolutionRequest(ResolutionMode.ACCEPT, null, "ok"), amy);
        assertEquals(0, BigDecimal.ZERO.compareTo(settlement.refund()));
    }

    @Test
    void acceptOfAPartialOrOtherRequestNeedsAnAmount() {
        Ticket partial = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.PARTIAL_REFUND, "2026-09-01T00:00:00Z");
        Ticket other = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.OTHER, "2026-09-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> service.resolve(partial.ticketId(),
                new ResolutionRequest(ResolutionMode.ACCEPT, null, "ok"), amy));
        assertEquals(0, settlement.calls());

        service.resolve(other.ticketId(),
                new ResolutionRequest(ResolutionMode.ACCEPT, new BigDecimal("50"), "ok"), amy);
        assertEquals(0, new BigDecimal("50").compareTo(settlement.refund()));
    }

    @Test
    void rejectAlwaysRefundsNothingAndManualNeedsAnAmount() {
        Ticket t = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.FULL_REFUND, "2026-09-01T00:00:00Z");

        service.resolve(t.ticketId(),
                new ResolutionRequest(ResolutionMode.REJECT, new BigDecimal("99"), "no"), amy);
        assertEquals(0, BigDecimal.ZERO.compareTo(settlement.refund()));
        assertEquals("no", settlement.reason());

        assertThrows(IllegalArgumentException.class, () -> service.resolve(t.ticketId(),
                new ResolutionRequest(ResolutionMode.MANUAL, null, "x"), amy));
        service.resolve(t.ticketId(),
                new ResolutionRequest(ResolutionMode.MANUAL, new BigDecimal("12.34"), "custom"), amy);
        assertEquals(0, new BigDecimal("12.34").compareTo(settlement.refund()));
    }

    @Test
    void resolveRejectsNonAgents() {
        Ticket t = ticket(TicketStatus.UNDER_REVIEW, amy, RemedyType.FULL_REFUND, "2026-09-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> service.resolve(t.ticketId(),
                new ResolutionRequest(ResolutionMode.REJECT, null, "x"), host));
        assertEquals(0, settlement.calls());
    }

    @Test
    void fileTicketAndHostResponseAreNotOwnedByW10() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.fileTicket(null, host, Role.HOST));
        assertThrows(UnsupportedOperationException.class,
                () -> service.addHostResponse(UUID.randomUUID(), "x", host));
    }

    private static List<UUID> ids(List<Ticket> list) {
        return list.stream().map(Ticket::ticketId).toList();
    }
}
```

Wrap any line over 120 columns when saving (several `ticket(...)` calls here are close to the limit).

- [x] **Step 3: Run to verify it fails** — `.\gradlew test --tests "com.snoozeshare.service.TicketServiceTest"` → FAIL (`cannot find symbol: class TicketServiceImpl`).

- [x] **Step 4: Implement `TicketServiceImpl`** (categories included — Task 10 only adds tests for them)

```java
package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.statemachine.TicketStateMachine;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.TicketCategoryRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.DisputeSettlementService;
import com.snoozeshare.service.TicketService;
import com.snoozeshare.service.requests.NewTicketRequest;
import com.snoozeshare.service.requests.ResolutionRequest;

public final class TicketServiceImpl implements TicketService {

    private final TicketRepository tickets;
    private final TicketCategoryRepository categories;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final DisputeSettlementService settlement;
    private final AuditService audit;
    private final Clock clock;

    public TicketServiceImpl(TicketRepository tickets, TicketCategoryRepository categories,
                             BookingRepository bookings, UserRepository users,
                             DisputeSettlementService settlement, AuditService audit, Clock clock) {
        this.tickets = tickets;
        this.categories = categories;
        this.bookings = bookings;
        this.users = users;
        this.settlement = settlement;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole) {
        throw new UnsupportedOperationException("Owned by W4");
    }

    @Override
    public List<TicketCategory> listCategories() {
        return categories.findActive();
    }

    @Override
    public List<TicketCategory> listAllCategories() {
        return categories.findAll();
    }

    @Override
    public TicketCategory createCategory(String label, UUID agentId) {
        requireAgent(agentId);
        String clean = DomainValidation.requireText(label, "label").trim();
        if (categories.labelInUse(clean, null)) {
            throw new IllegalArgumentException("Category label already exists");
        }
        TicketCategory created = categories.save(new TicketCategory(UUID.randomUUID(), clean, true));
        audit.record(agentId, "TICKET_CATEGORY_CREATED", "TicketCategory", created.categoryId(), null,
                created);
        return created;
    }

    @Override
    public TicketCategory renameCategory(UUID categoryId, String label, UUID agentId) {
        requireAgent(agentId);
        TicketCategory existing = loadCategory(categoryId);
        String clean = DomainValidation.requireText(label, "label").trim();
        if (categories.labelInUse(clean, categoryId)) {
            throw new IllegalArgumentException("Category label already exists");
        }
        TicketCategory renamed = categories.save(
                new TicketCategory(categoryId, clean, existing.active()));
        audit.record(agentId, "TICKET_CATEGORY_RENAMED", "TicketCategory", categoryId, existing, renamed);
        return renamed;
    }

    @Override
    public TicketCategory setCategoryActive(UUID categoryId, boolean active, UUID agentId) {
        requireAgent(agentId);
        TicketCategory existing = loadCategory(categoryId);
        TicketCategory updated = categories.save(
                new TicketCategory(categoryId, existing.label(), active));
        audit.record(agentId, "TICKET_CATEGORY_TOGGLED", "TicketCategory", categoryId, existing, updated);
        return updated;
    }

    @Override
    public List<Ticket> queueForAgent(TicketStatus statusFilter, AssigneeFilter assignee, UUID agentId) {
        return tickets.findQueue(statusFilter, assignee, agentId);
    }

    @Override
    public Ticket assignToMe(UUID ticketId, UUID agentId) {
        requireAgent(agentId);
        Ticket ticket = loadTicket(ticketId);
        if (ticket.status() != TicketStatus.OPEN || !TicketStateMachine.canTransition(
                ticket.status(), TicketStatus.UNDER_REVIEW, Role.AGENT)) {
            throw new IllegalStateException("Ticket is not open");
        }
        if (ticket.assignedAgentId() != null && !agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is already assigned to another agent");
        }
        Ticket updated = tickets.save(with(ticket, TicketStatus.UNDER_REVIEW, agentId,
                ticket.agentNotes()));
        audit.record(agentId, "TICKET_ASSIGNED", "Ticket", ticketId, ticket.status(), updated.status());
        return updated;
    }

    @Override
    public Ticket addAgentNote(UUID ticketId, String note, UUID agentId) {
        User agent = requireAgent(agentId);
        String text = DomainValidation.requireText(note, "note").trim();
        Ticket ticket = loadTicket(ticketId);
        if (ticket.status() != TicketStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Notes can only be added while the ticket is under review");
        }
        if (!agentId.equals(ticket.assignedAgentId())) {
            throw new IllegalStateException("Ticket is not assigned to this agent");
        }
        String entry = "[" + clock.instant() + "] " + agent.displayName() + ": " + text;
        String notes = ticket.agentNotes() == null || ticket.agentNotes().isBlank()
                ? entry : ticket.agentNotes() + "\n" + entry;
        Ticket updated = tickets.save(with(ticket, ticket.status(), ticket.assignedAgentId(), notes));
        audit.record(agentId, "TICKET_NOTE_ADDED", "Ticket", ticketId, null, entry);
        return updated;
    }

    @Override
    @Deprecated
    public Ticket addHostResponse(UUID ticketId, String responseText, UUID hostId) {
        throw new UnsupportedOperationException("Superseded by MessageService");
    }

    @Override
    public Ticket resolve(UUID ticketId, ResolutionRequest request, UUID agentId) {
        requireAgent(agentId);
        Ticket ticket = loadTicket(ticketId);
        Booking booking = bookings.findById(ticket.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        BigDecimal refund = switch (request.mode()) {
            case REJECT -> BigDecimal.ZERO;
            case MANUAL -> requireAmount(request.guestRefund());
            case ACCEPT -> switch (ticket.requestedRemedy()) {
                case FULL_REFUND -> booking.totalAmount();
                case HOST_PAYOUT -> BigDecimal.ZERO;
                case PARTIAL_REFUND, OTHER -> requireAmount(request.guestRefund());
            };
        };
        return settlement.settle(ticketId, request.mode(), refund, agentId, request.reason()).ticket();
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Guest refund is required");
        }
        return amount;
    }

    private User requireAgent(UUID agentId) {
        User user = agentId == null ? null : users.findById(agentId).orElse(null);
        AuthorizationService.requireRole(Role.AGENT, user);
        return user;
    }

    private Ticket loadTicket(UUID ticketId) {
        return tickets.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket does not exist"));
    }

    private TicketCategory loadCategory(UUID categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category does not exist"));
    }

    private static Ticket with(Ticket t, TicketStatus status, UUID assignedAgentId, String agentNotes) {
        return new Ticket(t.ticketId(), t.bookingId(), t.raisedByUserId(), t.raisedByRole(), t.category(),
                t.title(), t.description(), t.requestedRemedy(), t.supportingText(), status,
                assignedAgentId, agentNotes, t.resolutionReason(), t.createdAt(), t.resolvedAt());
    }
}
```

- [x] **Step 5: Run to verify it passes** — `.\gradlew test --tests "com.snoozeshare.service.TicketServiceTest"` → PASS (10 tests).

- [x] **Step 6: Write the failing integration test** — `TicketServiceIntegrationTest.java`

Ticket 2 (`OPEN`, unassigned, requested `OTHER`) is on booking 9: escrow **875.00**, guest Aria wallet **525.00**, host Priya wallet **0.00**. Accepting with a 175.00 refund gives host gross 700.00, fee 21.00, net **679.00**.

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketCategoryRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.TicketServiceImpl;
import com.snoozeshare.service.requests.ResolutionRequest;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class TicketServiceIntegrationTest {

    private static TicketServiceImpl service(MockDbFixture db) {
        var connection = db.connection();
        var settlement = SettlementFixtures.settlement(db, new InProcessEventBus(),
                new JdbcWalletTransactionRepository(connection));
        return new TicketServiceImpl(new JdbcTicketRepository(connection),
                new JdbcTicketCategoryRepository(connection), new JdbcBookingRepository(connection),
                new JdbcUserRepository(connection), settlement,
                new AuditServiceImpl(new JdbcAuditLogRepository(connection)), SettlementFixtures.CLOCK);
    }

    @Test
    void assignNoteAndAcceptAPartialRefundEndToEndOnTheMockDatabase(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            TicketServiceImpl service = service(db);

            Ticket assigned = service.assignToMe(MockIds.TICKET_2, MockIds.AGENT_AMY);
            assertEquals(TicketStatus.UNDER_REVIEW, assigned.status());
            service.addAgentNote(MockIds.TICKET_2, "Requested gate photos", MockIds.AGENT_AMY);

            Ticket resolved = service.resolve(MockIds.TICKET_2,
                    new ResolutionRequest(ResolutionMode.ACCEPT, new BigDecimal("175.00"),
                            "Gate was locked"), MockIds.AGENT_AMY);

            assertEquals(TicketStatus.RESOLVED_APPROVED, resolved.status());
            assertTrue(resolved.agentNotes().contains("Requested gate photos"));
            assertEquals(0, new BigDecimal("700").compareTo(db.walletBalance(MockIds.WALLET_ARIA)));
            assertEquals(0, new BigDecimal("679").compareTo(db.walletBalance(MockIds.WALLET_PRIYA)));
            assertEquals("COMPLETED", db.scalarString(
                    "SELECT status FROM bookings WHERE bookingId = ?", MockIds.BOOKING_9));
            db.assertLedgerInvariant();
        }
    }

    @Test
    void acceptingAnOtherRequestWithoutAnAmountChangesNothing(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            TicketServiceImpl service = service(db);
            service.assignToMe(MockIds.TICKET_2, MockIds.AGENT_AMY);
            long before = db.scalarLong("SELECT COUNT(*) FROM wallet_transactions");

            assertThrows(IllegalArgumentException.class, () -> service.resolve(MockIds.TICKET_2,
                    new ResolutionRequest(ResolutionMode.ACCEPT, null, "no amount"),
                    MockIds.AGENT_AMY));

            assertEquals(before, db.scalarLong("SELECT COUNT(*) FROM wallet_transactions"));
            assertEquals("UNDER_REVIEW", db.scalarString(
                    "SELECT status FROM tickets WHERE ticketId = ?", MockIds.TICKET_2));
        }
    }

    @Test
    void anotherAgentCannotTakeOrResolveATicketAlreadyUnderReview(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            TicketServiceImpl service = service(db);

            assertThrows(IllegalStateException.class,
                    () -> service.assignToMe(MockIds.TICKET_3, MockIds.AGENT_AMY));
            assertThrows(IllegalStateException.class, () -> service.resolve(MockIds.TICKET_3,
                    new ResolutionRequest(ResolutionMode.REJECT, null, "not mine"), MockIds.AGENT_AMY));
        }
    }
}
```

- [x] **Step 7: Run** — `.\gradlew test --tests "com.snoozeshare.service.TicketServiceIntegrationTest"` → PASS.

- [x] **Step 8: Commit**

```powershell
git add src/main src/test
git commit -m "feat: add TicketServiceImpl (queue, assign, notes, resolve, categories)" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 10: Category administration tests

`TicketServiceImpl` already implements the category methods (Task 9). This task pins their behaviour with tests (TDD would have written them first; if you prefer strict order, write this test, watch it fail by temporarily throwing from a method, then restore).

**Files:**
- Test: `src/test/java/com/snoozeshare/service/TicketServiceCategoryTest.java`

- [x] **Step 1: Write the test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.service.impl.TicketServiceImpl;
import com.snoozeshare.testsupport.Fakes;

class TicketServiceCategoryTest {

    private final UUID agent = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();
    private final Fakes.RecordingAudit audit = new Fakes.RecordingAudit();
    private final TicketService service = new TicketServiceImpl(new Fakes.InMemoryTickets(),
            new Fakes.InMemoryCategories(), null,
            new Fakes.StubUsers()
                    .with(new User(agent, Role.AGENT, "Amy", "amy@x.test", AccountStatus.ACTIVE, null,
                            Instant.parse("2026-01-01T00:00:00Z")))
                    .with(new User(guest, Role.GUEST, "Gus", "gus@x.test", AccountStatus.ACTIVE, null,
                            Instant.parse("2026-01-01T00:00:00Z"))),
            new Fakes.RecordingSettlement(), audit,
            Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC));

    @Test
    void createTrimsTheLabelAndMakesTheCategoryActive() {
        TicketCategory created = service.createCategory("  Noise  ", agent);

        assertEquals("Noise", created.label());
        assertTrue(created.active());
        assertEquals(List.of("Noise"), service.listCategories().stream().map(TicketCategory::label).toList());
        assertEquals(List.of("TICKET_CATEGORY_CREATED"), audit.actions());
    }

    @Test
    void duplicateOrBlankLabelsAreRejectedCaseInsensitively() {
        service.createCategory("Noise", agent);

        assertThrows(IllegalArgumentException.class, () -> service.createCategory("noise", agent));
        assertThrows(IllegalArgumentException.class, () -> service.createCategory("   ", agent));
    }

    @Test
    void renameAllowsTheSameLabelButNotAnotherCategorysLabel() {
        TicketCategory noise = service.createCategory("Noise", agent);
        service.createCategory("Smell", agent);

        assertEquals("NOISE", service.renameCategory(noise.categoryId(), "NOISE", agent).label());
        assertThrows(IllegalArgumentException.class,
                () -> service.renameCategory(noise.categoryId(), "smell", agent));
        assertThrows(IllegalArgumentException.class,
                () -> service.renameCategory(UUID.randomUUID(), "Whatever", agent));
    }

    @Test
    void deactivatedCategoriesLeaveTheGuestListButStayInTheAdminList() {
        TicketCategory noise = service.createCategory("Noise", agent);

        service.setCategoryActive(noise.categoryId(), false, agent);

        assertTrue(service.listCategories().isEmpty());
        assertEquals(1, service.listAllCategories().size());
        assertFalse(service.listAllCategories().get(0).active());

        service.setCategoryActive(noise.categoryId(), true, agent);
        assertEquals(1, service.listCategories().size());
    }

    @Test
    void onlyAgentsMayAdministerCategories() {
        assertThrows(IllegalStateException.class, () -> service.createCategory("Noise", guest));
        assertThrows(IllegalStateException.class, () -> service.createCategory("Noise", UUID.randomUUID()));
    }
}
```

- [x] **Step 2: Run** — `.\gradlew test --tests "com.snoozeshare.service.TicketServiceCategoryTest"` → PASS (5 tests). If a test fails, fix `TicketServiceImpl` (not the test) unless the test contradicts the spec.

- [x] **Step 3: Commit**

```powershell
git add src/test/java/com/snoozeshare/service/TicketServiceCategoryTest.java
git commit -m "test: cover ticket category administration (F9.3.1)" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: Dispute read models

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/DisputeQueryServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/DisputeQueryServiceTest.java`

- [x] **Step 1: Write the failing test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.impl.DisputeQueryServiceImpl;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeQueryServiceTest {

    private static DisputeQueryServiceImpl service(MockDbFixture db) {
        var c = db.connection();
        return new DisputeQueryServiceImpl(new JdbcTicketRepository(c), new JdbcBookingRepository(c),
                new JdbcPropertyRepository(c), new JdbcUserRepository(c),
                new JdbcWalletTransactionRepository(c), SettlementFixtures.CLOCK);
    }

    @Test
    void queueIsOldestFirstWithNamesResolved(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            List<DisputeSummary> queue = service(db).queue(null, AssigneeFilter.ALL, MockIds.AGENT_AMY);

            assertEquals(6, queue.size());
            assertEquals(MockIds.TICKET_4, queue.get(0).ticketId());
            assertEquals(MockIds.TICKET_5, queue.get(5).ticketId());
            DisputeSummary open = queue.get(1);
            assertEquals(MockIds.TICKET_2, open.ticketId());
            assertEquals("#0002", open.ticketLabel());
            assertEquals("Missing promised beach access", open.title());
            assertEquals("Beachfront Bungalow", open.listingTitle());
            assertEquals("Aria Costa", open.guestName());
            assertEquals("Priya Nair", open.hostName());
            assertNull(open.assignedAgentName());
            assertEquals(TicketStatus.OPEN, open.status());
        }
    }

    @Test
    void queueHonoursTheAssigneeFilter(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeQueryServiceImpl service = service(db);

            assertEquals(List.of(MockIds.TICKET_2), service.queue(null, AssigneeFilter.UNASSIGNED,
                    MockIds.AGENT_AMY).stream().map(DisputeSummary::ticketId).toList());
            List<DisputeSummary> mine = service.queue(null, AssigneeFilter.MINE, MockIds.AGENT_BEN);
            assertEquals(1, mine.size());
            assertEquals("Ben Alvarez", mine.get(0).assignedAgentName());
        }
    }

    @Test
    void detailOfAHeldBookingShowsTheEscrowAndTheStayEndedPhase(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeDetail detail = service(db).detail(MockIds.TICKET_3);

            assertEquals(BookingStatus.CONFIRMED, detail.bookingStatus());
            assertTrue(detail.escrowHeld());
            assertEquals(0, new BigDecimal("210").compareTo(detail.escrowAmount()));
            assertEquals("Stay ended — escrow held", detail.phaseLabel());
            assertEquals("Sophia Rossi", detail.guestName());
            assertEquals("Diego Fernandez", detail.hostName());
            assertEquals("Modern Studio Near Metro", detail.listingTitle());
            assertEquals("Ben Alvarez", detail.assignedAgentName());
        }
    }

    @Test
    void detailOfASettledBookingShowsNoHeldEscrow(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeDetail detail = service(db).detail(MockIds.TICKET_1);

            assertFalse(detail.escrowHeld());
            assertEquals(BookingStatus.COMPLETED, detail.bookingStatus());
            assertEquals("Completed", detail.phaseLabel());
        }
    }

    @Test
    void anUnknownTicketIsRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            DisputeQueryServiceImpl service = service(db);

            assertThrows(IllegalArgumentException.class, () -> service.detail(UUID.randomUUID()));
        }
    }
}
```

- [x] **Step 2: Run to verify it fails** — `.\gradlew test --tests "com.snoozeshare.service.DisputeQueryServiceTest"` → FAIL (`cannot find symbol: class DisputeQueryServiceImpl`).

- [x] **Step 3: Implement**

```java
package com.snoozeshare.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.settlement.EscrowPolicy;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.repository.WalletTransactionRepository;
import com.snoozeshare.service.DisputeDetail;
import com.snoozeshare.service.DisputeQueryService;
import com.snoozeshare.service.DisputeSummary;

public final class DisputeQueryServiceImpl implements DisputeQueryService {

    private final TicketRepository tickets;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final WalletTransactionRepository transactions;
    private final Clock clock;

    public DisputeQueryServiceImpl(TicketRepository tickets, BookingRepository bookings,
                                   PropertyRepository properties, UserRepository users,
                                   WalletTransactionRepository transactions, Clock clock) {
        this.tickets = tickets;
        this.bookings = bookings;
        this.properties = properties;
        this.users = users;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public List<DisputeSummary> queue(TicketStatus status, AssigneeFilter assignee, UUID agentId) {
        return tickets.findQueue(status, assignee, agentId).stream().map(this::summarize).toList();
    }

    @Override
    public DisputeDetail detail(UUID ticketId) {
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket does not exist"));
        Booking booking = booking(ticket);
        Property property = property(booking);
        boolean held = EscrowPolicy.isHeld(transactions.findByBookingId(booking.bookingId()));
        return new DisputeDetail(ticket, label(ticketId), property.title(), booking.startDate(),
                booking.endDate(), name(booking.guestId()), name(property.hostId()),
                name(ticket.raisedByUserId()), agentName(ticket), booking.status(),
                booking.totalAmount(), held, phase(booking, held, LocalDate.now(clock)));
    }

    private DisputeSummary summarize(Ticket ticket) {
        Booking booking = booking(ticket);
        Property property = property(booking);
        return new DisputeSummary(ticket.ticketId(), label(ticket.ticketId()), ticket.title(),
                ticket.category(), property.title(), name(booking.guestId()), name(property.hostId()),
                agentName(ticket), ticket.status(), ticket.createdAt());
    }

    private Booking booking(Ticket ticket) {
        return bookings.findById(ticket.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
    }

    private Property property(Booking booking) {
        return properties.findById(booking.listingId())
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
    }

    private String agentName(Ticket ticket) {
        return ticket.assignedAgentId() == null ? null : name(ticket.assignedAgentId());
    }

    private String name(UUID userId) {
        return users.findById(userId).map(User::displayName).orElse("Unknown user");
    }

    static String label(UUID ticketId) {
        String text = ticketId.toString();
        return "#" + text.substring(text.length() - 4);
    }

    private static String phase(Booking booking, boolean held, LocalDate today) {
        if (booking.status() == BookingStatus.CONFIRMED) {
            if (held && booking.endDate().isBefore(today)) {
                return "Stay ended — escrow held";
            }
            if (booking.startDate().isAfter(today)) {
                return "Upcoming";
            }
            if (!booking.endDate().isBefore(today)) {
                return "Active";
            }
        }
        return prettify(booking.status());
    }

    private static String prettify(BookingStatus status) {
        String text = status.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
```

- [x] **Step 4: Run** — same command → PASS (5 tests).

- [x] **Step 5: Commit**

```powershell
git add src/main src/test
git commit -m "feat: add dispute queue/detail read models" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 12: Wire `AppContext`, dev DB entry, and the end-to-end service test

**Files:**
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`, `src/main/java/com/snoozeshare/app/Main.java`
- Test: `src/test/java/com/snoozeshare/service/DisputeFlowEndToEndTest.java`

- [x] **Step 1: Write the failing end-to-end test**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.service.requests.ResolutionRequest;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeFlowEndToEndTest {

    @Test
    void anAgentWorksAnOpenDisputeThroughTheWholeAppContext(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            User amy = context.userService().authenticate("amy.tanaka@snoozeshare.test");
            context.session().loginAs(amy);
            List<TicketResolvedEvent> resolved = new ArrayList<>();
            context.eventBus().subscribe(TicketResolvedEvent.class, resolved::add);

            var unassigned = context.disputeQueryService().queue(
                    null, AssigneeFilter.UNASSIGNED, amy.userId());
            assertEquals(1, unassigned.size());
            assertEquals(MockIds.TICKET_2, unassigned.get(0).ticketId());

            context.messageService().post(MockIds.TICKET_2, ThreadChannel.GUEST, amy.userId(),
                    Role.AGENT, "Could you send a photo of the gate?");
            assertEquals(1, context.messageService().thread(MockIds.TICKET_2, ThreadChannel.GUEST).size());

            context.ticketService().assignToMe(MockIds.TICKET_2, amy.userId());
            context.ticketService().resolve(MockIds.TICKET_2,
                    new ResolutionRequest(ResolutionMode.ACCEPT, new BigDecimal("175.00"),
                            "Gate was locked"), amy.userId());

            var detail = context.disputeQueryService().detail(MockIds.TICKET_2);
            assertEquals(TicketStatus.RESOLVED_APPROVED, detail.ticket().status());
            assertFalse(detail.escrowHeld());
            assertEquals(1, resolved.size());
            assertEquals(0, new BigDecimal("700").compareTo(
                    context.walletService().balanceOf(MockIds.GUEST_ARIA)));
            assertTrue(context.walletService().statementFor(MockIds.GUEST_ARIA).stream()
                    .anyMatch(t -> t.relatedTicketId() != null
                            && t.relatedTicketId().equals(MockIds.TICKET_2)));
            db.assertLedgerInvariant();
        }
    }
}
```

- [x] **Step 2: Run to verify it fails** — `.\gradlew test --tests "com.snoozeshare.service.DisputeFlowEndToEndTest"` → FAIL (`cannot find symbol: method create(String)` / `disputeQueryService()`).

- [x] **Step 3: Modify `AppContext`**

Add imports (alphabetical within groups): `java.time.Clock`; `com.snoozeshare.repository.jdbc.JdbcTicketCategoryRepository`, `JdbcTicketRepository`; `com.snoozeshare.service.DisputeQueryService`, `DisputeSettlementService`, `MessageService`, `TicketService`; `com.snoozeshare.service.impl.DisputeQueryServiceImpl`, `DisputeSettlementServiceImpl`, `InMemoryMessageService`, `TicketServiceImpl`.

Add fields next to the existing ones:

```java
    private final TicketService ticketService;
    private final DisputeQueryService disputeQueryService;
    private final MessageService messageService;
```

In the constructor, replace the line `this.sceneRouter = new SceneRouter();` with:

```java
        JdbcTicketRepository ticketRepo = new JdbcTicketRepository(connection);
        JdbcTicketCategoryRepository categoryRepo = new JdbcTicketCategoryRepository(connection);
        JdbcWalletTransactionRepository transactionRepo = new JdbcWalletTransactionRepository(connection);
        Clock clock = Clock.systemUTC();
        DisputeSettlementService settlementService = new DisputeSettlementServiceImpl(connection,
                ticketRepo, bookingRepo, propertyRepo, users, wallets, transactionRepo, auditService,
                eventBus, clock);
        this.ticketService = new TicketServiceImpl(ticketRepo, categoryRepo, bookingRepo, users,
                settlementService, auditService, clock);
        this.disputeQueryService = new DisputeQueryServiceImpl(ticketRepo, bookingRepo, propertyRepo,
                users, transactionRepo, clock);
        this.messageService = new InMemoryMessageService(clock);
        this.sceneRouter = new SceneRouter();
```

Add a factory and accessors:

```java
    public static AppContext create(String jdbcUrl) throws SQLException {
        return new AppContext(ConnectionFactory.open(jdbcUrl));
    }

    public TicketService ticketService() {
        return ticketService;
    }

    public DisputeQueryService disputeQueryService() {
        return disputeQueryService;
    }

    public MessageService messageService() {
        return messageService;
    }
```

- [x] **Step 4: Modify `Main.start`** so a developer can run against a copy of the mock DB

Replace `context = AppContext.create();` with:

```java
            String databaseUrl = System.getenv("SNOOZESHARE_DB_URL");
            context = databaseUrl == null || databaseUrl.isBlank()
                    ? AppContext.create() : AppContext.create(databaseUrl);
```

- [x] **Step 5: Run the end-to-end test, then the whole suite**

Run: `.\gradlew test --tests "com.snoozeshare.service.DisputeFlowEndToEndTest"` → PASS.
Run: `.\gradlew test` → PASS.

- [x] **Step 6: Commit**

```powershell
git add src/main src/test
git commit -m "feat: wire W10 services into AppContext; allow running against a DB copy" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 13: Resolution preview (pure UI logic) and theme classes

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/admin/tickets/ResolutionPreview.java`
- Modify: `src/main/resources/com/snoozeshare/ui/common/theme.css`
- Test: `src/test/java/com/snoozeshare/ui/admin/ResolutionPreviewTest.java`

- [x] **Step 1: Write the failing test**

```java
package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.snoozeshare.ui.admin.tickets.ResolutionPreview;

class ResolutionPreviewTest {

    private static final BigDecimal ESCROW = new BigDecimal("480.00");

    @Test
    void validAmountShowsRefundHostPayoutAndFee() {
        ResolutionPreview.Result result = ResolutionPreview.compute(ESCROW, "100");

        assertTrue(result.valid());
        assertEquals("Guest refund SGD 100.00 | Host payout SGD 368.60 (fee SGD 11.40)", result.summary());
        assertEquals(0, new BigDecimal("100").compareTo(result.refund()));
    }

    @Test
    void zeroIsAFullPayoutAndTheEscrowIsAFullRefund() {
        assertEquals("Guest refund SGD 0.00 | Host payout SGD 465.60 (fee SGD 14.40)",
                ResolutionPreview.compute(ESCROW, "0").summary());
        assertEquals("Guest refund SGD 480.00 | Host payout SGD 0.00 (fee SGD 0.00)",
                ResolutionPreview.compute(ESCROW, "480.00").summary());
    }

    @Test
    void blankNonNumericNegativeAndTooLargeAmountsAreInvalidWithAMessage() {
        for (String text : new String[] {null, "", "  ", "abc", "-5", "480.01", "1.234"}) {
            ResolutionPreview.Result result = ResolutionPreview.compute(ESCROW, text);
            assertFalse(result.valid(), String.valueOf(text));
            assertFalse(result.message().isBlank(), String.valueOf(text));
        }
    }
}
```

- [x] **Step 2: Run to verify it fails** — `.\gradlew test --tests "com.snoozeshare.ui.admin.ResolutionPreviewTest"` → FAIL (`cannot find symbol: class ResolutionPreview`).

- [x] **Step 3: Implement**

```java
package com.snoozeshare.ui.admin.tickets;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.snoozeshare.domain.settlement.SettlementBreakdown;
import com.snoozeshare.domain.settlement.SettlementCalculator;

/**
 * Live preview text for the resolution dialog. Pure logic so it can be tested without JavaFX.
 */
public final class ResolutionPreview {

    /** valid=false carries an explanatory message; valid=true carries the summary and parsed refund. */
    public record Result(boolean valid, String message, String summary, BigDecimal refund) {
    }

    private ResolutionPreview() {
    }

    public static Result compute(BigDecimal escrow, String refundText) {
        if (refundText == null || refundText.isBlank()) {
            return invalid("Enter a guest refund amount (0 for a full payout to the host)");
        }
        BigDecimal refund;
        try {
            refund = new BigDecimal(refundText.trim());
        } catch (NumberFormatException exception) {
            return invalid("Refund must be a number");
        }
        try {
            SettlementBreakdown split = SettlementCalculator.split(escrow, refund);
            return new Result(true, "", "Guest refund " + money(split.guestRefund())
                    + " | Host payout " + money(split.hostNet()) + " (fee " + money(split.fee()) + ")",
                    split.guestRefund());
        } catch (IllegalArgumentException exception) {
            return invalid(exception.getMessage());
        }
    }

    private static Result invalid(String message) {
        return new Result(false, message, "", null);
    }

    private static String money(BigDecimal value) {
        return "SGD " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
```

- [x] **Step 4: Run** — same command → PASS.

- [x] **Step 5: Append the theme classes** to `theme.css` (matches the existing navy palette)

```css
/* --- W10: agent dispute screens --- */
.badge-danger { -fx-background-color: #fee4e2; -fx-text-fill: #b42318; -fx-padding: 3px 11px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; }
.badge-warning { -fx-background-color: #fef0c7; -fx-text-fill: #93370d; -fx-padding: 3px 11px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; }
.badge-success { -fx-background-color: #d1fadf; -fx-text-fill: #067647; -fx-padding: 3px 11px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; }
.filter-chip { -fx-background-color: #e8edf5; -fx-text-fill: #263a5b; -fx-background-radius: 999px; -fx-padding: 5px 13px; -fx-font-size: 12px; }
.filter-chip:selected { -fx-background-color: #263a5b; -fx-text-fill: white; -fx-font-weight: bold; }
.summary-card { -fx-background-color: white; -fx-border-color: #d4dbe7; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 16px 20px; }
.summary-caption { -fx-font-size: 11px; -fx-text-fill: #667085; }
.chat-pane { -fx-background-color: white; -fx-border-color: #d4dbe7; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 12px; }
.chat-bubble { -fx-background-color: #e8edf5; -fx-background-radius: 8px; -fx-padding: 8px 12px; }
.chat-bubble-agent { -fx-background-color: #d9e2f3; -fx-background-radius: 8px; -fx-padding: 8px 12px; }
```

- [x] **Step 6: Run** `.\gradlew test --tests "com.snoozeshare.ui.*"` → PASS (existing `AuthLayoutTest` reads `theme.css` for specific strings — still present). Commit:

```powershell
git add src/main src/test
git commit -m "feat: add resolution preview logic and dispute theme classes" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 14: Queue screen and admin shell wiring

**Files:**
- Create: `ui/admin/tickets/DisputeQueueController.java`, `resources/com/snoozeshare/ui/admin/tickets/dispute-queue.fxml`
- Modify: `ui/admin/AdminShellController.java`, `resources/com/snoozeshare/ui/admin/admin-shell.fxml`
- Test: `src/test/java/com/snoozeshare/ui/admin/AdminFxmlLayoutTest.java` (grows in Tasks 15–16)

- [x] **Step 1: Write the failing layout test**

```java
package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminFxmlLayoutTest {

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/resources/com/snoozeshare/ui/admin/" + relative));
    }

    private static String controller(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/snoozeshare/ui/admin/" + relative));
    }

    @Test
    void adminShellHostsDisputesAndCategoriesInItsCenterPane() throws Exception {
        String shell = read("admin-shell.fxml");
        String source = controller("AdminShellController.java");

        assertTrue(shell.contains("fx:id=\"shellRoot\""));
        assertTrue(shell.contains("onMouseClicked=\"#showCategories\""));
        assertTrue(shell.contains("onMouseClicked=\"#showDisputes\""));
        assertTrue(source.contains("showCategories"));
        assertTrue(source.contains("showDisputes"));
        assertTrue(source.contains("showOperations"));
        assertTrue(source.contains("showAccounts"));
    }

    @Test
    void queueScreenHasTheFiltersAndTable() throws Exception {
        String queue = read("tickets/dispute-queue.fxml");

        for (String id : new String[] {"table", "allChip", "unassignedChip", "mineChip", "statusCombo",
                "unassignedBadge"}) {
            assertTrue(queue.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(queue.contains("Dispute queue"));
    }

    @Test
    void noForceActionExistsAnywhereInTheAgentScreens() throws Exception {
        for (String file : new String[] {"admin-shell.fxml", "tickets/dispute-queue.fxml"}) {
            assertFalse(read(file).toLowerCase().contains("force"), file);
        }
        assertFalse(controller("AdminShellController.java").toLowerCase().contains("force"));
    }
}
```

- [x] **Step 2: Run to verify it fails** — `.\gradlew test --tests "com.snoozeshare.ui.admin.AdminFxmlLayoutTest"` → FAIL (files missing / `shellRoot` absent).

- [x] **Step 3: Create `dispute-queue.fxml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.ToggleButton?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.admin.tickets.DisputeQueueController">
    <padding><Insets top="24" right="32" bottom="24" left="32"/></padding>

    <HBox spacing="10" alignment="CENTER_LEFT">
        <Label text="Dispute queue" styleClass="page-title"/>
        <Label fx:id="unassignedBadge" styleClass="badge-danger"/>
        <Region HBox.hgrow="ALWAYS"/>
        <ToggleButton fx:id="allChip" text="All" styleClass="filter-chip" selected="true"/>
        <ToggleButton fx:id="unassignedChip" text="Unassigned" styleClass="filter-chip"/>
        <ToggleButton fx:id="mineChip" text="Mine" styleClass="filter-chip"/>
        <ComboBox fx:id="statusCombo" prefWidth="180"/>
    </HBox>

    <TableView fx:id="table" VBox.vgrow="ALWAYS"/>
    <Label fx:id="emptyLabel" styleClass="small"/>
</VBox>
```

- [x] **Step 4: Create `DisputeQueueController`**

```java
package com.snoozeshare.ui.admin.tickets;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.service.DisputeSummary;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

public final class DisputeQueueController {

    private static final String[] STATUS_LABELS = {
        "All statuses", "Open", "Under review", "Resolved (approved)", "Resolved (rejected)"
    };
    private static final TicketStatus[] STATUS_VALUES = {
        null, TicketStatus.OPEN, TicketStatus.UNDER_REVIEW, TicketStatus.RESOLVED_APPROVED,
        TicketStatus.RESOLVED_REJECTED
    };

    @FXML private TableView<DisputeSummary> table;
    @FXML private ToggleButton allChip;
    @FXML private ToggleButton unassignedChip;
    @FXML private ToggleButton mineChip;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Label unassignedBadge;
    @FXML private Label emptyLabel;

    private AppContext context;
    private Consumer<UUID> onOpen = id -> { };
    private Subscription subscription;
    private AssigneeFilter filter = AssigneeFilter.ALL;

    @FXML
    private void initialize() {
        ToggleGroup group = new ToggleGroup();
        allChip.setToggleGroup(group);
        unassignedChip.setToggleGroup(group);
        mineChip.setToggleGroup(group);
        group.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true);
                return;
            }
            filter = selected == unassignedChip ? AssigneeFilter.UNASSIGNED
                    : selected == mineChip ? AssigneeFilter.MINE : AssigneeFilter.ALL;
            refresh();
        });
        statusCombo.getItems().addAll(STATUS_LABELS);
        statusCombo.getSelectionModel().selectFirst();
        statusCombo.valueProperty().addListener((observable, previous, selected) -> refresh());

        table.getColumns().add(column("Ticket", 90, DisputeSummary::ticketLabel));
        table.getColumns().add(column("Subject", 260, DisputeSummary::title));
        table.getColumns().add(column("Booking", 190, DisputeSummary::listingTitle));
        table.getColumns().add(column("Guest / Host", 210,
                summary -> summary.guestName() + " / " + summary.hostName()));
        table.getColumns().add(column("Assigned", 130,
                summary -> summary.assignedAgentName() == null ? "—" : summary.assignedAgentName()));
        table.getColumns().add(column("Status", 150, summary -> statusText(summary.status())));
        table.setRowFactory(view -> {
            TableRow<DisputeSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    onOpen.accept(row.getItem().ticketId());
                }
            });
            return row;
        });
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        subscription = context.eventBus().subscribe(TicketResolvedEvent.class,
                event -> Platform.runLater(this::refresh));
        refresh();
    }

    public void setOnOpen(Consumer<UUID> callback) {
        onOpen = callback == null ? id -> { } : callback;
    }

    public void dispose() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private void refresh() {
        if (context == null) {
            return;
        }
        UUID agentId = context.session().currentUser().orElseThrow().userId();
        int selected = Math.max(0, statusCombo.getSelectionModel().getSelectedIndex());
        var rows = context.disputeQueryService().queue(STATUS_VALUES[selected], filter, agentId);
        table.getItems().setAll(rows);
        emptyLabel.setText(rows.isEmpty() ? "No disputes match these filters." : "");
        int unassigned = context.disputeQueryService()
                .queue(TicketStatus.OPEN, AssigneeFilter.UNASSIGNED, agentId).size();
        unassignedBadge.setText(unassigned + " unassigned");
        unassignedBadge.setVisible(unassigned > 0);
        unassignedBadge.setManaged(unassigned > 0);
    }

    private static TableColumn<DisputeSummary, String> column(String title, double width,
            Function<DisputeSummary, String> value) {
        TableColumn<DisputeSummary, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    static String statusText(TicketStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case UNDER_REVIEW -> "Under review";
            case RESOLVED_APPROVED -> "Resolved (approved)";
            case RESOLVED_REJECTED -> "Resolved (rejected)";
        };
    }
}
```

- [x] **Step 5: Update the admin shell.** In `admin-shell.fxml` change the root to expose the center and add the nav item:

```xml
<BorderPane fx:id="shellRoot" xmlns="http://javafx.com/javafx/25" xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.snoozeshare.ui.admin.AdminShellController" minWidth="1280" minHeight="800">
```

and add `<Label text="Categories" styleClass="nav-item" onMouseClicked="#showCategories"/>` after the `Accounts` label in the sidebar `VBox`.

Replace `AdminShellController.java`:

```java
package com.snoozeshare.ui.admin;

import java.io.IOException;
import java.util.UUID;

import com.snoozeshare.ui.admin.categories.CategoryAdminController;
import com.snoozeshare.ui.admin.tickets.DisputeDetailController;
import com.snoozeshare.ui.admin.tickets.DisputeQueueController;
import com.snoozeshare.ui.common.NavShellController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public final class AdminShellController extends NavShellController {

    @FXML private BorderPane shellRoot;

    private Node defaultCenter;
    private DisputeQueueController queueController;

    @FXML
    private void initialize() {
        defaultCenter = shellRoot.getCenter();
    }

    @FXML
    private void showOperations() {
        restoreDefaultCenter();
        displayPage("Operations", "Monitor support operations and platform activity.");
    }

    @FXML
    private void showDisputes() {
        showQueue();
    }

    @FXML
    private void showAccounts() {
        restoreDefaultCenter();
        displayPage("Accounts", "Manage account status and support access.");
    }

    @FXML
    private void showCategories() {
        disposeQueue();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/admin/categories/category-admin.fxml"));
            Node view = loader.load();
            CategoryAdminController controller = loader.getController();
            controller.setContext(getContext());
            shellRoot.setCenter(view);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load category administration", exception);
        }
    }

    private void showQueue() {
        disposeQueue();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/admin/tickets/dispute-queue.fxml"));
            Node view = loader.load();
            queueController = loader.getController();
            queueController.setContext(getContext());
            queueController.setOnOpen(this::showDetail);
            shellRoot.setCenter(view);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load dispute queue", exception);
        }
    }

    private void showDetail(UUID ticketId) {
        disposeQueue();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
            Node view = loader.load();
            DisputeDetailController controller = loader.getController();
            controller.setContext(getContext());
            controller.setOnBack(this::showQueue);
            controller.load(ticketId);
            shellRoot.setCenter(view);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load dispute detail", exception);
        }
    }

    private void restoreDefaultCenter() {
        disposeQueue();
        shellRoot.setCenter(defaultCenter);
    }

    private void disposeQueue() {
        if (queueController != null) {
            queueController.dispose();
            queueController = null;
        }
    }
}
```

The detail and category controllers are created in Tasks 15 and 16; **this task will not compile until they exist**. So: create empty-but-compiling stubs now (each with the `setContext`/`setOnBack`/`load` methods shown in Tasks 15–16, bodies empty), commit, and fill them in later — or do Tasks 15–16 before Step 6. Prefer the stub route to keep commits green:

```java
package com.snoozeshare.ui.admin.tickets;

import java.util.UUID;

import com.snoozeshare.app.AppContext;

public final class DisputeDetailController {
    public void setContext(AppContext context) {
    }

    public void setOnBack(Runnable callback) {
    }

    public void load(UUID ticketId) {
    }
}
```

```java
package com.snoozeshare.ui.admin.categories;

import com.snoozeshare.app.AppContext;

public final class CategoryAdminController {
    public void setContext(AppContext context) {
    }
}
```

plus placeholder `dispute-detail.fxml` / `category-admin.fxml` files containing just `<?xml version="1.0" encoding="UTF-8"?><javafx.scene.layout.VBox xmlns:fx="http://javafx.com/fxml" fx:controller="<the controller>"/>` (with the matching controller name) so `showDetail`/`showCategories` load without error until replaced.

- [x] **Step 6: Run** — `.\gradlew test --tests "com.snoozeshare.ui.*"` → PASS (including the existing `ShellNavigationTest`, which still finds `showOperations/showDisputes/showAccounts`, and `UiDependencyTest`, which forbids `repository`/`java.sql` imports — none used).

- [x] **Step 7: Commit**

```powershell
git add src/main src/test
git commit -m "feat: agent dispute queue screen and admin shell navigation" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 15: Dispute detail screen and resolution dialog

**Files:**
- Replace stubs: `ui/admin/tickets/DisputeDetailController.java`, `resources/.../tickets/dispute-detail.fxml`
- Create: `ui/admin/tickets/ResolutionDialogController.java`, `resources/.../tickets/resolution-dialog.fxml`
- Test: extend `AdminFxmlLayoutTest`

- [x] **Step 1: Extend the failing layout test** — add to `AdminFxmlLayoutTest`

```java
    @Test
    void detailScreenHasBookingSummaryChatPanesNotesAndTheThreeResolutionActions() throws Exception {
        String detail = read("tickets/dispute-detail.fxml");

        for (String id : new String[] {"crumbLabel", "statusBadge", "assignButton", "listingLabel",
                "datesLabel", "guestLabel", "hostLabel", "escrowLabel", "phaseLabel", "guestThread",
                "hostThread", "guestInput", "hostInput", "notesHistory", "notesArea", "addNoteButton",
                "acceptButton", "rejectButton", "manualButton", "errorLabel"}) {
            assertTrue(detail.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(detail.contains("Internal notes"));
        assertFalse(detail.toLowerCase().contains("force"));
    }

    @Test
    void resolutionDialogHasModeChipsAmountPreviewAndRequiredReason() throws Exception {
        String dialog = read("tickets/resolution-dialog.fxml");

        for (String id : new String[] {"remedyLabel", "fullRefundChip", "fullPayoutChip", "customChip",
                "amountBox", "amountField", "previewLabel", "reasonArea", "errorLabel"}) {
            assertTrue(dialog.contains("fx:id=\"" + id + "\""), id);
        }
        assertTrue(dialog.contains("Reason (required, written to the audit log)"));
        assertFalse(dialog.contains("Adjust wallet"));
    }
```

- [x] **Step 2: Run to verify it fails** — `.\gradlew test --tests "com.snoozeshare.ui.admin.AdminFxmlLayoutTest"` → the two new tests FAIL (placeholder FXML).

- [x] **Step 3: Write `dispute-detail.fxml`** (replace the placeholder)

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Hyperlink?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.control.TextArea?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<ScrollPane fitToWidth="true" xmlns:fx="http://javafx.com/fxml"
            fx:controller="com.snoozeshare.ui.admin.tickets.DisputeDetailController">
    <VBox spacing="16">
        <padding><Insets top="20" right="32" bottom="28" left="32"/></padding>

        <HBox spacing="8" alignment="CENTER_LEFT">
            <Hyperlink fx:id="backLink" text="Disputes" onAction="#handleBack"/>
            <Label text="&gt;"/>
            <Label fx:id="crumbLabel" styleClass="card-title"/>
            <Label fx:id="statusBadge"/>
            <Region HBox.hgrow="ALWAYS"/>
            <Button fx:id="assignButton" text="Assign to me" onAction="#handleAssign" styleClass="button"/>
        </HBox>

        <HBox styleClass="summary-card" spacing="28" alignment="CENTER_LEFT">
            <VBox spacing="2"><Label text="Booking" styleClass="summary-caption"/><Label fx:id="listingLabel" styleClass="card-title"/><Label fx:id="datesLabel" styleClass="small"/></VBox>
            <VBox spacing="2"><Label text="Guest" styleClass="summary-caption"/><Label fx:id="guestLabel" styleClass="card-title"/></VBox>
            <VBox spacing="2"><Label text="Host" styleClass="summary-caption"/><Label fx:id="hostLabel" styleClass="card-title"/></VBox>
            <VBox spacing="2"><Label text="Escrow" styleClass="summary-caption"/><Label fx:id="escrowLabel" styleClass="card-title"/></VBox>
            <VBox spacing="2"><Label text="Booking phase" styleClass="summary-caption"/><Label fx:id="phaseLabel" styleClass="badge-warning"/></VBox>
        </HBox>

        <HBox spacing="16">
            <VBox styleClass="chat-pane" spacing="8" HBox.hgrow="ALWAYS" minHeight="300">
                <Label fx:id="guestThreadTitle" text="Guest messages" styleClass="card-title"/>
                <VBox fx:id="guestThread" spacing="8" VBox.vgrow="ALWAYS"/>
                <HBox spacing="8">
                    <TextField fx:id="guestInput" promptText="Reply to guest..." HBox.hgrow="ALWAYS"/>
                    <Button text="Send" onAction="#handleSendGuest" styleClass="button"/>
                </HBox>
            </VBox>
            <VBox styleClass="chat-pane" spacing="8" HBox.hgrow="ALWAYS" minHeight="300">
                <Label fx:id="hostThreadTitle" text="Host messages" styleClass="card-title"/>
                <VBox fx:id="hostThread" spacing="8" VBox.vgrow="ALWAYS"/>
                <HBox spacing="8">
                    <TextField fx:id="hostInput" promptText="Reply to host..." HBox.hgrow="ALWAYS"/>
                    <Button text="Send" onAction="#handleSendHost" styleClass="button"/>
                </HBox>
            </VBox>
        </HBox>

        <Label text="Internal notes (invisible to guest or host)" styleClass="card-title"/>
        <Label fx:id="notesHistory" wrapText="true" styleClass="small"/>
        <TextArea fx:id="notesArea" promptText="Add investigation notes..." prefRowCount="3"/>
        <HBox><Button fx:id="addNoteButton" text="Add note" onAction="#handleAddNote" styleClass="outline-button"/></HBox>

        <HBox spacing="10" alignment="CENTER_LEFT">
            <Button fx:id="acceptButton" text="Accept" onAction="#handleAccept" styleClass="button"/>
            <Button fx:id="rejectButton" text="Reject dispute" onAction="#handleReject" styleClass="outline-button"/>
            <Button fx:id="manualButton" text="Manual adjustment..." onAction="#handleManual" styleClass="outline-button"/>
        </HBox>
        <Label fx:id="errorLabel" styleClass="error-message"/>
    </VBox>
</ScrollPane>
```

- [x] **Step 4: Replace `DisputeDetailController`**

```java
package com.snoozeshare.ui.admin.tickets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.service.DisputeDetail;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class DisputeDetailController {

    @FXML private Label crumbLabel;
    @FXML private Label statusBadge;
    @FXML private Button assignButton;
    @FXML private Label listingLabel;
    @FXML private Label datesLabel;
    @FXML private Label guestLabel;
    @FXML private Label hostLabel;
    @FXML private Label escrowLabel;
    @FXML private Label phaseLabel;
    @FXML private Label guestThreadTitle;
    @FXML private Label hostThreadTitle;
    @FXML private VBox guestThread;
    @FXML private VBox hostThread;
    @FXML private TextField guestInput;
    @FXML private TextField hostInput;
    @FXML private Label notesHistory;
    @FXML private TextArea notesArea;
    @FXML private Button addNoteButton;
    @FXML private Button acceptButton;
    @FXML private Button rejectButton;
    @FXML private Button manualButton;
    @FXML private Label errorLabel;

    private AppContext context;
    private UUID ticketId;
    private DisputeDetail detail;
    private Runnable onBack = () -> { };

    public void setContext(AppContext appContext) {
        context = appContext;
    }

    public void setOnBack(Runnable callback) {
        onBack = callback == null ? () -> { } : callback;
    }

    public void load(UUID id) {
        ticketId = id;
        render();
    }

    private UUID me() {
        return context.session().currentUser().orElseThrow().userId();
    }

    private void render() {
        detail = context.disputeQueryService().detail(ticketId);
        Ticket ticket = detail.ticket();
        boolean mine = me().equals(ticket.assignedAgentId());
        boolean underReview = ticket.status() == TicketStatus.UNDER_REVIEW;
        boolean canResolve = underReview && mine && detail.escrowHeld();

        crumbLabel.setText(detail.ticketLabel() + " " + ticket.title());
        statusBadge.setText(ticket.status() == TicketStatus.OPEN ? "Unassigned" : statusText(ticket.status()));
        statusBadge.getStyleClass().setAll(ticket.status() == TicketStatus.OPEN ? "badge-danger"
                : underReview ? "badge-warning" : "badge-success");
        assignButton.setDisable(ticket.status() != TicketStatus.OPEN);
        listingLabel.setText(detail.listingTitle());
        datesLabel.setText(detail.startDate() + " to " + detail.endDate());
        guestLabel.setText(detail.guestName());
        hostLabel.setText(detail.hostName());
        escrowLabel.setText(detail.escrowHeld() ? money(detail.escrowAmount()) + " held" : "Settled");
        phaseLabel.setText(detail.phaseLabel());
        guestThreadTitle.setText("Guest messages · " + detail.guestName());
        hostThreadTitle.setText("Host messages · " + detail.hostName());
        notesHistory.setText(ticket.agentNotes() == null ? "No notes yet." : ticket.agentNotes());
        addNoteButton.setDisable(!(underReview && mine));
        acceptButton.setText("Accept — remedy " + (ticket.raisedByRole() == Role.HOST ? "host" : "guest"));
        acceptButton.setDisable(!canResolve);
        rejectButton.setDisable(!canResolve);
        manualButton.setDisable(!canResolve);
        renderThread(guestThread, ThreadChannel.GUEST);
        renderThread(hostThread, ThreadChannel.HOST);
    }

    private void renderThread(VBox box, ThreadChannel channel) {
        box.getChildren().clear();
        List<Message> messages = context.messageService().thread(ticketId, channel);
        if (messages.isEmpty()) {
            Label empty = new Label("No messages yet.");
            empty.getStyleClass().add("small");
            box.getChildren().add(empty);
        }
        for (Message message : messages) {
            Label bubble = new Label(message.body());
            bubble.setWrapText(true);
            bubble.getStyleClass().add(message.authorRole() == Role.AGENT ? "chat-bubble-agent" : "chat-bubble");
            box.getChildren().add(bubble);
        }
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }

    @FXML
    private void handleAssign() {
        run(() -> context.ticketService().assignToMe(ticketId, me()));
    }

    @FXML
    private void handleAddNote() {
        run(() -> {
            context.ticketService().addAgentNote(ticketId, notesArea.getText(), me());
            notesArea.clear();
        });
    }

    @FXML
    private void handleSendGuest() {
        send(ThreadChannel.GUEST, guestInput);
    }

    @FXML
    private void handleSendHost() {
        send(ThreadChannel.HOST, hostInput);
    }

    @FXML
    private void handleAccept() {
        resolve(ResolutionMode.ACCEPT);
    }

    @FXML
    private void handleReject() {
        resolve(ResolutionMode.REJECT);
    }

    @FXML
    private void handleManual() {
        resolve(ResolutionMode.MANUAL);
    }

    private void send(ThreadChannel channel, TextField input) {
        run(() -> {
            context.messageService().post(ticketId, channel, me(), Role.AGENT, input.getText());
            input.clear();
        });
    }

    private void resolve(ResolutionMode mode) {
        ResolutionDialogController.show(detail, mode)
                .ifPresent(request -> run(() -> context.ticketService().resolve(ticketId, request, me())));
    }

    private void run(Runnable action) {
        try {
            action.run();
            errorLabel.setText("");
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
        }
        render();
    }

    private static String statusText(TicketStatus status) {
        return DisputeQueueController.statusText(status);
    }

    private static String money(BigDecimal value) {
        return "SGD " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
```

`DisputeQueueController.statusText` is package-private and this controller is in the same package — fine.

- [x] **Step 5: Create `resolution-dialog.fxml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TextArea?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.control.ToggleButton?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="12" prefWidth="480" xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.admin.tickets.ResolutionDialogController">
    <padding><Insets top="16" right="16" bottom="8" left="16"/></padding>

    <Label fx:id="subtitleLabel" styleClass="small"/>
    <Label fx:id="remedyLabel" styleClass="card-title"/>

    <HBox fx:id="modeChips" spacing="6">
        <ToggleButton fx:id="fullRefundChip" text="Full refund to guest" styleClass="filter-chip"/>
        <ToggleButton fx:id="fullPayoutChip" text="Full payout to host" styleClass="filter-chip"/>
        <ToggleButton fx:id="customChip" text="Custom" styleClass="filter-chip"/>
    </HBox>

    <VBox fx:id="amountBox" spacing="4">
        <Label text="Guest refund (SGD)"/>
        <TextField fx:id="amountField"/>
    </VBox>

    <Label fx:id="previewLabel" styleClass="price-panel" wrapText="true"/>

    <Label text="Reason (required, written to the audit log)"/>
    <TextArea fx:id="reasonArea" prefRowCount="3"/>
    <Label fx:id="errorLabel" styleClass="error-message"/>
</VBox>
```

- [x] **Step 6: Create `ResolutionDialogController`**

```java
package com.snoozeshare.ui.admin.tickets;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.service.DisputeDetail;
import com.snoozeshare.service.requests.ResolutionRequest;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class ResolutionDialogController {

    @FXML private Label subtitleLabel;
    @FXML private Label remedyLabel;
    @FXML private HBox modeChips;
    @FXML private ToggleButton fullRefundChip;
    @FXML private ToggleButton fullPayoutChip;
    @FXML private ToggleButton customChip;
    @FXML private VBox amountBox;
    @FXML private TextField amountField;
    @FXML private Label previewLabel;
    @FXML private TextArea reasonArea;
    @FXML private Label errorLabel;

    private DisputeDetail detail;
    private ResolutionMode mode;

    /** Shows the dialog modally; empty when the agent cancels. */
    public static Optional<ResolutionRequest> show(DisputeDetail detail, ResolutionMode mode) {
        try {
            FXMLLoader loader = new FXMLLoader(ResolutionDialogController.class.getResource(
                    "/com/snoozeshare/ui/admin/tickets/resolution-dialog.fxml"));
            Node content = loader.load();
            ResolutionDialogController controller = loader.getController();
            controller.configure(detail, mode);

            ButtonType confirm = new ButtonType(controller.confirmLabel(), ButtonBar.ButtonData.OK_DONE);
            Dialog<ResolutionRequest> dialog = new Dialog<>();
            dialog.setTitle(controller.title());
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, confirm);
            dialog.getDialogPane().getStylesheets().add(ResolutionDialogController.class.getResource(
                    "/com/snoozeshare/ui/common/theme.css").toExternalForm());
            Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirm);
            confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.validate()) {
                    event.consume();
                }
            });
            dialog.setResultConverter(button -> button == confirm ? controller.buildRequest() : null);
            return dialog.showAndWait();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load resolution dialog", exception);
        }
    }

    private void configure(DisputeDetail disputeDetail, ResolutionMode resolutionMode) {
        detail = disputeDetail;
        mode = resolutionMode;
        String target = detail.ticket().raisedByRole() == Role.HOST ? "host" : "guest";
        subtitleLabel.setText(detail.ticketLabel() + " · " + detail.listingTitle() + " · "
                + detail.guestName() + " vs " + detail.hostName());
        remedyLabel.setText(switch (mode) {
            case ACCEPT -> "Requested remedy: " + detail.ticket().requestedRemedy() + " (" + target + ")";
            case REJECT -> "The requested remedy will not be issued; the host is paid in full.";
            case MANUAL -> "Settle the held escrow of SGD " + detail.escrowAmount().toPlainString();
        });

        ToggleGroup group = new ToggleGroup();
        fullRefundChip.setToggleGroup(group);
        fullPayoutChip.setToggleGroup(group);
        customChip.setToggleGroup(group);
        fullRefundChip.setSelected(true);
        group.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true);
                return;
            }
            updateAmountVisibility();
            updatePreview();
        });
        amountField.textProperty().addListener((observable, previous, text) -> updatePreview());

        boolean manual = mode == ResolutionMode.MANUAL;
        modeChips.setVisible(manual);
        modeChips.setManaged(manual);
        updateAmountVisibility();
        updatePreview();
    }

    private boolean amountIsEntered() {
        return switch (mode) {
            case REJECT -> false;
            case MANUAL -> customChip.isSelected();
            case ACCEPT -> detail.ticket().requestedRemedy() == RemedyType.PARTIAL_REFUND
                    || detail.ticket().requestedRemedy() == RemedyType.OTHER;
        };
    }

    private void updateAmountVisibility() {
        boolean entered = amountIsEntered();
        amountBox.setVisible(entered);
        amountBox.setManaged(entered);
    }

    private String refundText() {
        if (amountIsEntered()) {
            return amountField.getText();
        }
        return switch (mode) {
            case REJECT -> "0";
            case MANUAL -> fullRefundChip.isSelected() ? detail.escrowAmount().toPlainString() : "0";
            case ACCEPT -> detail.ticket().requestedRemedy() == RemedyType.FULL_REFUND
                    ? detail.escrowAmount().toPlainString() : "0";
        };
    }

    private void updatePreview() {
        ResolutionPreview.Result result = ResolutionPreview.compute(detail.escrowAmount(), refundText());
        previewLabel.setText(result.valid() ? result.summary() : result.message());
    }

    private boolean validate() {
        ResolutionPreview.Result result = ResolutionPreview.compute(detail.escrowAmount(), refundText());
        if (!result.valid()) {
            errorLabel.setText(result.message());
            return false;
        }
        if (reasonArea.getText() == null || reasonArea.getText().isBlank()) {
            errorLabel.setText("A reason is required");
            return false;
        }
        errorLabel.setText("");
        return true;
    }

    private ResolutionRequest buildRequest() {
        BigDecimal refund = ResolutionPreview.compute(detail.escrowAmount(), refundText()).refund();
        return new ResolutionRequest(mode, refund, reasonArea.getText().trim());
    }

    private String title() {
        return switch (mode) {
            case ACCEPT -> "Accept dispute";
            case REJECT -> "Reject dispute";
            case MANUAL -> "Manual settlement";
        };
    }

    private String confirmLabel() {
        return switch (mode) {
            case ACCEPT -> "Confirm accept";
            case REJECT -> "Confirm reject";
            case MANUAL -> "Apply settlement";
        };
    }
}
```

- [x] **Step 7: Run** — `.\gradlew test --tests "com.snoozeshare.ui.*"` → PASS. Then `.\gradlew build` to catch checkstyle problems in the new UI files (long lines, import order); fix any it reports.

- [x] **Step 8: Commit**

```powershell
git add src/main src/test
git commit -m "feat: dispute detail screen and Accept/Reject/Manual resolution dialog" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 16: Category administration screen

**Files:**
- Replace stubs: `ui/admin/categories/CategoryAdminController.java`, `resources/.../categories/category-admin.fxml`
- Test: extend `AdminFxmlLayoutTest`

- [x] **Step 1: Extend the failing test**

```java
    @Test
    void categoryScreenHasTheTableAndAddButton() throws Exception {
        String screen = read("categories/category-admin.fxml");

        assertTrue(screen.contains("fx:id=\"table\""));
        assertTrue(screen.contains("fx:id=\"errorLabel\""));
        assertTrue(screen.contains("Ticket categories"));
        assertTrue(screen.contains("onAction=\"#handleAdd\""));
        assertTrue(screen.contains("+ Add category"));
    }
```

- [x] **Step 2: Run to verify it fails** → the new test FAILS (placeholder FXML).

- [x] **Step 3: Write `category-admin.fxml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.admin.categories.CategoryAdminController">
    <padding><Insets top="24" right="32" bottom="24" left="32"/></padding>

    <HBox alignment="CENTER_LEFT">
        <VBox spacing="2">
            <Label text="Ticket categories" styleClass="page-title"/>
            <Label text="Shown to guests when filing a dispute ticket" styleClass="small"/>
        </VBox>
        <Region HBox.hgrow="ALWAYS"/>
        <Button text="+ Add category" onAction="#handleAdd" styleClass="button"/>
    </HBox>

    <TableView fx:id="table" VBox.vgrow="ALWAYS"/>
    <Label fx:id="errorLabel" styleClass="error-message"/>
</VBox>
```

- [x] **Step 4: Replace `CategoryAdminController`**

```java
package com.snoozeshare.ui.admin.categories;

import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.TicketCategory;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;

public final class CategoryAdminController {

    @FXML private TableView<TicketCategory> table;
    @FXML private Label errorLabel;

    private AppContext context;

    @FXML
    private void initialize() {
        TableColumn<TicketCategory, String> label = new TableColumn<>("Label");
        label.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().label()));
        label.setPrefWidth(340);
        TableColumn<TicketCategory, String> active = new TableColumn<>("Active");
        active.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().active() ? "Yes" : "No"));
        active.setPrefWidth(120);
        TableColumn<TicketCategory, Void> actions = new TableColumn<>("Action");
        actions.setPrefWidth(260);
        actions.setCellFactory(column -> new ActionCell());
        table.getColumns().add(label);
        table.getColumns().add(active);
        table.getColumns().add(actions);
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        refresh();
    }

    @FXML
    private void handleAdd() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add category");
        dialog.setHeaderText("New ticket category");
        dialog.setContentText("Label:");
        dialog.showAndWait().ifPresent(text -> apply(() -> context.ticketService().createCategory(text, me())));
    }

    private void rename(TicketCategory category) {
        TextInputDialog dialog = new TextInputDialog(category.label());
        dialog.setTitle("Rename category");
        dialog.setHeaderText("Rename \"" + category.label() + "\"");
        dialog.setContentText("Label:");
        dialog.showAndWait().ifPresent(text -> apply(
                () -> context.ticketService().renameCategory(category.categoryId(), text, me())));
    }

    private void toggle(TicketCategory category) {
        apply(() -> context.ticketService().setCategoryActive(category.categoryId(), !category.active(), me()));
    }

    private UUID me() {
        return context.session().currentUser().orElseThrow().userId();
    }

    private void apply(Runnable action) {
        try {
            action.run();
            errorLabel.setText("");
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
        }
        refresh();
    }

    private void refresh() {
        table.getItems().setAll(context.ticketService().listAllCategories());
    }

    private final class ActionCell extends TableCell<TicketCategory, Void> {
        private final Button rename = new Button("Rename");
        private final Button toggle = new Button();

        ActionCell() {
            rename.getStyleClass().add("outline-button");
            toggle.getStyleClass().add("outline-button");
            rename.setOnAction(event -> rename(category()));
            toggle.setOnAction(event -> CategoryAdminController.this.toggle(category()));
        }

        private TicketCategory category() {
            return getTableView().getItems().get(getIndex());
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                return;
            }
            toggle.setText(category().active() ? "Deactivate" : "Activate");
            setGraphic(new HBox(8, rename, toggle));
        }
    }
}
```

- [x] **Step 5: Run** — `.\gradlew test --tests "com.snoozeshare.ui.*"` → PASS; then `.\gradlew build` → checkstyle clean.

- [x] **Step 6: Commit**

```powershell
git add src/main src/test
git commit -m "feat: ticket category administration screen (F9.3.1)" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 17: FX toolkit smoke test of the real screens

No test in the repo starts the FX toolkit today. This test loads the real FXML with a real `AppContext` on the mock DB and skips itself if the toolkit cannot start (headless CI).

**Files:**
- Test: `src/test/java/com/snoozeshare/ui/admin/AdminUiSmokeTest.java`

- [x] **Step 1: Write the test**

```java
package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.service.DisputeSummary;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;
import com.snoozeshare.ui.admin.tickets.DisputeDetailController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;

class AdminUiSmokeTest {

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
        Platform.setImplicitExit(false);
    }

    /** Runs work on the FX thread; any failure (including an AssertionError) is rethrown here. */
    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }

    private static AppContext login(MockDbFixture db, String email) throws Exception {
        AppContext context = AppContext.create(db.jdbcUrl());
        User agent = context.userService().authenticate(email);
        context.session().loginAs(agent);
        return context;
    }

    @Test
    void queueShowsAllSixMockTicketsOldestFirst(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            var rows = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-queue.fxml"));
                loader.load();
                ((com.snoozeshare.ui.admin.tickets.DisputeQueueController) loader.getController())
                        .setContext(context);
                @SuppressWarnings("unchecked")
                TableView<DisputeSummary> table = (TableView<DisputeSummary>) loader.getNamespace().get("table");
                return table.getItems().stream().map(DisputeSummary::ticketId).toList();
            });

            assertEquals(java.util.List.of(MockIds.TICKET_4, MockIds.TICKET_2, MockIds.TICKET_1,
                    MockIds.TICKET_6, MockIds.TICKET_3, MockIds.TICKET_5), rows);
        }
    }

    @Test
    void detailEnablesResolutionOnlyForTheAssignedAgentAndOffersNoForceActions(@TempDir Path directory)
            throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "ben.alvarez@snoozeshare.test")) {
            boolean[] state = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                Node root = loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(context);
                controller.load(MockIds.TICKET_3);
                Button assign = (Button) loader.getNamespace().get("assignButton");
                Button accept = (Button) loader.getNamespace().get("acceptButton");
                Button reject = (Button) loader.getNamespace().get("rejectButton");
                Button manual = (Button) loader.getNamespace().get("manualButton");
                assertEquals("Accept — remedy guest", accept.getText());
                return new boolean[] {assign.isDisabled(), accept.isDisabled(), reject.isDisabled(),
                    manual.isDisabled(), containsForce(root)};
            });

            assertTrue(state[0], "assign is disabled once assigned");
            assertFalse(state[1]);
            assertFalse(state[2]);
            assertFalse(state[3]);
            assertFalse(state[4], "no control mentions a force action");
        }
    }

    @Test
    void assigningAnOpenTicketEnablesTheResolutionButtons(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = login(db, "amy.tanaka@snoozeshare.test")) {
            boolean[] state = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/admin/tickets/dispute-detail.fxml"));
                loader.load();
                DisputeDetailController controller = loader.getController();
                controller.setContext(context);
                controller.load(MockIds.TICKET_2);
                Button assign = (Button) loader.getNamespace().get("assignButton");
                Button accept = (Button) loader.getNamespace().get("acceptButton");
                boolean before = accept.isDisabled();
                assign.fire();
                return new boolean[] {before, accept.isDisabled(), assign.isDisabled()};
            });

            assertTrue(state[0], "accept disabled before assigning");
            assertFalse(state[1], "accept enabled after assigning");
            assertTrue(state[2], "assign disabled after assigning");
        }
    }

    private static boolean containsForce(Node node) {
        if (node instanceof Labeled labeled && labeled.getText() != null
                && labeled.getText().toLowerCase().contains("force")) {
            return true;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsForce(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
```

Tidy when saving: replace every fully-qualified name (`com.snoozeshare.ui.admin.tickets.DisputeQueueController`, `java.util.List`, `javafx.scene.Parent`) with an import; wrap lines over 120; `ScrollPane` content is not in `getChildrenUnmodifiable` — if `containsForce` misses nested content, read `scrollPane.getContent()` too (add a `ScrollPane` branch).

- [x] **Step 2: Run**

Run: `.\gradlew test --tests "com.snoozeshare.ui.admin.AdminUiSmokeTest"`
Expected: PASS (3 tests) on a machine with a display; **skipped** (not failed) if the toolkit cannot start. Report which one happened. If a test *fails* on the real toolkit, the FXML/controller wiring has a bug — fix the source (id typo, missing `@FXML`), not the test.

- [x] **Step 3: Commit**

```powershell
git add src/test/java/com/snoozeshare/ui/admin/AdminUiSmokeTest.java
git commit -m "test: FX toolkit smoke test for agent dispute screens" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 18: Full verification, visual acceptance, and record-keeping

**Files:** `PROJECT_STATE.md`, `docs/project-state/done-ledger.md`, `docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md`, `docs/superpowers/plans/…` (checkboxes)

- [ ] **Step 1: Full build**

Run: `.\gradlew clean build`
Expected: `BUILD SUCCESSFUL`; the number of tests is the baseline from Task 0 plus the new ones; no checkstyle errors (warnings allowed). Paste the summary line into your report.

- [ ] **Step 2: Visual acceptance against the design canvas** (test type 12 in the spec — this is a manual step)

```powershell
Copy-Item db\snoozeshare-mock.db build\acceptance.db -Force
$env:SNOOZESHARE_DB_URL = "jdbc:sqlite:build/acceptance.db"
.\gradlew run
```

Log in as `amy.tanaka@snoozeshare.test` (Agent). Walk this checklist and note each result:

1. Sidebar shows `Operations / Disputes / Accounts / Categories`. Disputes opens a table of 6 tickets, **oldest first**, with a "1 unassigned" badge.
2. Filter chips `All / Unassigned / Mine` and the status dropdown change the rows.
3. Click ticket `#0002`: detail shows booking summary (Beachfront Bungalow, SGD 875.00 held, "Stay ended — escrow held"), two chat panes, notes box, `Accept` / `Reject dispute` / `Manual adjustment…` **disabled**, `Assign to me` enabled. No Force controls anywhere.
4. Send a chat message in each pane; it appears as an agent bubble.
5. Assign to me → badge/status change, action buttons enable. Add a note; it appears in the history.
6. Accept (ticket `#0002` is `OTHER`, so an amount field shows): enter `175`, watch the live preview read `Guest refund SGD 175.00 | Host payout SGD 679.00 (fee SGD 21.00)`; leave the reason empty → blocked with a message; enter a reason → ticket resolves, buttons disable, escrow shows "Settled".
7. Reopen `#0003` as Ben (log out, log in as `ben.alvarez@snoozeshare.test`): try **Manual adjustment…** with the three chips and Custom.
8. Categories: add, rename, deactivate a category; duplicate label shows an error.

Compare layout/labels with the canvas artboards (`AgentDisputeQueue`, `AgentDisputeDetail`, `AgentTicketCategories`, the three `ConfirmDispute*`). Expected differences, all documented: sidebar instead of tabs (D11), navy palette instead of the canvas "Fall Light" tokens (separate UI-design workstream), no Force block (C22), refund-amount field instead of "Adjust wallet" (C20), oldest-first order (F9.1.1). Anything else that differs is a defect — fix it or record it.

Clear the env var afterwards: `Remove-Item Env:SNOOZESHARE_DB_URL`.

- [ ] **Step 3: Apply the spec amendments** (edit the spec so it matches what was built)

In `docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md`:
- § 4.2: replace the `DisputeSettlementService` snippet with `Settlement settle(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId, String reason)`, remove `SettlementKind`, and add a `DisputeQueryService` paragraph (`queue`, `detail`).
- § 4.4: note `MigrationRunner` adopts a pre-provisioned DB, and that the mock seed timestamps now end in `Z` like the app's own writes.
- § 4.5: state the shell is sidebar-based and the palette is the current `theme.css`.
- § 5.2 row 10: replace "TestFX" with "FX toolkit smoke test (skips when the toolkit cannot start) + file-content layout tests + pure `ResolutionPreview` tests".
- § 6: add rows **D10** (`MigrationRunner` adopts a pre-provisioned DB) and **D11** (sidebar shell, current palette).

- [ ] **Step 4: Update `PROJECT_STATE.md`**

- § Workstreams W10: `Status` → `In review`; `Progress` → `Tasks 1–18 done; awaiting review and merge`; leave `Guide` as `—` for now.
- § Architecture: §4.3 Service — add `TicketServiceImpl`, `DisputeSettlementServiceImpl`, `DisputeQueryServiceImpl`, temporary `InMemoryMessageService`; §4.4 Domain — add `domain.settlement` (`SettlementCalculator`, `EscrowPolicy`) and the `AGENT` completion permission; §4.5 Repository — add `JdbcTicketRepository`, `JdbcTicketCategoryRepository`, `MigrationRunner` adoption; §4.2 UI — add the agent Disputes/Categories screens and how to run against a DB copy (`SNOOZESHARE_DB_URL`).
- § Deviations: add D10 and D11 (short, linking to the spec § 6).
- § Handoffs into W3: update item 2 to note that W3's branch stubs `applyTicketRemedy`/`manualOverride` as `"Owned by W10"` — at merge, either delegate them to `DisputeSettlementService` or leave them unsupported; W10 never edits `TransactionServiceImpl`.
- § How to Resume: S4 row → `Paused`, `Doing` → "W10 implemented; in review. Next: operator review, then merge; W10 Guide checkpoint after operator confirms".
- § Record: entries count.

- [ ] **Step 5: Add Done-ledger entries** (newest first, in `docs/project-state/done-ledger.md`)

One line each: "Implemented W10 Agent Dispute Resolution (F9.1.1, F9.1.2, F9.2.2, F9.3.1): atomic full-escrow settlement, ticket queue/assign/notes/categories, dispute read models, temporary in-memory chat, Agent Disputes and Categories screens"; "Made MigrationRunner adopt a pre-provisioned DB (needed to open the mock DB)"; "Allowed agents on CONFIRMED→COMPLETED in BookingStateMachine (C23)". Refs: this plan and the spec. Update the entry count in § Record.

- [ ] **Step 6: Tick the plan's checkboxes and commit**

```powershell
git add PROJECT_STATE.md docs
git commit -m "docs: record W10 implementation, deviations D10-D11, and ledger entries" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

- [ ] **Step 7: Independent review, then hand back**

Invoke `superpowers:requesting-code-review` on the whole branch (`git diff main...HEAD`) with the spec and this plan as the requirements. Fix Critical/Important findings (each fix gets a commit and a Done-ledger line). Then report to the operator: build/test results, the visual-acceptance checklist results, anything skipped (for example if the FX smoke test skipped), and ask whether W10 is confirmed `Done` so the Guide column can move to `Pending` per AGENTS.md § 5. Do **not** merge or open a PR unless the operator asks.

---

## Self-review (run against the spec)

**Spec coverage**

| Spec requirement | Task |
|---|---|
| § 3.1.1 queue oldest-first + All/Unassigned/Mine + status filter | 5 (SQL), 9 (service), 11 (read model), 14 (UI) |
| § 3.1.2 detail: summary, chat panes, notes, actions | 11, 15 |
| § 3.1.3 Accept / Reject / Manual dialogs, reason required, live preview, amount field | 13 (preview), 15 |
| § 3.1.4 assign (`OPEN → UNDER_REVIEW`) and notes (F9.1.2) | 9 |
| § 3.1.5 category admin (F9.3.1) | 5 (repo), 9 (service), 10 (tests), 16 (UI) |
| § 3.1.6 one audit row per mutation | 8 (`TICKET_RESOLVED`), 9 (`TICKET_ASSIGNED`, `TICKET_NOTE_ADDED`, category actions) |
| § 4.3 settlement semantics table, preconditions, one transaction, events after commit, ledger row types, fee math, `COMPLETED` (C23) | 1, 2, 8 |
| § 4.4 persistence (no schema change) | 5; parity guard 4 |
| § 4.6 wiring | 12 |
| C21 chat via `MessageService`, temporary in-memory impl | 6, 15 |
| C22 no force actions | 14, 15 (`noForceAction…` layout tests), 17 |
| § 5.1 mock-DB fixture, invariants, fixed clock | 3, 8 |
| § 5.2 test types 1–12 | 1 (1), 2 (2), 9–11 (3), 5 (4), 8–9 (5), 8 (6, 7: double-resolve and wrong-agent in `DisputeSettlementServiceTest`), 4 (8), existing `UiDependencyTest` + new layout tests (9), 13–17 (10), 12 (11), 18 (12) |
| § 7 acceptance criteria 1–9 | 18 (build + visual checklist) |

**Placeholder scan:** no "TBD/TODO"; every code step contains code. Two "tidy when saving" notes (imports/line length) tell the implementer exactly what checkstyle needs.

**Type consistency check:** `ResolutionMode {ACCEPT, REJECT, MANUAL}`, `AssigneeFilter {ALL, UNASSIGNED, MINE}`, `ThreadChannel {GUEST, HOST}` used identically in Tasks 5–17. `DisputeSettlementService.settle(ticketId, mode, guestRefund, agentId, reason)` matches the impl (Task 8), `Fakes.RecordingSettlement` (Task 9), and `TicketServiceImpl.resolve` (Task 9). `Settlement(ticket, booking, breakdown, guestTransaction, hostTransaction)` matches Tasks 7, 8, 9. `TicketService` signatures in Task 7 match `TicketServiceImpl` (Task 9) and the UI calls (Tasks 15–16). `DisputeDetail` fields (Task 7) match `DisputeQueryServiceImpl` (Task 11) and the controllers (Task 15): `ticket, ticketLabel, listingTitle, startDate, endDate, guestName, hostName, raisedByName, assignedAgentName, bookingStatus, escrowAmount, escrowHeld, phaseLabel`. `MockDbFixture` methods used in tests: `open, connection, jdbcUrl, scalarLong, scalarString, execute, walletBalance, assertLedgerInvariant, close`.

**Known soft spots to watch during execution:** (1) `MockDbFixtureTest` uses `SELECT COUNT(*) FROM pragma_foreign_key_check` — if the bundled SQLite build lacks that table-valued pragma, replace it with a `PRAGMA foreign_key_check` statement and count rows; (2) Task 17's `containsForce` may need a `ScrollPane` branch; (3) the mock DB `updatedAt` on wallets keeps its old format — harmless because settlement rewrites it with `Instant.toString()`.
