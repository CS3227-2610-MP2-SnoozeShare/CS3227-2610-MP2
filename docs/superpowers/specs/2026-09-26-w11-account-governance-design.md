# W11 — Agent Account Governance (F10) Design Spec

**Workstream:** W11 · **Branch:** `agent-account-governance` · **Date:** 2026-09-26
**Related:** merges W12 (audit trail, `agent-platform-audit-trail`) — its audit vocabulary, table patterns and multi-select menu are reused (§ 2, § 4.4).
**Backlog:** F10.1.1 (suspend), F10.1.2 (auto-cancel pending bookings/listings). Reactivation is added by operator decision (§ 2, C34).
**Visual source of truth:** [design canvas](https://claude.ai/artifact/PWBCxbfv9e9FGVvY6RKUwd) — artboards `AgentAccounts`, `ConfirmSuspendAccount`, and the green banner of `ConfirmForceCompleteBooking` (for reactivation). Where § 2 differs from an artboard, § 2 wins.

## 1. Goal

Give the Support Agent a working **Accounts** tab. An agent can see every account, search their accounts, 
suspend a Guest or Host with a required reason, and reactivate a suspended account. Suspension revokes
app use and automatically unwinds the user's pending bookings and active listings.

## 2. Decisions this design rests on

| ID | Decision |
|---|---|
| C34 | Operator, 2026-09-26: (a) **Reactivate** is built although F10.1.1 only says suspend (new capability, like C26's category delete). (b) F10.1.2 cascade is **in W11**, not a later slice. (c) **Agent accounts are listed but have no action** (dash); only Guest and Host can be suspended, and an agent cannot suspend themself. (d) Suspension reason is stored in a nullable **`users.suspensionReason`** column, added by migration **`V003`** (V002 is W12's audit trail). This **reverses the no-column part of C32** (which kept the reason only on the audit row): operator, 2026-09-26, "logs are not meant to be data storage". The reason is *also* written to the audit row as C32/W12 § 4a require. A `suspensions` table was rejected (overkill). |
| C35 | Operator, 2026-09-26, UI deltas from the canvas: the first column is **Display Name** (not "Username"); a new **Email** column follows it; **Joined** and every date in the two modals use **`DD MMM YYYY`** (e.g. `05 Mar 2026`); the Suspend modal shows the display name in the large banner font with the **full email** on a row below it; the Reactivate modal uses the success green **`#40680C`** from the Force Complete mock-up. |
| C36 | Operator, 2026-09-26: the suspension cascade force-cancels **`PENDING` bookings and `CONFIRMED` bookings that have not started** (check-in date after today), 100% escrow refund each. In-progress and ended `CONFIRMED` stays are untouched (their escrow and disputes belong to W10, C17). This widens F10.1.2 and follows W12 § 4a / seed booking 12. |
| C32 / W12 § 4a | Audit shapes for account governance are fixed by W12 (§ 3.1.7). |
| C33 | W12 UI precedents apply to every agent table: fixed header with only the rows scrolling, grey row hover without a hand cursor when rows are not clickable. |
| C8 | Cancelled/rejected pending bookings refund 100% of escrow. Reused for the cascade (as `FORCE_CANCELLED`). |
| C17 | Escrow on `CONFIRMED` stays is settled by agents via W10 tickets. The cascade never touches `CONFIRMED` bookings. |
| C26 | Modals use `AgentModal` (50% grey scrim), not the canvas blur. |
| C24 | Agent screens use `agent-theme.css` (Fall Light); guest/host shells untouched. |

## 3. Scope

### 3.1 In scope

1. **Accounts screen** — table of all users (Guest, Host, Agent), sorted by `createdAt` ascending. Columns: **Display Name · Email · Role · Joined · Status · Action**. Search box (right of the title) filters live as the user types (no Apply button), case-insensitively, as a *contains* match against any of the **displayed** cell texts: display name, email, role label (`Guest`, `Host`, `Support Agent`), joined (`05 Mar 2026`, so `mar` or `2026` match), and status (`Active`/`Suspended`). The suspension reason is not searched. Matching lives in a pure, unit-tested `AccountSearch` helper in the accounts package. Status pill: Active (green) / Suspended (red); a suspended row shows `Reason: <text>` under the pill. Action: red-outline **Suspend** (active Guest/Host), grey-outline **Reactivate** (suspended Guest/Host), `—` for Agents.
2. **Suspend modal** — banner (danger red) with display name large and email below, then `Role · joined DD MMM YYYY`; info note; required reason textarea; Cancel / **Confirm suspend**.
3. **Reactivate modal** — same card, banner in `#40680C` with the same three lines; info note that the user can log in and use the app again; required reason textarea (audit); Cancel / **Confirm reactivate**.
4. **Suspend cascade (F10.1.2)**, atomic with the status change:
   - Guest: every `PENDING` booking, and every `CONFIRMED` booking whose check-in date is after today, → `FORCE_CANCELLED` with 100% escrow refund (C36).
   - Host: the same two sets, on their properties; plus every `ACTIVE` property → `INACTIVE`.
   - `FORCE_CANCELLED` is legal for `Role.AGENT` from both `PENDING` and `CONFIRMED` in `BookingStateMachine`. W10 removed every agent entry point to it, so **this cascade is its only producer**. It matches the mock data's booking 12, and neither party's history says they cancelled or rejected.
   - `CONFIRMED` stays already started or ended, and their escrow and tickets, are untouched (W10 settles them).
5. **Enforcement** — login already refuses non-`ACTIVE` users and `ListingServiceImpl` refuses suspended hosts. Add a guard so `BookingService.submitRequest` refuses a suspended guest. No live sign-out: the app is single-process and the acting user is always the agent.
6. **Reactivate** — status → `ACTIVE`, reason cleared. Listings set `INACTIVE` by the cascade **stay inactive** (the host re-activates them); cancelled bookings stay cancelled.
7. **Audit** — the shapes are fixed by W12 § 4a and written through `AuditService.record(AuditRecord)` on the transaction's connection: `ACCOUNT_SUSPENDED` / `ACCOUNT_REACTIVATED` (entity `User`, `ACTIVE ↔ SUSPENDED`, `reason` = the agent's reason, subject = the user); one `LISTING_STATUS_CASCADE` row per listing (`ACTIVE → INACTIVE`, reason `Host suspended`); one `BOOKING_FORCE_CANCELLED` row per booking (`PENDING`/`CONFIRMED → FORCE_CANCELLED`, reason `Account suspended — cascading cancellation`, `bookingId` set, subject = the affected guest or host); and the `ESCROW_REFUND` money row for each refund via `recordWalletTransaction`. Actor is the agent. The Audit Log screen already renders all of these.

