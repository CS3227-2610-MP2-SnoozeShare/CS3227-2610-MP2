# W12 — Platform Audit Trail (F11) — Design

**Status:** Approved by the operator 2026-09-26 (defaults in § 10 accepted; account-governance addendum in § 4a). **Branch:** `agent-platform-audit-trail`.
**Backlog:** F11.1.1, F11.1.2, F11.1.3 (the "Analytics" half of the epic title has no items; out of scope).
**Related decisions:** C28–C31 in `PROJECT_STATE.md`; supersedes D2 point 2 (see § 8).

## 1. Problem

`audit_log` stores one free-form `afterState` blob per row (today via `Object.toString()`, so not
even valid JSON) that mixes status, money and reason. `TICKET_RESOLVED` alone carries status,
mode, refund, payout, fee and reason. This cannot be filtered or sorted, hides who was affected,
and one row describes four changes. Booking transitions, wallet movements and ticket creation are
not audited at all (F11.1.1 and F11.1.2 are unmet). The Agent "Audit Log" tab is a placeholder.

## 2. Goals / non-goals

**Goals**
1. One row records exactly one change, with typed columns for status, money and reason.
2. Every booking transition, every wallet movement, and every ticket lifecycle step writes rows in the
   same DB transaction as the change.
3. Agents can find rows by user (id or name), booking, ticket, action type and date range.
4. An Agent "Audit Log" screen matching the design board and the W10 agent UI.

**Non-goals (W12)**
- Folding `wallet_transactions` into the log, `users.balance`, a platform wallet: **W14** (C30/C31).
- Emitting account-governance rows (W11 implements, § 4a) and auditing host availability blocks (W7): not
  instrumented now; the schema and `AuditAction` already accommodate them.
- Analytics/charts. Editing or deleting rows (the log is append-only).

## 3. Log structure (operator-decided, C28)

`audit_log` after migration `V002__audit_trail.sql` (all new columns nullable):

| Column | Type | Meaning |
|---|---|---|
| `logId` | TEXT PK | unchanged |
| `actorUserId` | TEXT NOT NULL FK users | who did it (the seeded System user for system actions) |
| `actorName` | TEXT | display-name snapshot at write time (renames never rewrite history); always set by the service, null only on legacy rows |
| `actionType` | TEXT NOT NULL | one `AuditAction` name (§ 4) |
| `entityType` | TEXT NOT NULL | `Booking`, `Ticket`, `WalletTransaction`, `Property`, `TicketCategory`, `User` (normalised; today `PROPERTY`/`Property` are mixed) |
| `entityId` | TEXT NOT NULL | id of the one entity that changed |
| `beforeState` | TEXT | **status text only**, e.g. `IN_REVIEW`; null when none/creation |
| `afterState` | TEXT | **status text only**; null when the row is not a status change |
| `walletAdjustment` | REAL | signed SGD amount for wallet rows only (credit +, debit −); null otherwise |
| `reason` | TEXT | human-readable why / detail; agent-entered reason where one exists |
| `subjectUserId` | TEXT FK users | the user whose booking/wallet/ticket changed (may equal the actor) |
| `subjectName` | TEXT | display-name snapshot of the subject |
| `bookingId` | TEXT FK bookings | set on every row concerning a booking (booking, its ticket, its wallet rows) |
| `ticketId` | TEXT FK tickets | set on ticket rows, and on wallet rows caused by a ticket |
| `timestamp` | TEXT NOT NULL | UTC ISO-8601 `Z` (`JdbcCodecs.instant`) |

Indexes: `timestamp`, `actorUserId`, `subjectUserId`, `bookingId`, `ticketId`. The domain record
`AuditLogEntry` gains the same fields (`walletAdjustment` as `BigDecimal`; SQLite REAL round-trips
lose scale, so tests use `compareTo` per D4).

**Rules**
- **One change per row.** A row never has both a status change and a wallet adjustment.
- **Money rows** use `entityType = WalletTransaction`, `entityId` = the `wallet_transactions.transactionId`
  (so W14 can adopt them without rework), `beforeState/afterState = null`, `walletAdjustment` = the
  amount actually applied to that wallet (host payout is **net** of the 3% fee; the fee is
  written into `reason`, e.g. `Payout net of 3% platform fee (11.40)`; there is no platform wallet, C30).
