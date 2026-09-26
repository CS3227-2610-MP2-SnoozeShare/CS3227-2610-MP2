# W11 — Agent Account Governance (F10) Design Spec

**Workstream:** W11 · **Branch:** `agent-account-governance` · **Date:** 2026-09-26
**Backlog:** F10.1.1 (suspend), F10.1.2 (auto-cancel pending bookings/listings). Reactivation is added by operator decision (§ 2, C34).
**Visual source of truth:** [design canvas](https://claude.ai/artifact/PWBCxbfv9e9FGVvY6RKUwd) — artboards `AgentAccounts`, `ConfirmSuspendAccount`, and the green banner of `ConfirmForceCompleteBooking` (for reactivation). Where § 2 differs from an artboard, § 2 wins.

## 1. Goal

Give the Support Agent a working **Accounts** tab. An agent can see every account, search by display name,
suspend a Guest or Host with a required reason, and reactivate a suspended account. Suspension revokes
app use and automatically unwinds the user's pending bookings and active listings.

## 2. Decisions this design rests on

| ID | Decision |
|---|---|
| C34 | Operator, 2026-09-26: (a) **Reactivate** is built although F10.1.1 only says suspend (new capability, like C26's category delete). (b) F10.1.2 cascade is **in W11**, not a later slice. (c) **Agent accounts are listed but have no action** (dash); only Guest and Host can be suspended, and an agent cannot suspend themself. (d) Suspension reason is stored in a nullable **`users.suspensionReason`** column (resolves D2 item 1); a `suspensions` table and audit-log derivation were rejected (overkill / slow, and coupled to W12). |
| C35 | Operator, 2026-09-26, UI deltas from the canvas: the first column is **Display Name** (not "Username"); a new **Email** column follows it; **Joined** and every date in the two modals use **`DD MMM YYYY`** (e.g. `05 Mar 2026`); the Suspend modal shows the display name in the large banner font with the **full email** on a row below it; the Reactivate modal uses the success green **`#40680C`** from the Force Complete mock-up. |
| C8 | Cancelled/rejected pending bookings refund 100% of escrow. Reused for the cascade (as `FORCE_CANCELLED`). |
| C17 | Escrow on `CONFIRMED` stays is settled by agents via W10 tickets. The cascade never touches `CONFIRMED` bookings. |
| C26 | Modals use `AgentModal` (50% grey scrim), not the canvas blur. |
| C24 | Agent screens use `agent-theme.css` (Fall Light); guest/host shells untouched. |

## 3. Scope

### 3.1 In scope

1. **Accounts screen** — table of all users (Guest, Host, Agent), sorted by `createdAt` ascending. Columns: **Display Name · Email · Role · Joined · Status · Action**. Search box (right of the title) filters case-insensitively on display name **and** email. Status pill: Active (green) / Suspended (red); a suspended row shows `Reason: <text>` under the pill. Action: red-outline **Suspend** (active Guest/Host), grey-outline **Reactivate** (suspended Guest/Host), `—` for Agents.
2. **Suspend modal** — banner (danger red) with display name large and email below, then `Role · joined DD MMM YYYY`; info note; required reason textarea; Cancel / **Confirm suspend**.
3. **Reactivate modal** — same card, banner in `#40680C` with the same three lines; info note that the user can log in and use the app again; required reason textarea (audit); Cancel / **Confirm reactivate**.
4. **Suspend cascade (F10.1.2)**, atomic with the status change:
   - Guest: every `PENDING` booking → `FORCE_CANCELLED` with 100% escrow refund.
   - Host: every `PENDING` booking request on their properties → `FORCE_CANCELLED` with 100% refund; every `ACTIVE` property → `INACTIVE`.
   - `FORCE_CANCELLED` (agent-initiated, already legal from `PENDING` for `Role.AGENT`) is used for both, matching the mock data's suspension-cascade booking, so neither party's history says they cancelled or rejected.
   - `CONFIRMED` bookings and their escrow are untouched (W10 handles disputes).
5. **Enforcement** — login already refuses non-`ACTIVE` users and `ListingServiceImpl` refuses suspended hosts. Add a guard so `BookingService.submitRequest` refuses a suspended guest. No live sign-out: the app is single-process and the acting user is always the agent.
6. **Reactivate** — status → `ACTIVE`, reason cleared. Listings set `INACTIVE` by the cascade **stay inactive** (the host re-activates them); cancelled bookings stay cancelled.
7. **Audit** — `USER_SUSPENDED` and `USER_REACTIVATED` (before/after user snapshot including reason), plus the existing per-booking/listing audit rows for cascaded changes. Actor is the agent.

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

- Migration `V002__suspension_reason.sql`: `ALTER TABLE users ADD COLUMN suspensionReason TEXT`. Nullable, no default.
- `db/schema.sql` gains the column; `db/seed-mock-data.sql` gives the already-suspended seed user a reason matching the canvas (`repeated late cancellations flagged across 3 bookings`); `db/snoozeshare-mock.db` is rebuilt from the two SQL files. The existing schema-parity test guards migration-vs-`schema.sql` drift.
- `User` record gains `String suspensionReason` (null unless suspended). All constructor call sites updated.

### 4.3 Service

`AccountGovernanceService` (new interface, `AccountGovernanceServiceImpl`):

```java
List<AccountSummary> listAccounts(String search);          // search null/blank = all
User suspend(UUID userId, UUID agentId, String reason);
User reactivate(UUID userId, UUID agentId, String reason);
```

- `AccountSummary(User user)`-style read model with display name, email, role, `createdAt`, status, reason.
- Validation (all `IllegalArgumentException`/`IllegalStateException` with messages the dialog shows inline): actor must be an `AGENT` and `ACTIVE`; target must exist; target role must be `GUEST` or `HOST`; target must not be the actor; reason required (trimmed, non-blank); suspend requires `ACTIVE`, reactivate requires `SUSPENDED`.
- `suspend` runs in one DB transaction: save user → cascade (§ 3.1.4) → audit. Cascaded refunds go through the existing booking/escrow code paths (`TransactionService.refundEscrow` via booking cancel/decide logic) so ledger invariants hold; if any step fails the whole suspension rolls back. After commit, publish `AccountStatusChangedEvent(userId, newStatus)` for UI refresh.
- The existing stub `UserService.suspend(userId, agentId)` is removed (its behaviour is subsumed; D19).
- The cascade moves bookings `PENDING → FORCE_CANCELLED` through `BookingStateMachine` with `Role.AGENT` (already allowed; no state-machine change) and refunds escrow via `TransactionService.refundEscrow`, inside the governance transaction.

### 4.4 UI

- `AdminShellController.showAccounts()` loads `accounts/account-governance.fxml` into the shell center, mirroring `showCategories()` (dispose event subscription on tab change).
- `account-governance.fxml`: page title "Account governance", search `TextField` right-aligned, `agent-card` with `agent-grid-header` / `agent-grid-row` styled rows via a `GridPane` per row (same pattern as `CategoryAdminController`), column shares roughly 1.1 : 1.6 : 0.8 : 1 : 1.3 : 0.8.
- `suspension-dialog.fxml` + `SuspensionDialogController`: one card for both actions, parameterised by mode; banner style class `agent-banner-danger` or `agent-banner-success` added to `agent-theme.css` (`#980c1c` / `#40680c`, text `#fdf8f0`). Built on `AgentModal.create(card, title)`. The confirm button is disabled until a reason is entered; service errors show in an inline `error-message` label and keep the card open (as `CategoryDialogController`).
- Date format: one shared formatter `DD MMM YYYY` (`DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)`) in the accounts package, used by the table and both dialogs. Joined date uses `createdAt` converted with `ZoneId.systemDefault()`.
- Kept from the canvas: status pill colours, red-outline Suspend / grey-outline Reactivate buttons, reason text under the pill, search placeholder (reworded "Search name or email…"), modal copy structure. Intentionally different: `AgentModal` scrim instead of blur (C26); note text reworded to what the app does ("cannot log in or book; pending requests are cancelled"); the canvas footnote about a "grounding note" is dropped.

### 4.5 Error handling

Dialog shows service messages inline and stays open. A failed cascade rolls everything back: the user stays `ACTIVE`, and the dialog shows the failure. Table refresh runs on the status event and after each dialog closes.

## 5. Testing (TDD, service first)

- `AccountGovernanceServiceTest`: list/search (name and email, case-insensitive); suspend guest cancels pending bookings with refund and leaves confirmed ones; suspend host force-cancels pending requests with refund and deactivates active listings; validation (non-agent actor, agent target, self, blank reason, already suspended/active); atomic rollback when a cascade step throws; reactivate clears reason and leaves listings inactive; audit rows written; event published after commit.
- `UserRepository` test: reason persists and clears; `findAll` ordering.
- Migration/parity test for `V002`; mock DB still opens.
- `BookingService.submitRequest` refuses a suspended guest.
- UI: `AdminFxmlLayoutTest` loads both FXMLs; smoke test on the FX toolkit (search filters rows, Suspend flow, Reactivate flow, agent row shows no button); snapshot tests write `build/ui-snapshots/accounts*.png`; date-format unit test (`05 Mar 2026`).
- `gradlew build` (with checkstyle) stays green.

## 6. Deviations and open items

- **D19** — `UserService.suspend` removed in favour of `AccountGovernanceService.suspend` (adds reason, cascade, audit; the old stub set the status only and ignored the agent).
- **D2 item 1** resolved by C34(d). D2 item 2 (audit projection) stays with W12.