### 3.2 Non-goals

| Not built | Owner / reason |
|---|---|
| Suspending Agents; self-suspend | C34(c) |
| Cancelling `CONFIRMED`/in-progress stays on suspend | Escrow/disputes belong to W10 (C17) |
| Auto-restoring listings or bookings on reactivate | Deliberate; host re-lists |
| Audit Log screen and audit projection layer | W12 |
| Live session termination | Single-process app; no other live session to end |
| Suspension history table | Audit log keeps history (C34(d)) |

## 4. Design

### 4.1 Layering

```text
ui.admin.accounts  (AccountGovernanceController, SuspensionDialogController + FXML)
        │ service interfaces only
service: AccountGovernanceService, AuditService, EventBus
        │
domain: User (+suspensionReason), AccountStatus, BookingStateMachine (existing)
        │
repository: UserRepository (+findAll, save persists reason), BookingRepository, PropertyRepository
```

### 4.2 Data

- Migration `V003__suspension_reason.sql` (V002 is W12's `V002__audit_trail.sql`): `ALTER TABLE users ADD COLUMN suspensionReason TEXT`. Nullable, no default.
- `db/schema.sql` gains the column; `db/seed-mock-data.sql` gives the already-suspended seed users (`c…06` Ben Alvarez, `b…06` Chen Wu) a reason equal to the `reason` on their seeded `ACCOUNT_SUSPENDED` audit row; `db/snoozeshare-mock.db` is rebuilt from the two SQL files (it ships migrated with `schema_history`, D15) and `CommittedMockDbTest` is updated for v3. The existing schema-parity test guards migration-vs-`schema.sql` drift.
- `User` record gains `String suspensionReason` (null unless suspended). All constructor call sites updated.

### 4.3 Service

`AccountGovernanceService` (new interface, `AccountGovernanceServiceImpl`):

```java
List<AccountSummary> listAccounts();                         // all users, createdAt ascending
User suspend(UUID userId, UUID agentId, String reason);
User reactivate(UUID userId, UUID agentId, String reason);
```

- `AccountSummary` read model: user id, display name, email, role, `createdAt`, status, reason. Searching is a UI concern (below), so the service does not take a search string.
- Validation (all `IllegalArgumentException`/`IllegalStateException` with messages the dialog shows inline): actor must be an `AGENT` and `ACTIVE`; target must exist; target role must be `GUEST` or `HOST`; target must not be the actor; reason required (trimmed, non-blank); suspend requires `ACTIVE`, reactivate requires `SUSPENDED`.
- `suspend` runs in one DB transaction: save user → cascade (§ 3.1.4) → audit. Cascaded refunds go through the existing booking/escrow code paths (`TransactionService.refundEscrow` via booking cancel/decide logic) so ledger invariants hold; if any step fails the whole suspension rolls back. After commit, publish `AccountStatusChangedEvent(userId, newStatus)` for UI refresh.
- The existing stub `UserService.suspend(userId, agentId)` is removed (its behaviour is subsumed; D19).
- The cascade moves bookings `PENDING → FORCE_CANCELLED` through `BookingStateMachine` with `Role.AGENT` (already allowed; no state-machine change) and refunds escrow via `TransactionService.refundEscrow`, inside the governance transaction.

### 4.4 UI

- `AdminShellController.showAccounts()` loads `accounts/account-governance.fxml` into the shell center, mirroring `showCategories()` (dispose event subscription on tab change).
- `account-governance.fxml`: page title "Account governance", search `TextField` right-aligned (with the placeholder as "Search..." and the input left-aligned), `agent-card` with `agent-grid-header` / `agent-grid-row` styled rows via a `GridPane` per row (same pattern as `CategoryAdminController`), with the **fixed-header table pattern** from W12 (C33): the header grid sits above a `ScrollPane` (`agent-rows-scroll`, no horizontal bar) so only the rows scroll and the scroll bar starts below the header. Rows are not clickable, so they get the shared grey hover with no hand cursor; the Suspend/Reactivate buttons keep the hand. Column shares roughly 1.1 : 1.6 : 0.8 : 1 : 1.3 : 0.8.
- `suspension-dialog.fxml` + `SuspensionDialogController`: one card for both actions, parameterised by mode; banner style class `agent-banner-danger` or `agent-banner-success` added to `agent-theme.css` (`#980c1c` / `#40680c`, text `#fdf8f0`). Built on `AgentModal.create(card, title)`. The confirm button is disabled until a reason is entered; service errors show in an inline `error-message` label and keep the card open (as `CategoryDialogController`).
- Date format: one shared formatter `DD MMM YYYY` (`DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)`) in the accounts package, used by the table and both dialogs. Joined date uses `createdAt` converted with `ZoneId.systemDefault()`.
- Kept from the canvas: status pill colours, red-outline Suspend / grey-outline Reactivate buttons, reason text under the pill, search box (placeholder `Search...`, input text left-aligned; the canvas says "Search username…"), modal copy structure. Intentionally different: `AgentModal` scrim instead of blur (C26); note text reworded to what the app does ("cannot log in or book; pending requests are cancelled"); the canvas footnote about a "grounding note" is dropped.

### 4.5 Error handling

Dialog shows service messages inline and stays open. A failed cascade rolls everything back: the user stays `ACTIVE`, and the dialog shows the failure. Table refresh runs on the status event and after each dialog closes.

## 5. Testing (TDD, service first)

- `AccountGovernanceServiceTest`: list (createdAt order, includes agents); suspend guest force-cancels pending and not-yet-started confirmed bookings with refund, leaving started/ended confirmed ones; suspend host does the same for their properties' bookings and deactivates active listings; validation (non-agent actor, agent target, self, blank reason, already suspended/active); atomic rollback when a cascade step throws; reactivate clears reason and leaves listings inactive; audit rows written; event published after commit.
- `UserRepository` test: reason persists and clears; `findAll` ordering.
- Migration/parity test for `V002`; mock DB still opens.
- `BookingService.submitRequest` refuses a suspended guest.
- UI: `AdminFxmlLayoutTest` loads both FXMLs; smoke test on the FX toolkit (search filters rows, Suspend flow, Reactivate flow, agent row shows no button); snapshot tests write `build/ui-snapshots/accounts*.png`; `AccountSearch` unit test (each field, case-insensitive, blank shows all); date-format unit test (`05 Mar 2026`); a table-layout test mirroring `AdminTableLayoutTest` (fixed header, only rows scroll, hover without hand cursor).
- `gradlew build` (with checkstyle) stays green.

## 6. Deviations and open items

- **D19** — `UserService.suspend` removed in favour of `AccountGovernanceService.suspend` (adds reason, cascade, audit; the old stub set the status only and ignored the agent).
- **D2 item 1** resolved by C34(d). D2 item 2 (audit projection) stays with W12.