- **Non-status changes** (listing edit, category rename, note saved): states are null and `reason`
  describes the change without embedding content (note text is not logged).
- Rows from one action share one `Instant`; ties are ordered by insertion (`rowid`), so a resolution
  reads in causal order.
- Append-only: no update/delete path exists in `AuditLogRepository`.

### Worked example — agent resolves ticket T (Accept, refund 100 of 480 escrow)

| # | actionType | entity | before → after | walletAdjustment | subject | bookingId / ticketId |
|---|---|---|---|---|---|---|
| 1 | `TICKET_RESOLVED` | Ticket T | `IN_REVIEW` → `RESOLVED_APPROVED` | — | guest (ticket raiser) | B / T |
| 2 | `BOOKING_COMPLETED` | Booking B | `CONFIRMED` → `COMPLETED` | — | guest | B / T |
| 3 | `TICKET_REMEDY` | WalletTransaction | — | +100.00 | guest | B / T |
| 4 | `BOOKING_PAYOUT` | WalletTransaction | — | +368.60 (reason: 3% fee 11.40) | host | B / T |

Rows 3/4 are omitted when their amount is zero (Reject writes rows 1, 2, 4). Manual adjustment
uses `AGENT_OVERRIDE` for the guest row. Actor is the agent on all four; `reason` on row 1 is the agent's
resolution reason.

## 4. Action vocabulary (`domain.enums.AuditAction`)

Producers pass the enum; the column stores its name; the screen's combo lists all values.

- **Booking:** `BOOKING_REQUESTED` (null→PENDING), `BOOKING_CONFIRMED`, `BOOKING_REJECTED`,
  `BOOKING_CANCELLED_BY_GUEST`, `BOOKING_CANCELLED_BY_HOST`, `BOOKING_COMPLETED`.
- **Ticket:** `TICKET_OPENED`, `TICKET_ASSIGNED`, `TICKET_UNASSIGNED`, `TICKET_RESOLVED`, `TICKET_NOTE_SAVED`.
- **Money** (same names as `WalletTransactionType`): `TOP_UP`, `WITHDRAWAL`, `ESCROW_HOLD`, `ESCROW_REFUND`,
  `BOOKING_PAYOUT`, `TICKET_REMEDY`, `AGENT_OVERRIDE`.
- **Listing:** `LISTING_CREATED`, `LISTING_UPDATED`, `LISTING_STATUS_CHANGED`.
- **Category:** `TICKET_CATEGORY_CREATED`, `_RENAMED`, `_TOGGLED`, `_DELETED` (`CATEGORY_DELETED` renamed).
- **Reserved for W11 (defined now, emitted by W11; § 4a):** `ACCOUNT_SUSPENDED`, `ACCOUNT_REACTIVATED`, `LISTING_STATUS_CASCADE`, `BOOKING_FORCE_CANCELLED`.

The design board's `BOOKING_FORCE_CANCEL`/`_FORCE_COMPLETE` options are dropped (C22); its
`ACCOUNT_SUSPEND` becomes `ACCOUNT_SUSPENDED`.

### 4a. Account governance accommodation (operator, C32)

Account-governance actions (F10, W11) must be logged in this same table; W12 provides the schema and
vocabulary, **W11 implements the calls**. Nothing in W12 suspends or reactivates accounts. The shape W11
must use, so no further migration is needed:

| Action | entity | before → after | reason | subject |
|---|---|---|---|---|
| `ACCOUNT_SUSPENDED` | `User` = the account | `ACTIVE` → `SUSPENDED` | the agent's suspension reason | the suspended user |
| `ACCOUNT_REACTIVATED` | `User` | `SUSPENDED` → `ACTIVE` | agent's reason | the user |
| `LISTING_STATUS_CASCADE` (one row per listing) | `Property` | `ACTIVE` → `INACTIVE` | `Host suspended` | the host |
| `BOOKING_FORCE_CANCELLED` (one row per booking) | `Booking` | `CONFIRMED` → `FORCE_CANCELLED` | `Account suspended — cascading cancellation` | the affected guest/host, `bookingId` set |
| money rows for each cascading refund | `WalletTransaction` | — | — | wallet owner |

