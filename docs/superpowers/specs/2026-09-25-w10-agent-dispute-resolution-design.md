# W10 — Agent Dispute Resolution (F9) Design Spec

**Workstream:** W10 · **Branch:** `agent-dispute-resolution-and-state-overrides` · **Date:** 2026-09-25
**Backlog:** F9.1.1, F9.1.2, F9.2.2, F9.3.1 (F9.2.1 dropped — see § 2, C22)
**Visual source of truth:** [design canvas](https://claude.ai/artifact/PWBCxbfv9e9FGVvY6RKUwd) — artboards `AgentDisputeQueue`, `AgentDisputeDetail`, `AgentTicketCategories`, `ConfirmDisputeAction`, `ConfirmDisputeReject`, `ConfirmDisputeManualAdjustment`. Where the operator's decisions in § 2 differ from an artboard, the decisions win (C20).

## 1. Goal

Give the Support Agent a working **Disputes** tab and **Categories** tab. An agent can see the queue of
dispute tickets (oldest first), take one, read the guest and host evidence and chat threads, add
internal notes, and resolve the ticket by settling the booking's held escrow. Agents can also
create, rename, and deactivate the ticket categories guests choose from.

## 2. Decisions this design rests on

All recorded in `PROJECT_STATE.md`; restated here so the spec reads alone.

| ID | Decision |
|---|---|
| C16 | W10 is built against service interfaces, concurrently with W3/W4. Money logic lives in W10's own class, not in W3's `TransactionServiceImpl`. Shared touchpoints are limited to `AppContext` wiring and one additive `BookingStateMachine` change. |
| C17 | Escrow is held through the 7-day dispute window (checkout + 7 days). A ticket opened in the window keeps escrow held and the agent controls its settlement. Agent overrides therefore always act on **held escrow**; there is no clawback. The W3/W8 auto-complete trigger (F7.3.1) must skip bookings with an open ticket. |
| C18 / C20 | Resolution settles the **full** held escrow. Guest refund `R` carries no fee. Host receives `(escrow − R) × 0.97`. The 3% cut is only ever taken from host earnings, never guest money. |
| C21 | Chat threads are required. A general `MessageService`, owned by new workstream **W13 (Messaging)**, owns them. W10 codes against the interface. |
| C22 | Force Cancel / Force Complete (F9.2.1) are **out of scope**: accepting or rejecting a ticket already closes it. |
| C23 | Resolution moves the booking `CONFIRMED → COMPLETED`. `BookingStateMachine` gains `Role.AGENT` on that transition. |
| C7 | Escrow amount = `bookings.totalAmount` (no guest-side fee). |
| C9 | Superseded for W10 by C20: agent settlement is two-sided (guest row + host row), not single-sided. |

## 3. Scope

### 3.1 In scope

1. **Dispute queue** — tickets sorted by `createdAt` ascending (F9.1.1; the artboard shows newest-first, the backlog says chronological, the backlog wins). Filters: All / Unassigned / Mine. Status filter: Open / In review / Resolved. Row shows ticket, subject (`title`), booking, guest/host names, assignee, status badge.
2. **Dispute detail** — booking summary (listing, dates, guest, host, escrow held, derived booking phase), guest evidence pane and host response pane (thread from `MessageService`), internal-notes editor, action bar: **Assign to me**, **Accept**, **Reject**, **Manual adjustment…**.
3. **Resolution dialogs** — Accept, Reject, Manual adjustment (§ 4.3), each requiring a reason.
4. **Assign & notes** (F9.1.2) — `OPEN → IN_REVIEW` on assign; agent notes append to `agentNotes`.
5. **Category administration** (F9.3.1) — list, add, rename, activate/deactivate.
6. **Audit** — one `AuditService.record` per mutation.

### 3.2 Non-goals

| Not built | Owner / reason |
|---|---|
| Force Cancel / Force Complete (F9.2.1) | Dropped by operator, C22. The backlog line and the two `ConfirmForce*` artboards are out of scope. |
| Guest ticket filing UI, host response UI | W4 / W8 |
| Persistent chat storage, chat for non-ticket conversations, Messages tabs | W13 |
| Account suspension, audit log viewer | W11, W12 |
| Auto-complete scheduler (F7.3.1) | W3/W8. W10 only states the requirement: skip bookings with an open ticket (C17). |
| Real `TransactionService.applyTicketRemedy` / `manualOverride` | Their single-sided signatures do not fit C20; W10 does not implement or call them (see § 6, D5). |

## 4. Design

### 4.1 Layering

```text
ui.admin.tickets / ui.admin.categories        (controllers + FXML)
        │ depends on service interfaces only
service: TicketService, DisputeSettlementService, MessageService(interface), AuditService
        │
domain: TicketStateMachine, BookingStateMachine (+AGENT→COMPLETED), settlement math
        │
repository: TicketRepository, TicketCategoryRepository, BookingRepository, WalletRepository, WalletTransactionRepository
        └─ jdbc: JdbcTicketRepository, JdbcTicketCategoryRepository (new)
```

### 4.2 Service contracts

**`TicketService`** (existing interface, extended additively; implemented by new `TicketServiceImpl`)

| Method | Behaviour |
|---|---|
| `queueForAgent(TicketStatus, AssigneeFilter, UUID agentId)` | Replaces the single-arg form. Sorted `createdAt` ascending. `AssigneeFilter` = `ALL`, `UNASSIGNED`, `MINE`. |
| `assignToMe(UUID ticketId, UUID agentId)` | `OPEN → IN_REVIEW`, sets `assignedAgentId`. Fails if already assigned to another agent or not `OPEN`. |
| `addAgentNote(ticketId, note, agentId)` | Existing. Appends a timestamped, attributed entry to `agentNotes`. Ticket must be `IN_REVIEW` and assigned to the caller. |
| `resolve(ticketId, ResolutionRequest, agentId)` | See § 4.3. Replaces the `(approve, remedy, amount, reason)` overload. |
| `listCategories()` | Existing (active only, for guests). |
| `listAllCategories()`, `createCategory(label, agentId)`, `renameCategory(id, label, agentId)`, `setCategoryActive(id, active, agentId)` | Category admin. Label unique (case-insensitive) and non-blank. |

`addHostResponse` is superseded by `MessageService` and is deprecated, not implemented, in W10.

**`MessageService`** — interface **owned by W13**. W10 adds the file only so it can compile against it:

```java
List<Message> thread(UUID ticketId, ThreadChannel channel);      // channel = GUEST | HOST
Message post(UUID ticketId, ThreadChannel channel, UUID authorId, Role authorRole, String body);
```

W10 ships `InMemoryMessageService` (non-persistent, session-only) wired in `AppContext` so the chat panes work now.
It is explicitly temporary; W13 replaces it with a persistent implementation, no UI change required.

**`DisputeSettlementService`** (new interface + `DisputeSettlementServiceImpl`) — the only class that moves money for W10.

```java
Settlement settle(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId, String reason);
```

The ledger row type and ticket end status are derived from `ResolutionMode` (Accept / Reject / Manual) and the refund amount; there is no separate `SettlementKind`.

**`DisputeQueryService`** (new interface + `DisputeQueryServiceImpl`) - read models for the Agent UI so controllers never touch repositories: `queue(...)` returns queue rows (ticket, booking and party summaries, oldest first, with the assignee filter applied) and `detail(ticketId)` returns the booking summary, derived phase label, escrow state and notes for the detail screen.

### 4.3 Resolution semantics

Let `E` = booking `totalAmount` (escrow held). Let `R` = guest refund, `0 ≤ R ≤ E`. Let `H = E − R` be the host's gross share.

| Action | Requested remedy (ticket) | `R` | Ledger rows | Ticket status |
|---|---|---|---|---|
| **Accept** | `FULL_REFUND` | `E` | guest `TICKET_REMEDY` | `RESOLVED_APPROVED` |
| | `PARTIAL_REFUND`, `OTHER` | agent-entered amount | guest `TICKET_REMEDY`, host `BOOKING_PAYOUT` | `RESOLVED_APPROVED` |
| | `HOST_PAYOUT` | `0` | host `BOOKING_PAYOUT` | `RESOLVED_APPROVED` |
| **Reject** | any | `0` | host `BOOKING_PAYOUT` | `RESOLVED_REJECTED` |
| **Manual adjustment** | any | Full refund = `E`; Full payout = `0`; Custom = agent-entered | guest `AGENT_OVERRIDE` (if `R > 0`), host `BOOKING_PAYOUT` (if `H > 0`) | `RESOLVED_APPROVED` if `R > 0`, else `RESOLVED_REJECTED` |

Rules:

- Host row: `amount = H − fee`, `feeAmount = round(H × 0.03, 2, HALF_UP)`. The fee is informational per the existing Known Gap (no platform wallet). Both rows carry `relatedBookingId` and `relatedTicketId`, `initiatedBy = agentId`.
- A zero-amount row is never written. A guest refund of `0` or a host share of `0` simply omits that row.
- **Preconditions** (all checked inside the transaction, failure ⇒ nothing written): caller has `Role.AGENT`; ticket is `IN_REVIEW` and assigned to the caller; booking is `CONFIRMED`; escrow is currently **held**; `0 ≤ R ≤ E`; reason non-blank.
- **Escrow held** ≡ the booking has an `ESCROW_HOLD` row and no `ESCROW_REFUND`, `BOOKING_PAYOUT`, `TICKET_REMEDY`, or `AGENT_OVERRIDE` row.
- **One DB transaction** covers: both wallet balance updates, both ledger inserts, ticket update (`status`, `resolutionReason`, `resolvedAt`), booking update (`COMPLETED`, `completedAt`), and the audit row. Events (`TicketResolvedEvent`, `WalletTransactionRecordedEvent`) are published only after commit.
- Booking transition goes through `BookingStateMachine.canTransition(CONFIRMED, COMPLETED, AGENT)` (C23); ticket transition through `TicketStateMachine`.
- Money is `BigDecimal`, scale 2, `HALF_UP`. All assertions on SQLite-read amounts use `compareTo` (D4).

### 4.4 Persistence

No schema change. `tickets` and `ticket_categories` already exist in `V001__foundation.sql`.

- `JdbcTicketRepository` — `findById`, `findByStatus`, `findQueue(status, assigneeFilter, agentId)`, `save` (upsert). Row mapping via shared `RowMappers`.
- `JdbcTicketCategoryRepository` — `findActive`, `findAll`, `findById`, `existsByLabel(label)`, `save`.
- `MigrationRunner` adopts a pre-provisioned database (D10): the mock DB has tables but no `schema_history`, so the runner records the baseline instead of re-running `V001` against existing tables. The mock seed timestamps now end in `Z`, matching the app's own `Instant.toString()` writes, so `JdbcCodecs.instant` can parse them.
- A read query `WalletTransactionRepository.findByBooking(bookingId)` (additive if not already present) supports the escrow-held check.
- `tickets.category` stores the label text, not an id. Renaming a category does **not** rewrite existing tickets; deactivating hides it only from new filings.

### 4.5 UI (`ui.admin`)

The admin shell is sidebar-based (Operations / Disputes / Accounts / Categories) and uses the current navy `theme.css`, not the canvas's tabs and Fall Light palette (D11). The *Disputes* and *Categories* bodies are replaced.

| Class / FXML | Artboard | Notes |
|---|---|---|
| `ui.admin.tickets.DisputeQueueController` + `dispute-queue.fxml` | AgentDisputeQueue | Table, All/Unassigned/Mine chips, "N unassigned" badge. |
| `ui.admin.tickets.DisputeDetailController` + `dispute-detail.fxml` | AgentDisputeDetail | Booking summary card, two chat panes (guest / host), notes, action bar. **No "Booking state override" block** (C22). Action label follows the raiser: "Accept — remedy guest" / "Accept — remedy host". |
| `ui.admin.tickets.ResolutionDialogController` + `resolution-dialog.fxml` | ConfirmDisputeAction / Reject / ManualAdjustment | One dialog, three modes. Accept: amount field (prefilled for `FULL_REFUND`/`HOST_PAYOUT`, entered for `PARTIAL_REFUND`/`OTHER`). Manual: Full refund / Full payout / Custom chips. All modes show **live** "Guest refund $R · Host payout $H−fee (fee $f)" preview. Replaces the artboard's "Adjust wallet" dropdown (C20). Reason required. |
| `ui.admin.categories.CategoryAdminController` + `category-admin.fxml` | AgentTicketCategories | Table, active toggle, Add / Edit dialogs. |

Derived booking phase label shown on the detail card: **"Stay ended — escrow held"** (`CONFIRMED`, `endDate < today`, escrow held) or **"Active"** / **"Upcoming"** otherwise. It is a display label only, not a status.

Controllers depend only on `service.*` interfaces. Views refresh via `TicketResolvedEvent` subscriptions (unsubscribed on tab dispose).

### 4.6 Wiring

`AppContext` gains `TicketService`, `DisputeSettlementService`, `MessageService` (in-memory), the two new JDBC repositories, and passes them to `AdminShellController`. This is the only file W3/W4 also edit; merge conflicts there are expected to be trivial additions.

## 5. Testing strategy

TDD-first, service layer first. The team's shared mock database `db/snoozeshare-mock.db` is the integration fixture; the committed file is **never mutated by tests** (they work on a temp copy).

### 5.1 The mock-DB fixture (`MockDbFixture`)

1. Copies `db/snoozeshare-mock.db` to a temp file per test class (JUnit `@TempDir`), opens it via the real `ConnectionFactory`.
2. **No normalisation step.** The committed mock DB itself was corrected to follow C17/C20/C23 on 2026-09-25 (§ 6, D6): the copy exists only so mutating tests never write to the committed file. Tests read the data exactly as the team sees it.
3. Asserts the **ledger invariant** (`wallets.balance = Σ wallet_transactions` per wallet, and each row's `balanceAfter` equals the chronological running sum) on the fresh copy and again after every mutation test.
4. Injects a fixed `Clock` (`2026-09-25`) so "stay ended" logic is deterministic.

**Mock rows used** (all in the committed DB):

| Ticket | Status | Booking (total) | Booking status / ledger | Used for |
|---|---|---|---|---|
| #2 | `OPEN`, unassigned | 9 (875.00, stay ended 08-06) | `CONFIRMED`; `ESCROW_HOLD` only | Queue "Unassigned" filter; assign flow; resolve-Accept/Reject/Manual scenarios (mutating tests, on the temp copy) |
| #3 | `IN_REVIEW`, assigned to Ben Alvarez | 11 (210.00, stay ended 09-04) | `CONFIRMED`; `ESCROW_HOLD` only | "Mine" filter (as Ben); resolve scenarios; not-assigned-to-caller rejection when run as another agent |
| #1 | `RESOLVED_APPROVED` | 10 (480.00) | `COMPLETED`; hold + `TICKET_REMEDY` 100.00 + host payout 368.60 (fee 11.40) | Read-only reference for the Accept-partial ledger shape; already-resolved rejection |
| #4 | `RESOLVED_APPROVED` | 13 (330.00) | `COMPLETED`; hold + `AGENT_OVERRIDE` 165.00 + host payout 160.05 (fee 4.95) | Read-only reference for the Manual-adjustment ledger shape |
| #5, #6 | `RESOLVED_REJECTED` | 8, 6 (cancelled bookings) | escrow already refunded | Rejection of settlement on a non-`CONFIRMED` / non-held booking |

Plus 6 categories (all active), and the guest/host/agent users and wallets. Scenarios that need shapes the mock does not have (for example a host-raised ticket on a held booking, or a deactivated category) insert extra rows inside the test on the temp copy.

### 5.2 Test types

| # | Type | Scope | Data | What is asserted |
|---|---|---|---|---|
| 1 | **Unit — domain** | `TicketStateMachine`; `BookingStateMachine` `CONFIRMED→COMPLETED` for AGENT (allowed) and GUEST/HOST unchanged; `FORCE_*` unaffected | none | Every legal/illegal transition, per role; no regression to existing table |
| 2 | **Unit — settlement math** | pure `SettlementCalculator` | table-driven | `R` boundaries (0, `E`, partial), fee rounding `HALF_UP`, `R + H = E`, host net = `H − fee`, no zero rows, `R > E` / negative rejected |
| 3 | **Unit — service (fakes)** | `TicketServiceImpl`, `DisputeSettlementServiceImpl` with in-memory repos/fakes | in-memory | Queue order ascending + filters; assign rules (already assigned, not OPEN); notes gate; every precondition in § 4.3 rejects with no writes; category label uniqueness/blank; agent-role authorization; one audit call per mutation; events only after commit |
| 4 | **Repository integration** | `JdbcTicketRepository`, `JdbcTicketCategoryRepository`, escrow-held query | mock DB copy | Round-trip of all 15 ticket fields; queue SQL ordering/filter; upsert; category CRUD; enum mapping; `Instant` parsing |
| 5 | **Service integration** | `TicketServiceImpl` + real JDBC + `InProcessEventBus` | mock DB copy (tickets #2 and #3 on held bookings 9 and 11) | Accept full/partial/host-payout, Reject, Manual full-refund/full-payout/custom: exact ledger rows (type, amount, fee, related ids, `balanceAfter`), wallet balances, ticket + booking end states, `completedAt`, audit row content, ledger invariant |
| 6 | **Atomicity / failure** | forced failure mid-settlement (throwing repository decorator) | mock DB copy | Full rollback: no partial rows, balances/status unchanged, no event published |
| 7 | **Concurrency / idempotency** | double resolve; resolve after another agent assigned; resolve an already-settled booking | mock DB copy | Second attempt fails cleanly; escrow never paid twice |
| 8 | **Schema parity** | `V001__foundation.sql` vs `db/schema.sql` for `tickets`, `ticket_categories`, `wallet_transactions`, `bookings` | both | Same columns/constraints — guards against the hand-written mock DB drifting (D6) |
| 9 | **Architecture** | existing `LayerDependencyTest` + new rule | source | `ui.admin.*` imports no `repository.*`/`infra.db.*`; `java.sql` only in `repository.jdbc` |
| 10 | **UI** | queue, detail, resolution dialog, categories controllers with fake services | in-memory + one mock-DB-backed smoke. Implemented as an FX toolkit smoke test (skips when the toolkit cannot start) + file-content layout tests + pure `ResolutionPreview` tests | Queue renders oldest-first; filter chips; Assign→enabled actions; dialog validation (reason required, amount ≤ escrow, live preview numbers); action label by raiser; no force controls present; category toggle/add/edit; chat panes render `MessageService` output |
| 11 | **End-to-end smoke** | `AppContext` bootstrapped on mock DB copy, agent logged in | mock DB copy | Queue → assign → accept → ticket shows resolved, wallet history updated, refresh event received |
| 12 | **Visual acceptance (manual)** | run app vs canvas artboards | mock DB | Checklist in the plan: layout, tokens, labels, statuses match the six artboards except the documented deviations |

`.\gradlew test` must pass with all of 1–11; item 12 is a signed-off checklist in the final review.

## 6. Deviations & discoveries carried into `PROJECT_STATE.md`

| ID | Item | Handling |
|---|---|---|
| D5 | W10 adds a dedicated `DisputeSettlementService` instead of using `TransactionService.applyTicketRemedy` / `manualOverride`, whose single-sided signatures cannot express C20's two-sided full-escrow settlement. Departs from the convention that `TicketService` calls `TransactionService`. | Reconcile with W3 at merge: either fold `settle` into `TransactionService` or leave both. |
| D6 | The mock DB is hand-written from `db/schema.sql` (not generated from migrations) and held rows predating C17/C20: booking 9 paid out with an open ticket, ticket 4/booking 13 single-sided and `FORCE_COMPLETED`, tickets filed outside the 7-day window, a broken `balanceAfter` chain on wallet 5. | **Resolved:** corrected in place per operator direction — `seed-mock-data.sql` edited and `snoozeshare-mock.db` rebuilt from `schema.sql` + seed, ledger invariants and FK check verified. Tests use a temp copy only for isolation; a schema-parity test guards future drift. |
| D7 | Design artifact shows Force actions, a queue sorted newest-first, an "Adjust wallet" dropdown, and no Accept amount field. | Superseded by C22, F9.1.1, C20, and § 4.3 respectively. |
| D8 | `tickets.category` is label text, not a foreign key. | Renames do not propagate to existing tickets; accepted. |
| D9 | `BookingStateMachine` allowed only HOST on `CONFIRMED → COMPLETED`. | Additive AGENT permission (C23); W3 must be told when merging. |
| D10 | `MigrationRunner` refused a database that has tables but no `schema_history` (the mock DB). | It now adopts a pre-provisioned DB by recording the baseline; needed to open the mock DB in the app. |
| D11 | The mockups show a tabbed shell with the Fall Light palette; the built admin shell is sidebar-based and uses the current navy `theme.css`. | Accepted; aligning with the canvas is a separate UI-design workstream. |

## 7. Acceptance criteria

1. An agent logged in via the mocked session sees the Disputes queue built from the mock DB, oldest ticket first, and can filter All / Unassigned / Mine.
2. Assigning an `OPEN` ticket makes it `IN_REVIEW` and assigned to the caller; a second agent cannot take it.
3. Accept, Reject, and Manual adjustment each settle the **entire** held escrow, write the exact ledger rows in § 4.3, set the ticket and booking (`COMPLETED`) end states, and leave the ledger invariant intact.
4. Any precondition failure or mid-flight error leaves the database unchanged and publishes no event.
5. A reason is required on every resolution and is stored in `resolutionReason` and the audit log.
6. Categories can be created, renamed, and deactivated; deactivated categories no longer appear in `listCategories()`.
7. No Force Cancel / Force Complete control exists anywhere in the Agent UI.
8. Chat panes show guest and host threads through `MessageService`; W10 works with the in-memory implementation.
9. `.\gradlew build` (including checkstyle) and `.\gradlew test` pass.

## 8. Follow-up artifacts

- Implementation plan: `docs/superpowers/plans/2026-09-25-w10-agent-dispute-resolution.md` (next, via `writing-plans`).
- W13 Messaging spec (separate workstream; consumes the `MessageService` interface defined here).
- `docs/ProductBacklog.md` updated 2026-09-25 (operator approved): F9.2.1 dropped, F9.2.2 / F9.1.1 / F7.3.1 reworded, new epic F12 Messaging.
- W3 handoffs are recorded in `PROJECT_STATE.md` § Workstreams → *Handoffs into W3*.
- W3/W8: honour C17 (skip auto-complete on open tickets) and reconcile D5/D9.