`AuditAction` therefore includes these values now (W11 activates them). The suspension reason lives in
`reason` on the `ACCOUNT_SUSPENDED` row, which is the source D2 point 1 asked for (no `users.suspensionReason` column
is added). The seeded suspension/cascade rows are written in this shape.

## 5. Service and repository changes

- `AuditService.record(AuditRecord)` replaces `record(actor, action, entityType, id, Object, Object)`.
  `AuditRecord` carries actor, `AuditAction`, entity type/id, before/after status, `walletAdjustment`,
  reason, subject, bookingId, ticketId. The service resolves `actorName`/`subjectName` from
  `UserRepository` at write time. It never opens its own transaction: it uses the caller's
  connection so the row commits or rolls back with the change.
- `AuditService.search(AuditFilter, limit, offset)` replaces `query(userId, bookingId, actionType)`.
  `AuditFilter(String text, AuditAction action, LocalDate from, LocalDate to)`.
- **Search semantics** (operator, C29): a full UUID, or a hex id fragment (contains-match) of ≥ 4 chars, matches
  `actorUserId`, `subjectUserId`, `bookingId`, `ticketId`, `entityId`. Any other text is a
  case-insensitive contains match on display name/email, **resolved to user ids first** (current
  `users` names ∪ names recorded in the log), and the log is then queried by
  `actorUserId IN (...) OR subjectUserId IN (...)`, so a user's whole history appears even after a
  rename. Date range is inclusive, in the app clock's zone. Order: `timestamp DESC, rowid ASC`.
  Page size 200 with a "Load more" button.
- **Instrumentation points** (all inside the existing `TransactionManager` lambdas):
  `BookingServiceImpl` submit/decide/cancel/complete; `TicketServiceImpl` create-path, assign,
  unassign, notes, categories; `DisputeSettlementServiceImpl` (rows 1–4 above, replacing the single
  `TICKET_RESOLVED` blob); every ledger write via `WalletLedgerWriter` and the settlement `credit`
  helper writes one money row. `ListingServiceImpl` calls are migrated to the new API.
- **Ticket creation:** `TicketService.fileTicket` is owned by W4 and stays unimplemented here. `TICKET_OPENED`
  (with both `ticketId` and `bookingId`) is defined now and carried by the mock seed, so W4 only has to call it.
- **System actor:** a seeded user `SnoozeShare System` (fixed id, reserved email
  `system@snoozeshare.invalid`, role `AGENT`, status `SUSPENDED` so it can never authenticate and is
  excluded from agent pickers). A proper `SYSTEM` role and its wallet belong to W14.

## 6. UI — Agent "Audit Log" tab

Follows W10 exactly (C24–C26: Fall Light palette, `agent-theme.css`, tab strip, `agent-card`,
`agent-table`). Board source: `AgentAuditLog.dc.html` in artifact `PWBCxbfv9e9FGVvY6RKUwd`.

New files: `ui/admin/audit/AuditLogController.java`, `resources/.../ui/admin/audit/audit-log.fxml`;
`AdminShellController.showAuditLog()` loads it (as `showCategories` does); CSS added to
`agent-theme.css`.

| Board (HTML) | FXML / style class |
|---|---|
| Page wrapper, `padding:24px 32px`, `gap:16px` | `VBox styleClass="agent-content" spacing="16"` + `Insets 24/32/24/32` |
| `<h1>Audit log</h1>` 20px/800 | `Label styleClass="page-title"` |
| Filter card (white, 1px border, radius 12, padding 14/16) | `HBox styleClass="agent-card"` (padding, `alignment=BOTTOM_LEFT`, spacing 12) |
| Field label 10px caps bold | `Label styleClass="agent-dialog-field-label"` (W10 modal label) |
| Text input radius 8 | `TextField` (`.agent-root .text-field`, existing) |
| `<select>` action type | `ComboBox styleClass="agent-combo"` |
| **New:** From / To date | `DatePicker`, new `.agent-root .date-picker` block styled like `.text-field` |
| "Apply filters" solid accent | `Button styleClass="button"` |
| "Clear" underlined link | `Hyperlink styleClass="agent-crumb-link"` |
| Hint text in the card | dropped (the snapshot caveat no longer applies) |
| Results grid (bordered card, inset header) | `TableView styleClass="agent-table"` inside `VBox agent-card`, same fixed-row-height binding as `DisputeQueueController` |
| Header cells 11px caps | table headers (existing `agent-table` column-header style), titles `TIMESTAMP · ACTOR · ACTION TYPE · STATUS · REASON · AMOUNT` |
| Action pill (per-type colours) | `TableCell` graphic `Label` with `agent-pill-{accent,success,warning,danger}` or neutral `agent-pill` |
| Amount `+$465.60` / `-$120.00` / `—` | plain cell, `agent-cell-strong`; SGD label per C10; `—` when no wallet adjustment |
| Rows not clickable | no row click handler (unlike disputes), no pointer cursor |

**Board deviations (deliberate, operator-decided):** User ID + Booking ID inputs become one search
box (C29); From/To date pickers added; force-action options removed (C22); currency `SGD` (C10);
rows are a `TableView` like W10, not CSS-grid divs. Pill colours: accent = agent/ticket actions,
success = money in / completed, warning = suspend/cancel-type, danger = rejected/deleted, neutral = other.

**Table cell rules:** timestamp `MMM d, h:mm a` local zone; Actor = `actorName`; Status = `After` label,
or `Before → After` when both exist, `—` if neither; Reason blank shows `—`. Empty result: "No audit
entries match these filters." Apply filters runs the search; Clear resets all four and reloads;
Enter in the search box also applies.

## 7. Data migration and mock DB

- `V002__audit_trail.sql`: `ALTER TABLE ADD COLUMN` ×7, indexes, System-user insert. Legacy rows keep
  null in the new columns and their old `afterState` text (they were never valid JSON in-app).
- `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db` are updated and the `.db` rebuilt
  (rule in § Orientation of the state file). The 15 seed audit rows are rewritten to the new shape,
  extended so every ticket 1–6 and the seeded bookings show their one-change rows, and the money rows
  match the seeded `wallet_transactions`. `SchemaParityTest` must stay green.

## 8. Recorded consequences

- **D2 point 2** ("status/reason/amount derived from before/after JSON via a projection layer") is
  superseded: they are real columns (C28).
- Until W14, wallet movements are **dual-written** (`wallet_transactions` and an audit row) in the same
  transaction; W14 removes the duplicate.
- Audit-write failure now aborts the surrounding transaction (intended: no unaudited state change).

## 9. Testing (TDD, per project method)

- Repository: save/search round-trip of every column; filter by uuid, prefix, name-resolved ids
  (including rename), action, inclusive date range; ordering; paging; append-only surface.
- Service: each instrumented action writes exactly the rows in § 3/§ 4 (resolution: 4/3 rows per mode;
  cancel refund tiers; host reject; top-up/withdraw), in one transaction (rollback test: failing
  audit write leaves booking/ticket/wallet unchanged).
- Legacy: `AuditServiceTest`, `TicketService*`, `DisputeSettlement*`, `ListingServiceTest` migrated.
- UI (`AdminUiSmokeTest` + snapshot pattern): screen loads over the mock DB copy, filters narrow rows,
  Clear resets, pills/amount formatting; `AdminFxmlLayoutTest` covers the new FXML; snapshot written to
  `build/ui-snapshots/`. Full `gradlew build` incl. checkstyle green.

## 10. Open questions — RESOLVED 2026-09-26 (operator approved all defaults)

1. **Reference column.** The board has no booking/ticket column, so an agent cannot tell *which*
   booking a row is about. Default: add a narrow `REF` column (`Booking #0009`, `Ticket #0004`: last four characters, consistent with the queue's `ticketLabel`). Alternative: keep the board's six columns.
2. **System user role.** Default: `AGENT` + `SUSPENDED` (§ 5). Alternative: add a real `SYSTEM` role now
   (SQLite CHECK rewrite = table rebuild of `users`, more blast radius).
3. **Status cell.** Default `Before → After`. Alternative: after-state only as on the board.
4. **Instrumenting W7 host blocks and W6 listing edits.** Default: listings migrated to the new API,
   W7 blocks left un-audited (not in F11.1.1/2). Say if you want blocks audited too.
