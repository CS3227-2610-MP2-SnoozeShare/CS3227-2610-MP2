# Project State — SnoozeShare

**Living document — the source of truth for this project.** Read it in full before doing
anything, then run `git log --oneline -20` to confirm it still matches reality. Update it at
every boundary, not at the end of the session.

- **Phase:** W14 Unified Ledger — Done, operator-confirmed 2026-09-29 (PR #16 merged to `main`), guide checkpoint awaiting operator decision; W13 Messaging (service + host Messages UI) done and merged to `main`; W9 Host Wallet Management done and merged; W12 Platform Audit Trail done (merged); W11 Account Governance implemented, awaiting operator acceptance
- **Stack:** Java 25, JavaFX 25 (javafx.controls, javafx.fxml), Gradle (application + shadow + checkstyle plugins), SQLite (embedded, file-based, `org.xerial:sqlite-jdbc`) via plain JDBC, JUnit 5 + TestFX for tests
- **Branch:** `host-ui-touchup` (branched from `w9`; Host UI touch-up commits)
- **Method:** Native inline execution with TDD-first vertical slices, fresh-context whole-branch review at end
- **Last updated:** 2026-09-29 by Claude Sonnet 5 — W14 confirmed `Done`; PR #16 (merged) and the follow-up audit-log Target-column PR #19 reflected; S16 session row retired; C45/C46 recorded; the W14 Developer Guide checkpoint written (§ 4.15 added, wallet/ledger wording corrected throughout), Guide set to `Pending`
- **Last verified against repo:** 2026-09-29
- **Developer guide:** `docs/DeveloperGuide.md` seeded and extended with W10 and W12 on 2026-09-26, W6/W7 and others on `main`, then W11 on 2026-09-27 (operator-approved checkpoints; W11 added § 4.13/4.14 Account governance, a sequence diagram and the Accounts screen in § 4.8; W12 added § 4.9/4.13 Audit trail, two diagrams, the Audit Log screen in § 4.8), then W14 on 2026-09-29 (new § 4.15 Unified ledger / `LedgerWriter`, plus wallet/ledger wording corrected in §§ 3.1, 4.5, 4.7, 4.8, 4.12–4.14, 5, 6, 7; C46). W1/W2/W5/W6/W7 are `Awaiting confirmation` and not yet documented; W9/W13 not yet in the guide.

Sections are ordered by how often they are needed: **1–3 say where we are, 4–5 say what the
system is, 6–7 say what not to touch and what is stuck, 8–10 are the record.** Cite sections by
name (`§ Known Gaps`), not by number, so they can be reordered without breaking references.

---

## 1. Orientation

SnoozeShare is a Java 25 / JavaFX desktop application for short-term property rentals, built as
CS3227's Mini Project 2. It has three roles — **Guest**, **Host**, **Support Agent** — each with
its own role-isolated UI, sharing one domain model, one wallet/transaction ledger, mocked
authentication, and shared booking/ticket state machines. It runs as a single process against an
embedded database; there is no server or distributed deployment. See
[`docs/SnoozeShare-Architecture-Proposal.md`](docs/SnoozeShare-Architecture-Proposal.md) for the
full design rationale and
[`docs/ProductBacklog.md`](docs/ProductBacklog.md) for the feature backlog (Epics F0–F11 across
three iterations).

**Repo map** — where documents live. Code layout is in § Architecture.

| Path | What lives there |
|---|---|
| `docs/superpowers/specs/` | Design specs — one per feature-level workstream, including W1 support tooling |
| `docs/superpowers/plans/` | Implementation plans, including the W1 support-tooling plan |
| `docs/project-state/done-ledger.md` | The Done ledger — every change, newest first |
| `docs/SnoozeShare-Architecture-Proposal.md` | The pre-existing architecture proposal — module boundaries, package layout, interface contracts, DB schema. Treated as the seed for § Architecture below, not a per-feature spec. |
| `docs/ProductBacklog.md` | The formal product backlog / engineering spec — epics F0–F11, prioritized and sprint-mapped |
| `docs/DeveloperGuide.md` | Handover brief for developers — covers W10 and W12 (see the Guide column in § Workstreams) |
| `docs/UserGuide.md`, `docs/Reflections.md` | End-user guide; reflections on the agentic workflow (not read in full for this bootstrap — out of scope per bootstrapping bounds) |
| `logs/` | Per-agent-session interaction logs named `YYYY-MM-DD_HH-mm-ss_<branch>.md` in SGT |
| `src/main/java/com/snoozeshare/` | Application source — package skeleton only, see § Architecture |
| `config/checkstyle/` | Checkstyle rules enforced on build |
| `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db` | Shared team-reference SQLite DB — draft SQL + the built `.db` file, committed so everyone queries the same data. **Rebuild the `.db` from the two SQL files after any seed edit** (`sqlite3 x.db < schema.sql; sqlite3 x.db < seed-mock-data.sql`); corrected 2026-09-25 to follow C17/C20 (see D6). **Seed timestamps are UTC ISO-8601 ending in `Z`** (as the app writes them via `Instant.toString()`; `JdbcCodecs.instant` rejects zone-less values) — keep that format in any seed edit. **Seed IDs must be valid hex UUIDs** (mock prefixes: users `a`/`b`/`c`, tickets `d`, listings `1`, bookings `2`, wallets `3`, transactions `4`, availability `5`, audit `6`, categories `7`, reviews `8`); non-hex prefixes make `UUID.fromString` throw. **The committed `.db` ships already migrated** (schema_history v1 through v8 as of W14 — System user with role `SYSTEM` and a real wallet, `wallet_transactions` gone, its 37 money rows now literal `audit_log` rows; `db/schema.sql` includes `CREATE TABLE IF NOT EXISTS schema_history`) and `CommittedMockDbTest` guards that. Dev/reference artifact, not wired into app startup (see § 4.5) |

---

## 2. How to Resume

```bash
.\gradlew build
.\gradlew test
.\gradlew run
```

**Sessions in flight**

| Session | Started | Agent(s) | Branch | Workstream | Status | Doing | Last touched |
|---|---|---|---|---|---|---|---|
| S1 | 2026-09-22 (time not tracked) | Claude Sonnet 5 | main | — | Paused | Bootstrapped this file; switched DB to SQLite; built and populated the shared mock DB `db/snoozeshare-mock.db` with operator-approved schema/data; no feature (F0–F11) work started yet | 2026-09-22 |
| S2 | 2026-09-23 | Claude Sonnet 5 | ui-mockup | — | Active | Design canvas reached 31 artboards (full `docs/ProductBacklog.md` coverage) and the deferred design spec is now written: `docs/superpowers/specs/2026-09-23-ui-design-system-design.md`. Not yet committed to git (git safety default — only PROJECT_STATE.md/.gitignore edits from this session are staged-but-uncommitted too). Next: operator reviews the written spec (brainstorming skill's user-review gate), then either request changes or move to `writing-plans` for the implementation plan | 2026-09-23 |
| S3 | 2026-09-24 | Claude Opus 4.6 | w2 | W2 | Paused | W2 complete: spec, plan, 7 tasks implemented via native inline TDD, whole-branch review done, 2 Important findings fixed (unknown amenity crash, O(n) host lookup). All 20 tests pass. Ready for merge to main | 2026-09-24 |
| S6 | 2026-09-25 | Claude Opus 4.6 | w5 | W5 | Paused | W5 complete: spec, plan, all 6 tasks implemented. All tests pass. Ready for merge to main | 2026-09-25 |
| S7 | 2026-09-27 | Claude Sonnet 5 | messaging-service | W13 | Paused | Service slice built and verified; host Messages UI continued on `w9` | 2026-09-27 |
| S8 | 2026-09-27 | Codex | host-ui-touchup | W6/W7/W13 + GitHub Pages docs | Paused | Session interaction log recorded for this chat; docs links target repository files on GitHub and docs-source Jekyll build passes | 2026-09-29 |
| S14 | 2026-09-26 | Codex | w8 | W8 — Host Request Queue, Earnings & Disputes | Paused | PR #11 open against main; W8 implementation, review fixes, and Developer Guide handoff complete | 2026-09-27 |

Status vocabulary, used verbatim: `Active` · `Paused` · `Blocked — needs human` (name the
question ID, same as a workstream row).

---

## 3. Workstreams

W1 has completed its shared-foundation implementation and is documented in the Developer Guide. The rows below are the backlog's epics (§2 of the architecture proposal is
their shared design; `docs/ProductBacklog.md` is their shared spec source) reframed as
workstreams so future sessions have somewhere to record status. Each started workstream must link
its spec and plan before feature implementation, per AGENTS.md § 3.

| ID | Workstream | Status | Spec | Plan | Progress | Guide |
|---|---|---|---|---|---|---|
| W1 | F0 — Auth, Registration & Wallet Provisioning | Done | [shared-foundation design](docs/superpowers/specs/2026-09-23-w1-shared-foundation-design.md) | [shared-foundation plan](docs/superpowers/plans/2026-09-23-w1-shared-foundation.md) | W1 implementation and verification complete; Developer Guide updated with shared-foundation coverage | Documented 2026-09-27 |
| W2 | F1 — Listing Search & Property Discovery | Done | [listing-search design](docs/superpowers/specs/2026-09-24-w2-listing-search-design.md) | [listing-search plan](docs/superpowers/plans/2026-09-24-w2-listing-search.md) | Implementation complete: search/filter, detail modal, price breakdown | Awaiting confirmation |
| W3 | F2 — Booking Execution & Trip Hub (incl. escrow) | Building | [booking-execution design](docs/superpowers/specs/2026-09-24-w3-booking-execution-design.md) | [booking-execution plan](docs/superpowers/plans/2026-09-24-w3-booking-execution.md) | All 9 tasks complete: TransactionServiceImpl, BookingServiceImpl (submit/cancel/decide), AppContext wiring, Trip Hub UI, Book Now button | — |
| W4 | F3 — Guest Feedback, Disputes & Reviews | Done | [guest-feedback design](docs/superpowers/specs/2026-09-26-w4-guest-feedback-disputes-reviews-design.md) | [guest-feedback plan](docs/superpowers/plans/2026-09-26-w4-guest-feedback-disputes-reviews.md) | All 8 tasks complete: fileTicket, ReviewService, AppContext wiring, ticket filing modal, review modal, Support tab, Trip Hub buttons | Awaiting confirmation |
| W5 | F4 — Guest Wallet Management (top-up/withdraw) | Done | [wallet-management design](docs/superpowers/specs/2026-09-25-w5-wallet-management-design.md) | [wallet-management plan](docs/superpowers/plans/2026-09-25-w5-wallet-management.md) | All 6 tasks complete: dashboard, modal, navigation, CSS, sidebar refresh | Awaiting confirmation |
| W6 | F5 — Host Listing Management & Publishing | In review | [listing-management design](docs/superpowers/specs/2026-09-25-w6-listing-management-design.md); | [listing-management plan](docs/superpowers/plans/2026-09-25-w6-listing-management.md); | Amenity tile container interaction corrected; all Host CSS remains in host-theme.css; suite retains AgentModalTest failure | Awaiting confirmation |
| W7 | F6 — Host Calendar & Date Overrides | Done | [host-calendar design](docs/superpowers/specs/2026-09-26-w7-host-calendar-design.md) | [host-calendar plan](docs/superpowers/plans/2026-09-26-w7-host-calendar.md) | Complete; booking calendar uses compact label fields and larger surrounding form gaps | Documented 2026-09-27 |
| W8 | F7 — Host Request Queue, Earnings & Disputes | Done | [host requests/earnings design](docs/superpowers/specs/2026-09-26-w8-host-requests-earnings-design.md) | [host requests/earnings plan](docs/superpowers/plans/2026-09-26-w8-host-requests-earnings.md) | PR #11 open against main; review fixes complete; broader F7.2.2 deferred to W13 | Documented 2026-09-27 |
| W9 | F8 — Host Wallet Management | Done | [host wallet management design](docs/superpowers/specs/2026-09-27-w9-host-wallet-management-design.md) | [host wallet management plan](docs/superpowers/plans/2026-09-27-w9-host-wallet-management.md) | Complete: wallet-owned statement styling, dynamic escrow display, directional badges, and native Host Booking-style modals | Documented 2026-09-27 |
| W10 | F9 — Agent Dispute Resolution (F9.2.1 force actions dropped, C22) | Done | [W10 design](docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md) | [W10 plan](docs/superpowers/plans/2026-09-25-w10-agent-dispute-resolution.md) | All 22 tasks done, operator confirmed 2026-09-26; W3 merge handoffs open | Documented 2026-09-26 |
| W11 | F10 — Agent Account Governance (F10.1.1 suspend, F10.1.2 cascade; Reactivate added, C34) | Done | [W11 design](docs/superpowers/specs/2026-09-26-w11-account-governance-design.md) | [W11 plan](docs/superpowers/plans/2026-09-26-w11-account-governance.md) | 14/14 tasks; operator accepted real-app run 2026-09-27 | Documented 2026-09-27 |
| W12 | F11 — Platform Audit Trail (Analytics half of the epic has no items, out of scope; W11 emits the account-governance rows, C32) | Done | [W12 design](docs/superpowers/specs/2026-09-26-w12-platform-audit-trail-design.md) | [W12 plan](docs/superpowers/plans/2026-09-26-w12-platform-audit-trail.md) | Done | Documented 2026-09-26 |
| W13 | F12 — Messaging (persistent ticket chat; `MessageService` replaces the in-memory seam; Host UI slice, C37/C38) | Done | [W13 service design](docs/superpowers/specs/2026-09-27-w13-messaging-service-design.md); [Host Messages design](docs/superpowers/specs/2026-09-27-host-messages-ui-design.md) | [W13 service plan](docs/superpowers/plans/2026-09-27-w13-messaging-service.md); [Host Messages plan](docs/superpowers/plans/2026-09-27-host-messages-ui.md) | Host ticket creation removed per reversed C39/C40 (the messaging-branch ones, distinct from W14's C39/C40); confirmed 2026-09-29, guide update proposed | Pending |
| W14 | Unified ledger — fold `wallet_transactions` into `audit_log`, System account (role SYSTEM) with a real wallet; `wallets` table kept (C30, C31, C39, C40) | Done | [W14 design](docs/superpowers/specs/2026-09-27-w14-unified-ledger-design.md) | [W14 plan](docs/superpowers/plans/2026-09-27-w14-unified-ledger.md) | All 12 tasks done & reviewed; C42 fixed a settlement crash; PR #16 merged to `main`; operator confirmed 2026-09-29 | Pending |
| W15 | Host Listings Dashboard & Copy Refinement | Done | — | — | Live listing metrics, listing-card redesign, Requests/wallet copy, and layout updates complete; operator explicitly waived new spec/plan | — |

**Handoffs into W3 (raised by W10, 2026-09-25; W3 reached `main` 2026-09-25, W10 merged `main` 2026-09-26 and the
stubs below are still open)** — honour or reconcile these:

1. **Auto-complete must skip bookings with an open ticket** (C17, backlog F7.3.1 updated). Any
   scheduler or service that moves `CONFIRMED → COMPLETED` after checkout + 7 days must leave a
   booking alone while a dispute ticket on it is `OPEN` or `IN_REVIEW`; its escrow stays held
   until an agent resolves the ticket.
2. **Reconcile the settlement service** (D5). W10 adds `DisputeSettlementService` for two-sided,
   full-escrow agent settlement (C20). `TransactionService.applyTicketRemedy` / `manualOverride`
   (single-sided) are not implemented or called by W10; decide at merge whether to fold `settle`
   into `TransactionServiceImpl` or keep both. On `origin/w3` those two methods are stubbed
   `throw new UnsupportedOperationException("Owned by W10")` and `settleBookingCompletion` as
   `"Owned by W8"` — W10 never edits `TransactionServiceImpl`, so at merge either delegate the two
   W10 stubs (`applyTicketRemedy`, `manualOverride`) to `DisputeSettlementService` or leave them
   unsupported.
   Also note: `WalletLedgerWriter` opens its own transaction and subtracts `feeAmount` from the
   balance, whereas the architecture doc and mock DB treat `feeAmount` on `BOOKING_PAYOUT` rows as
   informational (`amount` already net). W10 therefore writes wallet + ledger rows itself; whoever
   builds W8's payout settlement must not pass a fee through `WalletLedgerWriter` unchanged.
   **Merge finding (2026-09-26):** `BookingServiceImpl.forceTransition(...)` on `main` is stubbed `"Owned by W10"`, but force actions were dropped (C22), so W10 does not implement it; remove it from `BookingService` or leave it unsupported. `BookingServiceImpl.complete` (`"Owned by W8"`) and the auto-complete guard in item 1 are still unbuilt on `main`.
3. **Reconcile the state machine** (D9, C23). W10 allows `Role.AGENT` on `CONFIRMED → COMPLETED` in
   `BookingStateMachine`. If W3 touches that file, keep the AGENT permission.
4. **Shared wiring:** both branches edit `AppContext` (additive service/repository wiring) —
   expect a trivial conflict there.
5. **Mock DB:** `db/snoozeshare-mock.db` / `seed-mock-data.sql` now follow C17/C20 (open tickets
   sit on `CONFIRMED` bookings with escrow held; resolved tickets settle full escrow two-sided).
   W3 tests or seed logic that assumed the old rows (bookings 9, 10, 11, 13) must be re-checked.

Status vocabulary, used verbatim: `Not started` · `Spec'd` · `Planned` · `Building` ·
`Blocked — needs human` · `In review` · `Done` · `Abandoned`

Guide vocabulary — developer-guide coverage, owned by the `update-documentation` skill:
`—` · `Awaiting confirmation` · `Pending` · `Documented YYYY-MM-DD` · `N/A`

---

## 4. Architecture

**How the system is put together, area by area.** W1 implements the shared domain, SQLite
migration/transaction boundary, core auth/wallet/audit JDBC adapters, service contracts,
session, events, and native JavaFX shell. Feature-specific repositories and transaction policies
remain owned by later workstreams. The area descriptions below are reconciled from
[`docs/SnoozeShare-Architecture-Proposal.md`](docs/SnoozeShare-Architecture-Proposal.md)
(inferred/harvested, not yet built or verified in code), except where the repo already diverges
— noted inline and in § Deviations.

```text
ui.guest / ui.host / ui.admin  (JavaFX/FXML, role-isolated, each built on ui.common)
            │  depends on
        service          (interfaces only — ListingService, BookingService, TicketService,
            │              WalletService, TransactionService, UserService, ReviewService,
            │              AuditService)
   domain ──┴── events    (pure Java records/enums/state machines  |  in-process pub/sub)
            │
        repository        (DAO interfaces; only repository.jdbc.* touches java.sql.*)
            │
        infra.db           (SQLite, embedded)
```

Dependency direction is strictly top-to-bottom: `ui.* → service → domain`,
`service → repository → infra.db`, `service → infra.events`, everyone → `session`. The
`LayerDependencyTest` enforces the critical domain/UI boundaries; broader package checks remain
convention-level until feature adapters are added.

### 4.1 App shell & bootstrap

**Now:** `AppContext` bootstraps a fresh SQLite database, shared services, session, event bus,
audit service, and role-aware `SceneRouter`. W2 added wiring for `ListingService`,
`AvailabilityService`, and their backing JDBC repositories. W3 added `BookingService` and
`TransactionService` wiring. `Main` loads the combined auth screen or role shell with shared CSS
and a 1280×800 minimum window. `Launcher` remains the shaded-jar entry point.

| Path | Role |
|---|---|
| `src/main/java/com/snoozeshare/app/Main.java` | JavaFX entry point and shared shell bootstrap |
| `src/main/java/com/snoozeshare/app/Launcher.java` | Non-Application main() for the shaded jar |
| `com.snoozeshare.app.AppContext` | Wires shared services, repositories, events, and session |
| `com.snoozeshare.app.SceneRouter` | Role-aware screen navigation off `SessionContext.currentRole()` |

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C1 | unknown (pre-dates this file) | Package-based modularity (`com.snoozeshare.{domain,service,repository,infra,ui.*,events}`) in one Gradle module, not JPMS `module-info.java` | JavaFX + JPMS reflection/`opens`/`exports` fights are painful for a course-scoped MVP; an ArchUnit test can give the same guarantee | [architecture proposal §1](docs/SnoozeShare-Architecture-Proposal.md) |

### 4.2 UI (role-isolated)

**Now:** W1 provides the combined auth/register screen, shared CSS, and role routing.
All three role shells (Guest, Host, Agent) now use the Fall Light theme (`agent-theme.css`) with
a topbar + horizontal tab strip layout; the login page also carries the warm palette. Guest/host-specific
CSS classes are overridden under `.agent-root` in `agent-theme.css`. W2 adds the Guest search
screen and property detail modal. W3 adds the Trip Hub dashboard
(`ui.guest.trips.TripDashboardController` + `trip-dashboard.fxml`) with Pending/Upcoming/Active/
Completed/Cancelled tabs, trip cards with cancel buttons, event bus subscriptions for real-time
refresh, and the "Book Now" button on `ListingDetailController` wired to
`BookingService.submitRequest()`. W5 adds the Wallet dashboard
(`ui.guest.wallet.WalletDashboardController` + `wallet-dashboard.fxml`) with balance display,
transaction history cards, and a shared top-up/withdraw modal dialog
(`WalletActionDialogController`). `NavShellController` now subscribes to
`WalletTransactionRecordedEvent` for real-time sidebar balance updates. `theme.css` gains
wallet dashboard styles (transaction cards, amount coloring).
W6 adds the Host Listings page, separate create/edit form flow, clickable listing cards with a
host-facing detail page, listing status toggles, and owner-authorized listing updates.
The Guest and Host shells now expose separate wallet pages while sharing wallet dashboard logic;
the live balance is displayed in each header instead of a right-side panel.
Host navigation starts on Listings and exposes Requests, Messages, and Wallet; the Host Messages
page merges private booking chat with host-side ticket chat. Guest navigation labels the existing
search page as Search and loads it immediately after login.
Host Wallet now replaces the shell center directly, matching Guest Wallet’s full available wallet
area and modal-overlay behavior.
Booking escrow publishes `WalletTransactionRecordedEvent` after its transaction commits, keeping
wallet headers and wallet dashboards synchronized with the persisted balance.
Host Listings, Bookings, listing details, and listing forms now also replace the shell center
directly; their page titles and descriptions are defined inside each page view.
W8 adds the native Host Booking requests page with host-owned active/past tables styled similarly to the agent table, gross/net/rating
projections, approve/reject confirmation modals, optional persisted host rejection messages, and
startup/page-load completion sweeps.
The Host listing create/edit form uses a responsive two-column, four-card layout with
human-readable property/status dropdowns, text-field capacity inputs, two-column amenity tiles,
and create/edit-specific action labels.
The Host Listings page uses a `My listings` heading, wide metric/action cards, and `Requests`
navigation; Host Wallet actions use equal-width `Top up` and `Withdraw` buttons.
Host shell FXML is well-formed and loads successfully through `SceneRouter` after authentication.
W9 moves the Guest/Host wallet dashboard and action-dialog controllers and FXML into
`ui.common.wallet`, renders the complete wallet statement in a mockup-based table, and
supports top-up presets plus full-balance withdrawal selection for both roles. The wallet table
uses the Host Booking/Agent table rhythm; its balance separates large black dollar amount from
smaller grey `SGD`, and wallet action/modals use contained green confirmations with outlined
secondary actions.
Per the proposal,
each role gets its own FXML+Controller tree under `ui.<role>`, and `ui.common` holds shared
pieces (`WalletPanelController`, `NavShell`, formatting/validation helpers, shared components)
constructed once per session and embedded into whichever role shell is active. Controllers are
meant to depend only on `service.*` interfaces, never `repository.*` or `infra.db.*` directly.

**W10 Agent screens (built):** `ui.admin` has the canvas-style agent shell (top bar + tab strip: Disputes / Accounts / Audit Log / Categories; Accounts and Audit Log are placeholders) in the "Fall Light" palette (`agent-theme.css`, agent scene only; C24). Screens: `DisputeQueueController` (oldest-first table, All/Unassigned/Mine chips, width-fitted status dropdown, unassigned badge), `DisputeDetailController` (summary card, resizable guest/host chat boxes, one persisted internal-notes field with Save, Assign/Unassign, Accept / Reject / Manual actions; C25), `ResolutionDialogController` and `CategoryDialogController` (modal cards built on `AgentModal` with a 50% grey scrim; live refund/payout preview, reason required; category Add/Edit/Delete; C26) and `CategoryAdminController`. To try it on a copy of the mock DB, set env `SNOOZESHARE_DB_URL` (e.g. `jdbc:sqlite:build/acceptance.db`); `Main` passes it to `AppContext.create(jdbcUrl)`. **W12 adds the Audit Log tab** (`ui.admin.audit.AuditLogController`, read-only): one search box + action-type multi-select + separate From/To date pickers + Apply filters + a red-outline Clear button, a table with a `REF` column (`Booking #0009` / `Ticket #0004`), `Before → After` status cell and signed wallet-adjustment cell, and pagination via `AuditService.search` (C29, C32, C33). **Fixed table headers (C33):** in every agent table the header row stays put and only the rows scroll, with a slim scroll bar that starts below the header: the dispute-queue and audit `TableView`s size to their rows but shrink to the window and scroll internally (no outer `ScrollPane`), and the categories list scrolls inside a `ScrollPane` under its fixed header (`.agent-rows-scroll`). Audit rows share the queue's grey hover but keep the default cursor. The date pickers and the `MultiSelectMenu` popup are styled in `agent-theme.css` after the board's Date picker component. `AdminUiSmokeTest` and the snapshot tests exercise the screens on the FX toolkit and write `build/ui-snapshots/*.png`.

**W11 Accounts screen (built):** `ui.admin.accounts` holds `AccountGovernanceController` + `account-governance.fxml` (fixed-header table like Categories: only rows scroll; columns Display Name, Email, Role, Joined, Status pill with suspension reason, Action; Agent rows show a dash and the System user is hidden; grey row hover), a live search box (placeholder `Search...`, `AccountSearch` matches name, email, role, joined, status), and `SuspensionDialogController` + `suspension-dialog.fxml`, one `AgentModal` card for both Suspend (name large, email beneath, required reason) and Reactivate (banner `#40680C`). `AccountText` formats dates as `DD MMM YYYY`. `AdminShellController.showAccounts()` loads the screen and disposes its event subscription. Styles are in `agent-theme.css`. Smoke and snapshot tests write `build/ui-snapshots/agent-accounts.png` and modal PNGs.

**Visual design (S2, branch `ui-mockup`):** fully spec'd. The design spec is
[`docs/superpowers/specs/2026-09-23-ui-design-system-design.md`](docs/superpowers/specs/2026-09-23-ui-design-system-design.md)
— design tokens, the AtlantaFX theming mechanism, the component library mapping, app shell/nav,
and a full 31-artboard screen inventory traced to `docs/ProductBacklog.md` line items. The visual
source of truth is the Claude Design canvas it documents —
https://claude.ai/artifact/PWBCxbfv9e9FGVvY6RKUwd (shared: anyone with the link). No FXML/CSS
exists yet. Next per AGENTS.md §3: an implementation plan (`docs/superpowers/plans/`) before any
of this becomes code — not yet started. Two schema gaps found while grounding the spec against
`db/schema.sql` are recorded in § Deviations D2.

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C2 | unknown | `WalletPanelController` lives in `ui.common`, not duplicated per role | Top-up/withdraw/balance display is identical for guests and hosts | [architecture proposal §2](docs/SnoozeShare-Architecture-Proposal.md) |
| C11 | 2026-09-23 | UI design proceeded mockup-first: a Claude Design canvas built and iterated (3 review rounds) before the written spec, instead of spec-then-mockup. The spec was written once the canvas was validated — resolved, not still deferred. Key choices, all now in the spec: AtlantaFX `PrimerLight` base + runtime CSS-variable override (not a Sass rebuild) for the operator's Fall Light palette, plus explicit per-component radius/padding overrides (AtlantaFX's radius/spacing are compile-time Sass values, not runtime-overridable); "Spacious/Soft" density; a 4-tab `TabLine` shell per role (Guest: Search/Trips/Messages/Wallet, Host: Listings/Requests/Messages/Wallet, Agent: Disputes/Accounts/Audit Log/Categories); platform-default sans-serif (AtlantaFX bundles no font); light-only for now | Operator explicitly asked to skip the spec/plan and go straight to visual mockups via brainstorming + Claude's Design artifact type, after a full clarifying-questions pass validated each choice first; operator then asked for the spec once the canvas was fully reviewed | Operator conversation, 2026-09-23; verified against AtlantaFX's actual source (`mkpaz/atlantafx` `styles/src/`) rather than assumed, since an initial claim about bundled Inter was wrong; spec: [`docs/superpowers/specs/2026-09-23-ui-design-system-design.md`](docs/superpowers/specs/2026-09-23-ui-design-system-design.md) |

### 4.3 Service (application/business logic)

**Now:** Shared service interfaces plus user registration/authentication, wallet provisioning,
atomic wallet ledger, and audit implementation exist. W2 adds `AvailabilityServiceImpl` and
`ListingServiceImpl`. W3 adds `BookingServiceImpl` (submitRequest with atomic escrow+block,
cancel with 48h refund policy, decide for host approve/reject, tripsFor/pendingRequestsFor
queries) and `TransactionServiceImpl` (holdEscrow, refundEscrow, historyFor, and atomic
normal booking settlement; `applyTicketRemedy`/`manualOverride` remain owned by W10). `UserService`
gained a `findById(UUID)` method. The proposal specifies nine service interfaces
(`ListingService`, `AvailabilityService`, `BookingService`, `TicketService`, `WalletService`,
`TransactionService`, `UserService`, `ReviewService`, `AuditService`) with full method
signatures — see [architecture proposal §3.1](docs/SnoozeShare-Architecture-Proposal.md) for the
exact contracts, including which backlog item (F-number) each method backs.
W6 extends `ListingServiceImpl` with host-authorized listing creation, detail updates, and
status changes, with validation and audit records.
`ListingMetricsService` supplies host dashboard booking counts and average review ratings with
zero-value fallbacks for listings without activity.

**W4 adds:** `TicketServiceImpl.fileTicket()` (8 validation rules, event publishing, 7-day window),
`TicketServiceImpl.myTickets()`, `ReviewServiceImpl` (submit + hasReview), `JdbcReviewRepository`.

**W8 adds:** host booking row projections, message-aware host decisions, guarded completion and
net host payout settlement after the 7-day dispute window.

**W11 adds:** `AccountGovernanceServiceImpl` (`listAccounts`, `suspend`, `reactivate`): one `TransactionManager` transaction that flips the status, force-cancels the target's PENDING and not-yet-started CONFIRMED bookings with a full refund and block release, deactivates a host's ACTIVE listings (C36), and writes W12 audit rows (`ACCOUNT_SUSPENDED`, `BOOKING_FORCE_CANCELLED`, `ESCROW_REFUND`, `ACCOUNT_REACTIVATED`); the event is published after commit. `BookingServiceImpl` refuses bookings from a suspended guest. `UserService.suspend` was removed (D19 resolved).

**W13 adds:** `MessageServiceImpl` and `JdbcMessageRepository` replace `InMemoryMessageService`.
Hosts use the persistent host-side ticket thread to respond to agent-managed tickets; ticket filing
remains guest-only.

**W10 adds:** `TicketServiceImpl` (queue, assign, notes, resolve, category admin),
`DisputeSettlementServiceImpl` (atomic full-escrow two-sided settlement, C20),
`DisputeQueryServiceImpl` (queue/detail read models for the Agent UI) and a temporary
`InMemoryMessageService` (session-only chat; replaced by W13's persistent `MessageServiceImpl`).

**W14 adds (executed, all 12 tasks done and reviewed, Done — operator confirmed 2026-09-29):** `LedgerWriter` is now the
single wallet-write path used by every service that moves money — `WalletServiceImpl`,
`TransactionServiceImpl`, `BookingServiceImpl`, `DisputeSettlementServiceImpl`,
`AccountGovernanceServiceImpl`, `DisputeQueryServiceImpl`. It never opens its own DB transaction;
every caller wraps the call in the `TransactionManager` transaction that also carries the
booking/ticket/account state change, so a balance change, its money row, and the triggering
state change commit or roll back together (rollback tests cover hold, refund, settlement, dispute
settlement and account suspension). `WalletService`/`TransactionService` stay as separate
interfaces (C3 narrowed to storage, not removed, per C39) — they now both write through
`LedgerWriter` instead of each hand-rolling a "read wallet, add, save, insert ledger row" block.
On a host payout, `LedgerWriter` writes two rows in the same transaction: `BOOKING_PAYOUT` to the
host (net of 3%) and, when the fee is nonzero, `PLATFORM_FEE` to the System user's wallet (same
actor/booking/ticket ids). A zero fee writes no `PLATFORM_FEE` row; a guest refund never carries
one. **Task 8 finding (C42):** once `LedgerWriter` rejected a zero-amount write, a $0-rate or
sub-cent-rounding listing crashed booking settlement — and, because settlement also runs from
`AppContext`'s startup completion sweep, could crash app startup. The operator chose to forbid
degenerate listing rates (`baseNightlyRate` must be positive with at most 2 decimal places, so
the smallest legal total is $0.01 and always nets to a positive payout) rather than build
free-stay support through settlement/`EscrowPolicy`/disputes; fixed in commits `cb9e1d0`,
`a93f41e`. `AuditService.recordWalletTransaction` (a default method left dead once every caller
moved onto `LedgerWriter`) was deleted as unused (Task 10).

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C3 | unknown | Two separate financial interfaces: `WalletService` (dumb primitive — balance, top-up, withdraw) vs. `TransactionService` (business rules — escrow, 3% fee, refund policy, dispute remedies). UI may call `WalletService` directly only for top-up/withdrawal; `BookingService`/`TicketService` are the only callers of `TransactionService` (narrowed to storage by W14/C39, executed by W14, 2026-09-27: both interfaces kept, both now write through `LedgerWriter`) | Keeps "how do bookings pay out" and "how do I add money to my account" independently testable; centralizes every balance change behind one choke point so wallets can't drift from booking/ticket state | [architecture proposal §3](docs/SnoozeShare-Architecture-Proposal.md) |
| C4 | unknown | Use Java 25 records directly as the domain model passed to JavaFX view models; no separate DTO layer | Over-engineering for MVP scale | [architecture proposal §3](docs/SnoozeShare-Architecture-Proposal.md) |
| C7 | 2026-09-22 | **No guest-side service fee.** `bookings.totalAmount = nightlyRateSnapshot × nights`, full stop. The only platform fee anywhere is the 3% deducted from a host's `BOOKING_PAYOUT` (already specified). Corrects an earlier mock-data draft that had invented a 5% guest fee to fill the doc's undefined `serviceFeeAmount` column — that column is now removed | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C8 | 2026-09-22 | `REJECTED` and `CANCELLED_BY_HOST` bookings both trigger a 100% `ESCROW_REFUND`, same as a guest cancelling >48h out. (Note: `BookingService` in the proposal has no explicit host-initiated-cancel method distinct from `decide(...,approve=false)` — flagged as a spec gap for whoever builds F6.1.2/host cancellation, not resolved by this decision.) | Operator confirmed "good assumption" while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C9 | 2026-09-22 | *(Superseded for agent ticket resolutions by C20, 2026-09-25 — those are now two-sided: guest refund row + host payout row.)* `TICKET_REMEDY` and `AGENT_OVERRIDE` wallet rows are single-sided — only the wallet actually credited/debited gets a row, no matching entry on the other side. There is no double-entry anywhere in `wallet_transactions`, extending the doc's existing "fees aren't a real platform-wallet transfer" note to these two types as well. (Executed by W14, 2026-09-27: `wallet_transactions` itself is gone; ticket remedies and overrides stay single-sided money rows in `audit_log`, only host payouts gained a second, System-side `PLATFORM_FEE` row.) | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C10 | 2026-09-22 | `wallets.currency` is `"SGD"` for real, not just the doc's illustrative example | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |

### 4.4 Domain

**Now:** Records, enums, validation, and booking/ticket state machines are implemented as pure
Java with zero
JavaFX/JDBC dependencies. `BookingStateMachine.canTransition(from, to, actingRole)` and
`TicketStateMachine` are meant to be the single legality check every role's service call goes
through (guest cancel, host approve/reject, agent force-override all call the same function).

**W10 adds:** `domain.settlement` (`SettlementCalculator` for refund/host-share/fee math,
`EscrowPolicy` for the escrow-held check) and the `Role.AGENT` permission on
`BookingStateMachine` `CONFIRMED -> COMPLETED` (C23, D9).

| Path | Role |
|---|---|
| `domain.model` *(planned)* | `User`, `Property` (fields: `propertyId`, `hostId`, `status`, `title`, `description`, `propertyType`, `streetAddress`, `city`, `region`, `postalCode`, `maxGuests`, `bedrooms`, `bathrooms`, `baseNightlyRate`, `checkInTime`, `checkOutTime`, `amenities` — operator-specified, see C6), `Booking`, `Wallet`, `WalletTransaction`, `Ticket`, `TicketCategory`, `Review`, `AuditLogEntry`, `AvailabilityBlock` |
| `domain.enums` *(planned)* | `Role`, `ListingStatus`, `PropertyType`, `AmenityType`, `BookingStatus`, `TicketStatus`, `RemedyType`, `WalletTransactionType`, `AccountStatus` |
| `domain.statemachine` *(planned)* | `BookingStateMachine`, `TicketStateMachine` |

### 4.5 Repository & persistence

**Now:** Repository interfaces exist for all aggregates. W1 implements SQLite migrations and core
`User`, `Wallet`, and `AuditLog` JDBC adapters. **W14 replaced the `WalletTransaction`/
`wallet_transactions` adapter with `LedgerRepository`/`JdbcLedgerRepository`, which reads money
rows straight out of `audit_log`** (a money row = any `audit_log` row with `walletAdjustment IS
NOT NULL` and a `balanceAfter`); `wallet_transactions` no longer exists (dropped in V008) and
`WalletTransactionRepository`/`JdbcWalletTransactionRepository` were deleted. `WalletTransaction`
itself survives as a pure read-model record built from those rows (no `feeAmount` field — the fee
is its own `PLATFORM_FEE` row to the System wallet, not a column on the payout row). W2 adds
`JdbcPropertyRepository` (dynamic WHERE clause building for search, UPSERT for save),
`JdbcAvailabilityBlockRepository` (overlap detection: `startDate < ? AND endDate > ?`), and
`JdbcBookingRepository` (overlap detection filtering PENDING+CONFIRMED only, host/history and
completion projections), `JdbcReviewRepository`, and `JdbcTicketRepository` booking lookup.
Shared `RowMappers`
centralizes result-set-to-record mapping with graceful unknown-amenity handling. Remaining
feature-specific aggregate adapters are owned by the workstreams that implement those features. The proposal specifies one
repository interface per aggregate (`UserRepository`,
`PropertyRepository`, `BookingRepository`, `AvailabilityBlockRepository`, `TicketRepository`,
`WalletRepository`, `WalletTransactionRepository`, `ReviewRepository`, `AuditLogRepository`),
each returning/consuming domain records — only `repository.jdbc.*` may import `java.sql.*`.
Schema was originally specified table-by-table in
[architecture proposal §4](docs/SnoozeShare-Architecture-Proposal.md) (users, properties,
availability_blocks, bookings (including nullable `hostDecisionMessage`), wallets, wallet_transactions — an append-only ledger, tickets,
ticket_categories, reviews, audit_log); **`wallet_transactions` in that list is now historical —
W14's V008 dropped the table, and `audit_log` is the only ledger.** A hand-written (non-Flyway)
copy of this schema plus a full mock dataset has been built
into a **shared, committed reference DB** — `db/schema.sql` / `db/seed-mock-data.sql` /
`db/snoozeshare-mock.db` — for the team to query together; it is a dev/reference artifact only,
not loaded by the application at startup. See § Record.

**W11 adds:** nullable `users.suspensionReason` (migration `V003__suspension_reason.sql`, applied or adopted by `MigrationRunner`; mock DB rebuilt with v3 history and seeded reasons), `UserRepository.findAll()`, `BookingRepository.findByListing(UUID)`, and a `suspensionReason` component on `User` (7-arg constructor kept).

**W14 adds:** three migrations. `V006__system_role.sql` rebuilds `users` (SQLite cannot alter a
CHECK constraint) so `role` allows `SYSTEM` alongside `GUEST`/`HOST`/`AGENT`; the System user
(`AuditService.SYSTEM_ACTOR_ID`) keeps its id and becomes role `SYSTEM`, status `ACTIVE` (no
longer `AGENT`+`SUSPENDED`), and gets a real wallet — provisioned by the migration/seed, not by
`WalletProvisioningService`, which still skips it. `MigrationRunner` runs V006 with foreign keys
off (the rebuild touches every table with a `users` FK) and checks `PRAGMA foreign_key_check`
before committing. `V007__unified_ledger.sql` adds `audit_log.balanceAfter`, backfills a money row
for every legacy `wallet_transactions` row that had none, backfills a `PLATFORM_FEE` row for every
legacy `BOOKING_PAYOUT` with a nonzero `feeAmount`, and creates the System wallet if missing.
`V008__drop_wallet_transactions.sql` drops the table once every caller had moved onto
`LedgerWriter`/`LedgerRepository` (split out from V007 as a deviation — see § Deviations — so the
strangler migration could land safely one caller at a time). The mock DB ships already migrated
through `schema_history` v8 with its 37 money rows verified byte-identical to the pre-drop
derivation.

**W10 adds:** `JdbcTicketRepository` and `JdbcTicketCategoryRepository`, and `MigrationRunner` now
adopts a pre-provisioned database (one with tables but no `schema_history`, such as the mock DB) by
recording the baseline (D10).

**W8 adds:** migration V002 for `bookings.hostDecisionMessage`; `MigrationRunner` applies it to
fresh and adopted databases, while JDBC booking reads/writes remain compatible with older
pre-provisioned copies. **W10 adds:** `JdbcTicketRepository` and `JdbcTicketCategoryRepository`,
and `MigrationRunner` adopts a pre-provisioned database (D10).

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C6 | 2026-09-22 | `properties` columns are exactly: `propertyId`, `hostId`, `status` (`ListingStatus`: ACTIVE/INACTIVE), `title`, `description`, `propertyType` (`APARTMENT`/`HOUSE`/`CONDO`/`PRIVATE_ROOM`), `streetAddress`, `city`, `region`, `postalCode`, `maxGuests`, `bedrooms`, `bathrooms`, `baseNightlyRate`, `checkInTime`, `checkOutTime`, `amenities` (`Set<WIFI,PARKING,AIR_CONDITIONING,KITCHEN,WASHER,WORK_DESK>`) | Operator supplied the authoritative field list (the architecture proposal had left it as "exactly your House fields" with no listing) — corrects an earlier draft `db/schema.sql` that had guessed different, non-matching field names/enum values | Operator conversation, 2026-09-22 |
| C5 | 2026-09-22 | **Embedded DB is SQLite** (`org.xerial:sqlite-jdbc`), accessed via hand-rolled `PreparedStatement` + `RowMapper` DAOs — no JPA/Hibernate | Operator confirmed SQLite over H2 (resolves Q2 — the architecture proposal's heading was the correct signal, its H2-flavored rationale paragraph was not). `build.gradle` and `.gitignore` updated accordingly. Rationale per the proposal otherwise stands: relational for FK-heavy data, transactional guarantees for overlap-prevention and escrow correctness, zero external services. | Operator conversation, 2026-09-22; `build.gradle`, `.gitignore`, [architecture proposal §4](docs/SnoozeShare-Architecture-Proposal.md) |

### 4.6 Events (in-process bus)

**Now:** `InProcessEventBus` provides typed synchronous subscriptions, unsubscribe handles, and
subscriber-failure isolation. Concrete shared event records include booking, ticket, wallet, and
availability events. It is used by the wallet ledger to publish only after commit.
Specified as a simple `Consumer<Event>` pub/sub
(`EventBus.publish` / `subscribe`) over a sealed `DomainEvent` hierarchy
(`BookingConfirmedEvent`, `BookingCancelledEvent`, `TicketOpenedEvent`, `TicketResolvedEvent`,
`WalletTransactionRecordedEvent`, `ListingAvailabilityChangedEvent`), used so cross-role UI
refresh (e.g. Guest Trip Hub reflecting a Host decision) doesn't require the publisher to know
the subscriber exists.

**W11 adds:** `AccountStatusChangedEvent`, published after an account suspend or reactivate commits so the Accounts screen refreshes.

### 4.6a Messaging (W13)

**Now:** `MessageService` (F12) is persistent. Tables `messages` and `message_reads` (migration `V004__messaging.sql`; `MigrationRunner` applies or adopts it; the mock DB ships with seeded threads on tickets 2 and 3) hold one GUEST and one HOST thread per ticket. `MessageServiceImpl` checks every call against ticket → booking → property: a guest reads and posts only the GUEST thread, a host only the HOST thread, any agent both; posting to a resolved ticket throws `IllegalStateException`. Order is insertion order (`rowid`), not `sentAt` text. `post` publishes `MessagePostedEvent` after the insert. `conversationsFor`, `unreadCount` and `markRead` back the Host Messages tab and remain the contract for the deferred Guest tab. The Host page merges ticket summaries with booking summaries, renders the selected thread through `ChatBubbles`, marks rows read, refreshes on both message events, and disables chat inputs on resolved tickets. `TicketRepository.findByParty` backs the inbox.

**Booking conversations (C38):** a second, private host↔guest chat scoped to a booking. `BookingConversationService` (`thread`, `post`, `conversationsFor`, `unreadCount`, `markRead`) over tables `booking_messages` and `booking_message_reads` (migration V005) publishes `BookingMessagePostedEvent`. Writable while the booking is `CONFIRMED` and up to check-out + 7 days, read-only afterwards; only the booking's guest and host take part (agents may not read it, decided in C38 and spec § 9). Text only, no edit/delete. The Host Messages tab consumes it; the Guest tab remains deferred. The mock DB has a chat on every ticket and on bookings 3, 4, 9, 10, 11, 14.

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C37 | 2026-09-27 | Recorded in full in § Decisions | Operator | [W13 spec](docs/superpowers/specs/2026-09-27-w13-messaging-service-design.md) |

### 4.7 Session

**Now:** `SessionContext` and `MockSessionContext` provide optional current user, null-safe
unauthenticated role, login, and logout. Specified
as `Optional<User> currentUser()`, `Role currentRole()`, `loginAs(User)` (mocked — sets state, no
credential check), `logout()`. Every service method that needs an actor takes the acting user's
id explicitly rather than reaching into a global, so services stay unit-testable without a live
session.

### 4.8 Audit trail

**Now:** `audit_log` is one row per change (C28) and, as of W14, **the single record of money
movement** — `wallet_transactions` no longer exists. A money row is any `audit_log` row with
`walletAdjustment IS NOT NULL` and a `balanceAfter`. Migration `V002__audit_trail.sql` adds 7
columns to the V001 table: `actorName`, `walletAdjustment` (signed, wallet rows only), `reason`,
`ticketId`, `bookingId`, `subjectUserId`, `subjectName`, plus indexes on timestamp/actor/subject/
booking/ticket; W14's `V007__unified_ledger.sql` adds `balanceAfter`. The seeded non-loginable
**System user** (`AuditService.SYSTEM_ACTOR_ID`) is role **`SYSTEM`**, status `ACTIVE`, with a
real wallet that receives a `PLATFORM_FEE` row on every host payout that carries a nonzero fee
(reverses C29/C32's `AGENT`+`SUSPENDED` choice; see C40, C42). `beforeState`/`afterState` hold
status text only. Rows are written through `AuditRecord.builder(...)` and `AuditService.record(...)`
**on the caller's connection**, so a state change and its money rows commit or roll back together
(rollback tests exist). A ticket resolution writes 4 rows: ticket status, booking status, guest
wallet adjustment, host wallet adjustment (net of fee; zero-amount sides skipped), plus a
`PLATFORM_FEE` row when the fee side is nonzero. `AuditService.search(AuditFilter, limit, offset)`
returns newest first; a name in the search text is resolved to user ids first and the log queried
by id. The Audit Log screen is described in § 4.2 (`PLATFORM_FEE` is one more action type in its
multi-select). **Single-ledger rule (replaces the former "dual-write until W14" note):** every
wallet balance change is one `LedgerWriter` call that inserts exactly one `audit_log` money row
(two for a host payout with a fee) in the same transaction as the balance update — there is no
second ledger table to keep in sync.

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C28–C32 | 2026-09-26 | Audit structure, filters, System user, platform fee as reason text only, W14 split, account-governance rows reserved for W11 — recorded in full in § Decisions | Operator | [W12 spec](docs/superpowers/specs/2026-09-26-w12-platform-audit-trail-design.md) |
| C39–C42 | 2026-09-27 | W14 executed: System user becomes role `SYSTEM` with a real wallet, `wallet_transactions` folded into `audit_log`, one `LedgerWriter` — recorded in full in § Decisions | Operator | [W14 spec](docs/superpowers/specs/2026-09-27-w14-unified-ledger-design.md) |

---

## 5. Conventions

Durable rules, harvested from the architecture proposal. Enforcement is partial: `LayerDependencyTest` and `UiDependencyTest` check that `ui` does not import
`repository`/`java.sql` and that `domain` is free of JavaFX/`java.sql`; the other rules (e.g. `ui` not importing
`infra.db`) are intent until a workstream adds a test.

- `ui.*` must never import `repository.*` or `infra.db.*` directly — only `service.*`.
- Only `repository.jdbc.*` may import `java.sql.*`.
- `BookingService`/`TicketService` are the only callers of `TransactionService`; UI never calls
  `TransactionService` directly, and never calls `WalletService.topUp/withdraw` from inside a
  booking/ticket flow.
- `TransactionService` never touches `WalletRepository` directly — it goes through
  `WalletService` so balance updates and the transaction-log write stay atomic in one DB
  transaction.
- All state transitions (booking, ticket) go through the shared `*StateMachine`, never
  re-implemented per role/UI.
- One audit row per change: a state change and its money movements are separate rows written in the same DB transaction, via `AuditRecord.builder`. Audit rows are only ever written by services, on the caller's connection.
- `wallets.balance` is a denormalized cache of the owner's newest money row's `balanceAfter` in
  `audit_log` — it is only ever written in the same DB transaction as the triggering money row
  insert, never independently.
- Only `LedgerWriter` changes a wallet balance; it never opens a transaction — every caller wraps
  the call in `TransactionManager` so the balance, the money row, and any related state change
  commit or roll back together.
- **ID namespaces:** `W` = workstream, `C` = decision, `D` = deviation, `Q` = needs a human,
  `S` = session. Numbers are unique across the whole file and never reused, wherever the entry
  sits.
- Agent interaction logs are append-only, one file per session under `logs/`, named with the SGT
  session-start timestamp and branch slug; that filename stem is the canonical session key. The
  logging skill is manually invoked at session end. Numeric `S` labels in the session table are not
  log identities; there is no consolidated log currently.

---

## 6. Known Gaps & Accepted Limitations

Harvested from [architecture proposal §6, "What I'd revisit as scope grows"](docs/SnoozeShare-Architecture-Proposal.md)
and the stated assumptions/constraints. **These are decisions, not a TODO list — do not
"helpfully" implement them.**

- **No multi-instance / distributed deployment support** — single-process desktop app by design;
  the `service` layer interfaces are deliberately shaped so they *could* become a REST/gRPC
  boundary later, but nothing does that now.
- **Ticket categories/remedy types are a status enum + a simple `ticket_categories` lookup
  table, not a rules engine** — revisit only if workflow branching is actually needed.
- **Booking overlap-check + escrow hold use a `SERIALIZABLE` DB transaction, not
  optimistic-locking or a queue** — acceptable for single-process MVP concurrency; would need to
  change if concurrent booking volume ever mattered.
- **Top-up/withdraw are fully mocked** — `WalletService` just credits/debits balance, no real
  payment gateway. A real integration would slot in behind the same interface.
- **Auth is fully mocked** — no real credential validation anywhere; role separation is enforced
  in service/domain code, not by real authentication. This is a stated project constraint, not
  an oversight.

- **Audit timestamps are `Instant.toString()` strings (W12).** Variable-length fractional seconds can misorder rows within the same second under `ORDER BY timestamp DESC`. Accepted; do not "fix" without a decision.
- **Audit Log UI polish left open (W12):** the `REF` column truncates when a row has both a booking and a ticket (the double-bordered date pickers were fixed by C33). The design board artifact is older than the spec (it has User ID / Booking ID inputs and no REF); the spec and C29 supersede it.

**Explicitly out of scope:** microservices, message brokers, horizontal scaling, JPMS module
boundaries, JPA/Hibernate, a separate DTO layer distinct from domain records.

---

## 7. Needs a Human

| ID | What is needed | What it blocks | Raised |
|---|---|---|---|
| — | No outstanding questions (Q3 resolved 2026-09-26 as C36 and the C34(d) amendment) | — | — |

---

## 8. Decisions & Context

Process and priority decisions are recorded here; technical decisions that shape a specific
architecture area remain recorded in that area's table.

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C11 | 2026-09-23 | Use one-workstream-at-a-time development with TDD-first vertical slices; parallel agents are limited to independent review and documentation | Operator approved the Q1 recommendation to keep implementation ownership and integration boundaries clear | Operator conversation, 2026-09-23 |
| C12 | 2026-09-23 | Host and Agent registration codes are mock constants | Operator approved this F0 simplification; real credential or configuration management is out of scope | Operator conversation, 2026-09-23 |
| C13 | 2026-09-23 | W1 owns the complete shared foundation and all cross-cutting logic required by later workstreams; feature workstreams own feature-specific business rules and UI behavior | Operator clarified that W1 must provide the full common base before parallel development begins | Operator conversation, 2026-09-23 |
| C14 | 2026-09-23 | Use a combined Login/Register entry screen; display Support Agent as the user-facing Agent role; use a shared header/left-navigation/content shell, shared CSS, Guest/Host wallet panels, and a minimum window size around 1280×800 | Operator approved all W1 UI recommendations before execution | Operator conversation, 2026-09-23 |
| C16 | 2026-09-25 | W10 is built concurrently with W3/W4 against the service interfaces (no waiting on `origin/w3`). W10 owns the ticket persistence + `TicketServiceImpl` + Agent UI; its remedy/override money logic lives in its own class (not inside W3's `TransactionServiceImpl`) so the only shared touchpoints are `AppContext` wiring and one new `BookingService` agent force-transition method. Tests use fakes/seeded tickets; guest ticket filing (W4) is out of W10 scope | Operator pointed out that interface-based design should allow concurrent development; agent had over-stated the coupling | Operator conversation, 2026-09-25 |
| C17 | 2026-09-25 | Escrow is held through the 7-day dispute window (checkout + 7d). A ticket opened in the window keeps escrow on hold and the agent fully controls its settlement; only an unchallenged window releases escrow to the host. Consequence: agent money overrides always operate on **held escrow** — no clawback from host wallets, no `COMPLETED`-and-paid-out case. Requires W3/W8's auto-complete trigger (F7.3.1) to skip bookings with an open ticket | Operator answer while scoping W10 override semantics; consistent with `docs/ProductBacklog.md` F7.3.1 and F3.1.1 | Operator conversation, 2026-09-25 |
| C18 | 2026-09-25 | On an agent Full Payout or Partial split, the 3% platform fee applies to the host's share (host gets share × 0.97, fee recorded informationally as with `BOOKING_PAYOUT`); a Full Refund to guest carries no fee | Operator chose the recommended uniform-fee rule | Operator conversation, 2026-09-25 |
| C19 | 2026-09-25 | Agent Force Cancel = 100% escrow refund to guest (per C8); Force Complete = normal settlement, host paid net of 3%. Each is one atomic action with a required reason and an audit row. Other splits go through the ticket money override, not the force action | Operator chose the recommended coupled-defaults option | Operator conversation, 2026-09-25 |
| C20 | 2026-09-25 | Every ticket resolution and manual adjustment settles the **full** held escrow: guest refund R (no fee), host receives (escrow − R) × 0.97. Platform cut is only ever taken from host earnings, never from guest money. Accept = requested remedy; Reject = R 0 (host paid in full, net of fee); Manual = agent-set R (Full refund = all, Full payout = 0). Supersedes the mockup's single-wallet "Adjust wallet" dropdown; the mockups are otherwise accurate but the operator's discussions take precedence | Operator answer while reviewing the W10 design artifact | Operator conversation, 2026-09-25 |
| C21 | 2026-09-25 | Ticket chat threads (guest↔agent and host↔agent, as shown in the design artifact's dispute detail) are required. No backing columns or service exist today and the backlog has no messaging epic. **Ownership: a general `MessageService` owned by new workstream W13 (Messaging)** — the agent chat is just another participant in the same chat, so it does not belong to W10 (the first draft had W10 owning a `ticket_messages` table; operator corrected this). W10 codes against a `MessageService` interface with a fake in tests | Operator: "There should be chat threads"; then "shouldn't the service that configures the chat be the one to own this?" | Operator conversation, 2026-09-25 |
| C22 | 2026-09-25 | **Reverses C19 and drops backlog F9.2.1 (Force Cancel / Force Complete) from W10.** Force actions are redundant: Accept with full refund ≡ force cancel, Reject ≡ force complete, and both close the ticket. The agent only ever settles via ticket resolution. F9.2.1, the two Force confirm artboards, and the "Booking state override" block on the dispute detail artboard are out of scope. `BookingStatus.FORCE_*` enum values stay in the enum, unused by W10 | Operator: force actions are just a forced ticket close the agent can already achieve by accepting/rejecting | Operator conversation, 2026-09-25 |
| C23 | 2026-09-25 | Ticket resolution moves the booking `CONFIRMED → COMPLETED` (funds settled per C20), which requires allowing `Role.AGENT` on that transition in `BookingStateMachine` (small additive change to a W1 file). Bookings in the dispute window are `CONFIRMED`; "Stay ended — escrow held" is a derived display label (`CONFIRMED`, stay over, escrow still held — within the 7-day window, or beyond it while a ticket is open), not a new status | Operator chose the recommended option; also clarifies the "COMPLETED with open ticket" mock rows meant stay-over-funds-held | Operator conversation, 2026-09-25 |
| C24 | 2026-09-25 | **Reverses D11 for the agent screens.** After the manual visual check the operator ruled that layout and palette differences from the design canvas (artifact `PWBCxbfv9e9FGVvY6RKUwd`: `AgentDisputeQueue`, `AgentDisputeDetail`, `AgentTicketCategories`, `ConfirmDispute*`) are defects, not accepted deviations. The agent shell becomes the canvas layout (top bar + horizontal tab strip Disputes / Accounts / Audit Log / Categories) in the canvas "Fall Light" palette, applied to the agent scene only via a new `agent-theme.css` (guest/host shells untouched). Still intentionally different from the canvas: no Force block (C22), refund-amount field instead of "Adjust wallet" (C20), `SGD` currency label (C10), native dialog without the dimmed backdrop. | Operator, on seeing the sidebar/navy UI. Rejected: keeping D11 as a documented deviation. |
| C25 | 2026-09-25 | After the second visual check the operator changed three behaviours: (1) internal notes become ONE persisted free-text field per ticket (`Ticket.agentNotes`, replaced on Save, autopopulated on return; no history list; white background like the chat panes; "Add note" becomes a "Save" button in the Send-button colour); (2) "Assign to me" becomes "Unassign" once the ticket is assigned to the signed-in agent (`UNDER_REVIEW` -> `OPEN`, assignee cleared, persisted, audited `TICKET_UNASSIGNED`), so `TicketStateMachine` now allows `UNDER_REVIEW -> OPEN` for agents; (3) dialogs, summary card and status dropdown restyled to the canvas exactly (no shadows in dialogs, border around the whole modal). | Operator. Rejected: note history list; disabling the assign button after assign. |
| C26 | 2026-09-25 | Third visual round, operator: dropdown text and options right-aligned; pointer cursor on ticket rows; chat boxes (resized together) and the notes box are user-resizable with minimum heights, notes Save button moves to the bottom-right of the field; every modal gets a light-grey 50% scrim over the window behind it (reverses the "no dimmed backdrop" exception in C24); Add/Edit category use the same modal card design, and the Edit modal gets a red Delete. **Scope decision:** category delete is a NEW capability beyond F9.3.1 (add/rename/toggle); a category still referenced by any ticket cannot be deleted (use the active toggle instead). | Operator. |
| C27 | 2026-09-25 | Ticket status `UNDER_REVIEW` is renamed **`IN_REVIEW`** everywhere (enum, `tickets.status` CHECK in `db/schema.sql` and `V001__foundation.sql`, seed data, mock DB, tests, UI text "In review", messages, variable names) so code and UI use one term. Status filter labels are now All statuses / Open / In review / Approved / Rejected, the dropdown is as narrow as its widest option (selector and popup the same width) and text is left-aligned again (reverses the right-alignment in C26 item 1). **Cross-workstream impact:** any other branch that references `TicketStatus.UNDER_REVIEW` or the string `'UNDER_REVIEW'` must rename it at merge; the architecture proposal's status list is updated in the same change. | Operator (inconsistency between UI and code). Rejected: UI-only rename. |
| C28 | 2026-09-26 | **Audit log structure (W12).** One row = one change. `beforeState`/`afterState` hold status text only; new typed columns `walletAdjustment` (signed, wallet rows only), `reason`, `ticketId`, `bookingId`, `subjectUserId`, plus name snapshots `actorName`/`subjectName`. A ticket resolution writes 4 rows: ticket status, booking status, guest wallet adjustment, host wallet adjustment (net of fee; zero-amount sides skipped). Supersedes D2 point 2 (no JSON-projection layer). | Operator: the single `afterState` blob was inappropriate. Operator confirmed the 4-row set (reading "host and guest side" as the two wallets, since a booking is one entity), real columns over JSON, and the extra id columns. Rejected: two per-side Booking rows; keeping JSON with fixed keys; keeping `entityId`-only lookups. | Operator conversation, 2026-09-26; [W12 spec § 3](docs/superpowers/specs/2026-09-26-w12-platform-audit-trail-design.md) |
| C29 | 2026-09-26 | Audit Log screen filters are ONE search box + action-type filter (a single combo here; superseded by the multi-select in C33) + From/To date range (replaces the board's User ID and Booking ID inputs). Name text is resolved to user ids first and the log queried by id, so history survives renames (names are also snapshotted on each row). System-initiated rows are attributed to a seeded non-loginable System user (actorUserId stays NOT NULL). | Operator | Operator conversation, 2026-09-26; W12 spec § 5–6 |
| C30 | 2026-09-26 | Platform fee stays **reason-text only** in W12; the § Known Gaps entry "no platform wallet" is KEPT for W12. Operator wants the fee eventually as a logged entry on a System account wallet, delivered by W14. | Operator; asked to keep-or-reverse the Known Gap per AGENTS.md § 4, chose to defer the reversal to W14 | Operator conversation, 2026-09-26 |
| C31 | 2026-09-26 | **Reversal, executed by W14, 2026-09-27** (originally recorded as planned/not-yet-executed): fold `wallet_transactions` into `audit_log` (it already functions as a log) and create a System account with a real wallet. Reverses the Known Gaps entry, C3 (WalletService/TransactionService split, narrowed not removed — see C39), C9 (single-sided rows, partially — see C9's own note) and the `wallets.balance` cache convention. **Not executed as originally written:** no balance was added to `users` — `wallets` stays as its own table (C40 reversed that part first). Until W14 ran, W12 dual-write wallet rows (ledger + audit) in one transaction; that dual-write is gone. Split from W12 because it touches 36 main / 18 test files and the open W3 merge handoffs. | Operator; recommended split accepted over doing it inside W12 | Operator conversation, 2026-09-26; W12 spec § 2, § 8 |
| C32 | 2026-09-26 | W12 spec approved with its § 10 defaults: `REF` column added to the Audit Log table; System user is role `AGENT` + `SUSPENDED`; Status cell shows `Before → After`; W7 blocks not audited. **Account-governance actions must also be audit-logged: the W12 schema/`AuditAction` accommodate them (spec § 4a), implementation is reserved for W11.** The suspension reason is stored as `reason` on the `ACCOUNT_SUSPENDED` row, which resolves D2 point 1 without a `users.suspensionReason` column. **The `AGENT`+`SUSPENDED` System-user choice was reversed by W14/C40 (executed by W14, 2026-09-27): the System user is now role `SYSTEM`, status `ACTIVE`.** | Operator | Operator conversation, 2026-09-26; W12 spec § 4a, § 10 |
| C33 | 2026-09-26 | **Operator UI amendments to the W12 Audit Log (supersede C29's single action combo and parts of the spec § 5-6):** (1) the action type is a MULTI-select (`MultiSelectMenu`, empty = all actions) with the same 34px height as the other filter controls, so `AuditFilter.actions` / `AuditCriteria.actionTypes` are sets and the repository binds an `IN` clause; (2) From and To stay two separate date pickers (no range), one border each, popup styled after the board's *Date picker* component (artifact `PWBCxbfv9e9FGVvY6RKUwd`, `Main.dc.html` Date picker), no Clear/Apply inside the picker, click selects; (3) the master Clear is a real red-outline button (`agent-button-danger`); the **Apply filters button is kept** (the spec does not remove it, selecting does not auto-apply); (4) **fixed table headers apply to ALL agent tables**: only the rows scroll, scroll bar starts below the header; (5) audit rows get the ticket queue's grey hover with no hand cursor (rows are not clickable). | Operator, reviewing the built Audit Log screen. Rejected: an auto-applying filter; removing Apply; a range picker. | Operator conversation, 2026-09-26; [W12 spec § 5–6](docs/superpowers/specs/2026-09-26-w12-platform-audit-trail-design.md) |
| C34 | 2026-09-26 | W11 scope: (a) **Reactivate** is built although F10.1.1 says only suspend (new capability, like C26 delete); (b) the F10.1.2 cascade (pending bookings force-cancelled with 100% refund, host's active listings set inactive; CONFIRMED stays untouched) is in W11; (c) Agent accounts are listed with no action, and agents cannot be suspended or self-suspend; (d) suspension reason lives in a nullable `users.suspensionReason` column (migration V003), **reversing the no-column part of C32**; operator: "logs are not meant to be data storage"; the reason is also written to the audit row | Operator answers while scoping W11. Rejected: a `suspensions` table (overkill), deriving from `audit_log` (slow, coupled to W12), suspend-only, cascade deferred, hiding or suspending agent rows | Operator conversation, 2026-09-26; [W11 spec](docs/superpowers/specs/2026-09-26-w11-account-governance-design.md) |
| C36 | 2026-09-26 | W11 cascade scope widened: suspension force-cancels `PENDING` **and not-yet-started `CONFIRMED`** bookings (100% refund), matching W12 § 4a and seed booking 12; started/ended `CONFIRMED` stays are untouched (W10). Reason column kept (C34(d) reverses part of C32). Accounts search matches display name, email, role, joined, status (placeholder `Search...`). Spec follows W12 audit shapes and fixed-header table pattern (C33) | Operator answers to Q3 | Operator conversation, 2026-09-26; [W11 spec](docs/superpowers/specs/2026-09-26-w11-account-governance-design.md) |
| C35 | 2026-09-26 | W11 UI deltas from the canvas: first column "Display Name" plus a new Email column; dates (Joined, both modals) as `DD MMM YYYY`; Suspend modal banner shows display name large with the full email beneath; Reactivate modal banner uses `#40680C` (from the Force Complete mock-up); modals use `AgentModal` scrim (C26) | Operator, 2026-09-26 | Operator conversation; [W11 spec](docs/superpowers/specs/2026-09-26-w11-account-governance-design.md) § 2 |
| C37 | 2026-09-27 | W13 scope: replace `InMemoryMessageService` with a persistent `MessageService` (tables `messages`, `message_reads`, migration V004), move the agent dispute page onto it, and define the guest/host contract (`conversationsFor`, `unreadCount`, `markRead`, `MessagePostedEvent`); guest/host Messages UI deferred. Resolved tickets become read-only. F7.2.2 stays deferred (C29). | Operator, 2026-09-27: "the actual implementation and UI of the messaging service on host and guest can be deferred"; chose service + agent page over interfaces-only | Operator conversation; [W13 spec](docs/superpowers/specs/2026-09-27-w13-messaging-service-design.md) |
| C38 | 2026-09-27 | **Reverses the W13 spec's exclusion of chat outside tickets.** Add a booking-scoped host↔guest chat, a copy of the ticket chat without the agent: text only, no attachments, no edit/delete, no typing indicator, writable from confirmation to check-out + 7 days (the dispute period), read-only after. Now: schema, contract, service and seed data; the guest/host UI stays deferred. Agents are not participants and may not read it, even as dispute evidence (operator confirmed 2026-09-27). | Operator, 2026-09-27, when asked to keep or reverse the exclusion while requesting host-guest sample data | Operator conversation; [W13 spec § 8a](docs/superpowers/specs/2026-09-27-w13-messaging-service-design.md) |
| C40 | 2026-09-27 | Reverse the former Host Messages ticket-filing decision: Hosts cannot create tickets and may only respond in existing agent-managed ticket threads. Remove the Host `+ New Ticket` UI, callback/dialog, host candidate query, and Host filing authorization; keep Guest filing unchanged. | Operator: "Host should not be able to create new tickets, they can only respond to agents working on the ticket"; confirmed "Yes, reverse C39 and remove C39" | Host Messages design/plan revision; response-only Host Messages implementation |
| C41 | 2026-09-28 | Refine the existing Host Listings, New/Edit Listing, and Booking Calendar pages: tighten vertical spacing, use the audit multi-select checkbox treatment locally, and make the block-date workflow require a reason with full-height blocked-date history. No new spec or plan for this UI refinement. | Operator explicitly requested the focused refinement and said no spec/plan was needed | Host page-owned FXML/CSS/controllers and UI regression tests |
| C15 | 2026-09-23 | Execute W1 natively in the existing `w1` checkout rather than creating a separate worktree | Operator explicitly selected the current checkout for execution | Operator conversation, 2026-09-23 |
| C28 | 2026-09-26 | Proceed with Host Listings Dashboard & Copy Refinement without a new design spec or implementation plan | Operator explicitly requested the earlier spec be undone and then asked to carry on; implementation records this waiver | Operator conversation, 2026-09-26 |
| C29 | 2026-09-26 | Defer F7.2.2 structured host dispute response notes/evidence from W8 to W13 Messaging | Operator chose to defer the formal host response path to W13; W8 will not add ticket response fields or conflate the flow with chat | Operator conversation, 2026-09-26 |
| C30 | 2026-09-27 | Host booking approve/reject actions require confirmation modals matching the supplied mockups | Operator requested centered AgentModal-style dialogs with scrim, booking summary cards, explanatory notices, and modal-specific confirm/cancel actions | Operator conversation, 2026-09-27 |
| C31 | 2026-09-27 | W8 persists the optional host rejection message on the booking and exposes it to guest booking/trip views; broader F7.2.2 response notes/evidence remains deferred to W13 | Operator confirmed the proposed nullable `hostDecisionMessage` behavior | Operator conversation, 2026-09-27 |
| C39 | 2026-09-27 | W14 specified. Follows C31, with two refinements: `WalletService`/`TransactionService` are kept (C3 narrowed to storage, not removed) and escrow stays implicit (no escrow account). Reversal of the § Known Gaps platform-fee entry, C9 and the `wallets.balance` convention takes effect when the plan runs, on operator approval of the spec. Defaults in spec § 10 are open until confirmed. (Executed by W14, 2026-09-27 — all 12 tasks done and reviewed; awaiting operator acceptance.) | Operator asked to spec W14; agent flagged that it is medium-sized (about 18 main / 13 test files, one migration, seed rewrite), not small, and that the UI needs no change | Operator conversation; [W14 spec](docs/superpowers/specs/2026-09-27-w14-unified-ledger-design.md) |
| C40 | 2026-09-27 | W14 refinements: (a) the `wallets` table stays, so **no `users.balance`** (reverses that part of C31); only `wallet_transactions` is folded into `audit_log`. (b) The System user gets a real **`SYSTEM` role** by rebuilding `users` (new CHECK; migration V006, which `MigrationRunner` must run with foreign keys off), reversing C32's `AGENT` + `SUSPENDED`; the ledger fold is V007. Spec § 8 and § 10 updated. (Executed by W14, 2026-09-27.) | Operator: "for security and maybe less changes, lets keep the dedicated wallets table"; the SYSTEM role "should be a small addition". Rejected: `users.balance`; keeping the System user as a suspended agent | Operator conversation; [W14 spec](docs/superpowers/specs/2026-09-27-w14-unified-ledger-design.md) |
| C41 | 2026-09-27 | W14 spec § 10 defaults accepted as written: keep `WalletService` and `TransactionService`; balance stays a stored cache on `wallets`; no escrow account; legacy payouts get backfilled `PLATFORM_FEE` rows. Ledger fold is split into V006 (SYSTEM role), V007 (add and backfill) and V008 (drop table) so code can leave `wallet_transactions` before it is dropped. The Known Gaps platform-fee entry, C9 and C3-storage reversals are approved and take effect as the plan executes. (Executed by W14, 2026-09-27; the V007/V008 split happened exactly as anticipated here, see § Deviations.) | Operator: "Accept defaults. Write plan" | Operator conversation; [W14 spec](docs/superpowers/specs/2026-09-27-w14-unified-ledger-design.md), [W14 plan](docs/superpowers/plans/2026-09-27-w14-unified-ledger.md) |
| C42 | 2026-09-27 | W14 Task 8 review found $0/sub-cent listing rates crashed booking settlement (and app startup, via the startup completion sweep) once `LedgerWriter` rejects a zero-amount payout. Operator chose to **forbid degenerate listing rates** rather than support free/sub-cent stays end-to-end: `ListingServiceImpl`/the host form now require `baseNightlyRate` to be positive and have at most 2 decimal places (smallest legal total is $0.01, which always settles to a positive net). Reverses nothing recorded; this is new validation, not a reversal. (Executed by W14, 2026-09-27; verified accurate on final review — no further changes needed.) | Operator, asked to choose between forbidding $0 listings or building out free-stay support in escrow/settlement/disputes; chose to forbid | Operator conversation; [W14 plan](docs/superpowers/plans/2026-09-27-w14-unified-ledger.md) Task 8, commits cb9e1d0, a93f41e |
| C43 | 2026-09-27 | **Renumbered from a colliding `C32` on the `w9`/`main` side while merging `unified-ledger` into `main` (merging-across-branches: append-only, renumber, don't drop).** W9 host wallet statements show every wallet transaction type (`TOP_UP`, `WITHDRAWAL`, escrow rows, payouts, ticket remedies, and agent overrides); payout creation remains W8/W10-owned | Operator confirmed “Yes, all types” when choosing the W9 statement scope | Operator conversation, 2026-09-27 |
| C44 | 2026-09-27 | **Renumbered from a colliding `C33` on the `w9`/`main` side, same merge as C43.** W9 uses one common Guest/Host wallet page and action-dialog flow matching the supplied mockups; top-up presets populate the amount field and withdrawal's full-balance link populates the current balance | Operator approved the common-wallet direction and supplied the UI interaction requirements | Operator conversation, 2026-09-27; [W9 design](docs/superpowers/specs/2026-09-27-w9-host-wallet-management-design.md) |
| C45 | 2026-09-29 | W14 (Unified Ledger) accepted as `Done`. Also landed on `main` since the operator last reviewed: a follow-up UI-only change (PR #19, `audit-log-target`) replacing the Audit Log's `REF` column with `TARGET` (the row's direct entity only) and switching the timestamp format to `dd/MM/yyyy HH:mm`; superseded D17's last-four `REF` label note. Operator still owes a yes/no on the W14 Developer Guide checkpoint (AGENTS.md § 5) | Operator: "confirm w14. Operator reviewed" | Operator conversation, 2026-09-29; PR [#16](https://github.com/CS3227-2610-MP2-SnoozeShare/CS3227-2610-MP2/pull/16), PR [#19](https://github.com/CS3227-2610-MP2-SnoozeShare/CS3227-2610-MP2/pull/19) |
| C46 | 2026-09-29 | The W14 Developer Guide checkpoint (C45) is confirmed: `docs/DeveloperGuide.md` gains a new § 4.15 (Unified ledger / `LedgerWriter`), an updated ERD and sequence diagrams, and every wallet/ledger passage — `WalletLedgerWriter`→`LedgerWriter`, `feeAmount`→a separate `PLATFORM_FEE` row, the System user's role/status, the Dual-write and W14 glossary entries, the `REF`→`TARGET` audit column — corrected to match the shipped W14 code, not the pre-W14 design. Guide: `Awaiting confirmation` → `Pending` | Operator: "porpose guide sections. Also fix any Dual-write/W14 glossary entry and all W14 wallet/ledger wording issues" | Operator conversation, 2026-09-29; `docs/DeveloperGuide.md` §§ 3.1, 4.5, 4.7, 4.8, 4.12–4.15, 5, 6, 7 |

---

## 9. Deviations & Discoveries

### W14 deviations (2026-09-27) — full detail in the [W14 plan](docs/superpowers/plans/2026-09-27-w14-unified-ledger.md) and the [W14 spec § 3](docs/superpowers/specs/2026-09-27-w14-unified-ledger-design.md)

- **V007/V008 migration split (executed, Tasks 4 and 10; anticipated by C41, recorded in full in the plan and spec § 3):** the spec's original single "add + backfill + drop" V007 was split into `V007__unified_ledger.sql` (add `balanceAfter`, backfill legacy money and fee rows, create the System wallet) and `V008__drop_wallet_transactions.sql` (drop the table), so every caller (`WalletService`, `TransactionService`, `BookingService`, dispute settlement, account governance) could move onto `LedgerWriter`/`LedgerRepository` one at a time across Tasks 6-9 while `wallet_transactions` still existed as a fallback, then drop it once nothing referenced it.
- **C42 — settlement crash from degenerate listing rates (see § Decisions):** Task 8's review found that once `LedgerWriter` rejects a zero-amount write, a $0 or sub-cent-rounding listing rate crashed booking settlement — and, since settlement also runs from `AppContext`'s startup sweep, could crash app startup. Fixed by forbidding `baseNightlyRate <= 0` or more than 2 decimal places, not by building free-stay support through settlement/`EscrowPolicy`/disputes.
- **`BookingServiceImpl.calculateRefundAmount` rounding fixed (2026-09-27, commit `821d25d`):** the 50%-refund divide was unrounded and could post a 3-decimal-place wallet transaction on an odd-cent total — a pre-existing precision bug, unrelated to W14's own scope, spotted while reviewing the money-movement code and originally spun off as a background task. Operator asked for it directly; fixed with `.setScale(2, RoundingMode.HALF_UP)`, matching the pattern already used elsewhere in the class and in `SettlementCalculator`, with a regression test (`cancelWithin48hOnAnOddCentTotalRoundsTheRefundToTwoDecimalPlaces`) that fails without the fix.
- **`AuditService.recordWalletTransaction` removed (Task 10):** a default method that had become dead code once every caller moved onto `LedgerWriter` was deleted rather than left unused.
- Full verification (Task 12): `.\gradlew build` completed with 448 tests run, 2 failed — both pre-existing and unrelated to W14 (D21: `FileTicketTest.successfullyFilesTicketAndPublishesEvent`, `ShellLayoutTest.hostWalletPageUsesTheSamePageInsetAsListingsAndBookings`). The real-app GUI run (`.\gradlew run`, exercising top-up/withdraw/booking/Audit Log) was **not performed** — no GUI-driving tool was available to this session (same precedent as D14/D20); the conservation-invariant tests (Task 11) passed against the rebuilt mock DB on the first try, with no seed fix needed.

### W10 deviations (OPEN 2026-09-25) — full detail in the [W10 spec § 6](docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md)

- **D5** — W10 adds `DisputeSettlementService` instead of `TransactionService.applyTicketRemedy`/`manualOverride` (single-sided, cannot express C20). Reconcile with W3 at merge.
- **D6 (RESOLVED 2026-09-25)** — The mock DB held rows predating C17/C20. Per operator direction it is now corrected **in place** (`db/seed-mock-data.sql` edited, `db/snoozeshare-mock.db` rebuilt from `schema.sql` + seed, rebuild verified deterministic): open tickets 2 and 3 sit on `CONFIRMED` bookings 9 and 11 with escrow held (payout and reviews for them removed); resolved ticket 1 now pays the host `380 − 3%` on 8/28 with `COMPLETED` at resolution; ticket 4 / booking 13 is a 50/50 manual adjustment (guest `AGENT_OVERRIDE` +165, host `BOOKING_PAYOUT` 160.05) and `COMPLETED`; tickets 1–4 filed inside the 7-day window; wallet 5's pre-existing `balanceAfter` chain fixed. Ledger invariants (balance = Σ tx, running `balanceAfter`, FK check) verified. `FORCE_COMPLETED` no longer appears in the mock data (C22); `FORCE_CANCELLED` remains (booking 12, suspension cascade). Tests still run against a temp *copy* purely so mutating tests never write to the committed file — no normalisation step exists any more. A schema-parity test still guards migration-vs-`schema.sql` drift.
- **D7** — Design artifact differs from decisions: Force actions (C22), newest-first queue (F9.1.1 wins), "Adjust wallet" dropdown (C20), no Accept amount field (added).
- **D8** — `tickets.category` is label text, not an FK; renames don't propagate.
- **D9** — `BookingStateMachine` gains `AGENT` on `CONFIRMED → COMPLETED` (C23); W3 must be told at merge.
- **D10** — `MigrationRunner` adopts a pre-provisioned DB: the mock DB has tables but no `schema_history`, and the app could not open it otherwise. See the [W10 spec § 6](docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md).
- **D11 (SUPERSEDED by C24, 2026-09-25)** — The admin shell is sidebar-based and uses the current navy `theme.css`, not the canvas tabs / Fall Light palette. Accepted; alignment is a separate UI-design workstream. See the [W10 spec § 6](docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md).
- **D12** — Accepted minor findings from the W10 code review (not fixed; revisit if they matter): (a) `JdbcTicketRepository` orders by `createdAt` text, so sub-second ties can mis-order; (b) `MigrationRunner` adoption only checks for a `users` table; (c) settlement reads guest/host wallets once, so a self-booking (guest == host) would lose an update; (d) [resolved by W12: `AuditServiceImpl` now uses the record's `at` or the injected clock]; (e) settlement takes escrow from `booking.totalAmount()`, not the `ESCROW_HOLD` row; (f) `DisputeDetailController.load()` still calls `render()` outside the error handler.
- **Found during W10 execution (2026-09-25, all fixed, each in the ledger):** (a) mock seed timestamps lacked the trailing `Z`, so `JdbcCodecs.instant` rejected them; (b) mock seed IDs used non-hex prefixes, so `UUID.fromString` threw (rule now in § Orientation repo map); (c) the baseline build was red from 7 pre-existing W2 checkstyle violations.
- **Backlog edits made 2026-09-25 (operator approved):** `docs/ProductBacklog.md` — F9.2.1 struck as dropped; F9.2.2 reworded to full-escrow settlement; F9.1.1 gains chat threads; F7.3.1 gains the open-ticket guard; new epic F12 Messaging (W13); changelog entry added.
- **Cross-workstream requirements:** listed under § Workstreams → *Handoffs into W3*.
- **D13 (W8, resolved 2026-09-27)** — The committed reference/mock database predates V002 and
  some tests open it without running migrations. W8 maps a missing `hostDecisionMessage` as null
  and uses a legacy-column save shape when needed; migrated application databases still receive
  V002 normally. Full verification retains three pre-existing UI failures in `ShellLayoutTest`
  and `AgentModalTest`, unrelated to W8; all W8-focused tests and Checkstyle pass.

### W12 deviations (2026-09-26) — see the [W12 spec](docs/superpowers/specs/2026-09-26-w12-platform-audit-trail-design.md) and [plan](docs/superpowers/plans/2026-09-26-w12-platform-audit-trail.md)

- **D13.5 — Wallet rows are dual-written until W14.** Every wallet movement writes `wallet_transactions` and an `audit_log` money row in one transaction (C31). Deliberate; W14 removes the duplication.
- **D14 — RESOLVED 2026-09-26 (operator verified the real-app run).** Originally: real-app manual run not done. Plan Task 10 Step 3 (launch the app on a copy of the mock DB, log in as `amy.tanaka@snoozeshare.test`, exercise Audit Log) was not performed. FX smoke and snapshot tests cover the flows; this stays as operator acceptance. Open UI polish is listed in § Known Gaps.
- **D15 — Mock DB now ships migrated.** `MockDbFixture` no longer migrates its copy; the committed `db/snoozeshare-mock.db` already has `schema_history` v1+v2 and the System user, `CommittedMockDbTest` guards it, and `db/schema.sql` now contains `CREATE TABLE IF NOT EXISTS schema_history`. Rebuild the `.db` from schema + seed after seed edits, as before.
- **D16 — Seed `availability_blocks` ids remapped** to valid hex prefixes (`60…`/`10…`/`20…`) because the old seed did not rebuild cleanly.
- **D17 — API changes from the plan:** `AuditService.query(...)` replaced by `search(...)`; `CATEGORY_DELETED` renamed `TICKET_CATEGORY_DELETED`. W11 emits account-governance rows (C32); the W14 unified ledger is unchanged by W12. W12 spec corrections (`actorName` snapshot, 7 `ALTER TABLE` columns, ≥4-char contains-match search, last-four-character REF labels) were applied to the spec.

- **D18 — C33 styling limits (accepted).** The JavaFX calendar cannot be restructured by CSS: the popup keeps its two separate month/year spinners (the board has one title with two arrows), weekday names stay mixed-case (no text-transform in JavaFX CSS), and no unavailable-day state exists. On the categories screen the header columns sit about 5px left of the row columns while the scroll bar is showing. Revisit only with a custom `DatePickerSkin`.

### D20 — W11 deviations and accepted limitations (2026-09-26)

- The Accounts list hides the System user. `BookingServiceImpl` gained a `UserRepository` constructor parameter and a suspended-guest guard. `MigrationRunner` now handles V003.
- **Refund without an escrow check (accepted):** the suspension cascade credits the booking's `totalAmount` without checking that an `ESCROW_HOLD` row exists, consistent with `BookingServiceImpl`.
- **Test fixture:** `AccountFixture.START_BALANCE` was raised from 1000 to 5000 so the cascade test can hold several escrows.
- **UI polish (fixed after operator acceptance run, 2026-09-27):** status pill vertically centred, Reactivate and Suspend buttons share one size, search field uses the white input fill.
- **C36 boundary:** a stay whose check-in is today counts as started, so its CONFIRMED booking is not cancelled.
- **Operator acceptance (done 2026-09-27, after polish fixes):** the real-app GUI run (plan Task 14 Step 2) was not performed by the agent (no GUI driving available). Instead `AccountGovernanceService` was driven headlessly against a copy of the mock DB: suspending Noah Kim force-cancelled 2 PENDING bookings (`FORCE_CANCELLED`) with 2 `ESCROW_REFUND` rows, wrote `ACCOUNT_SUSPENDED`, and reactivating wrote `ACCOUNT_REACTIVATED` and flipped the status back. FX smoke/snapshot tests cover the screens. Operator should run the app on `build/acceptance.db` to accept.

### D2 — Two schema gaps found while grounding the UI mockups against `db/schema.sql` (RESOLVED 2026-09-26 by W12: point 1 by C32, point 2 by C28)

**Resolution (2026-09-26):** point 2 — audit status/reason/amount are now real columns (`walletAdjustment`, `reason`, ...; C28), so no JSON projection layer exists. Point 1 — the suspension reason is stored as `reason` on the `ACCOUNT_SUSPENDED` audit row (C32), no `users.suspensionReason` column; W11 emits that row. Original notes below kept for history.

### D22 — W13 notes (2026-09-27)

The `MessageService.thread` signature gained a viewer (`viewerId`, `viewerRole`) and the in-memory implementation was deleted, so any other branch calling the old `thread(ticketId, channel)` must update. Wrong-role and non-party calls throw `IllegalArgumentException`, unlike W10's in-memory service which trusted the caller. Schema parity, `DatabaseBootstrapTest`, `MigrationRunnerReferenceDbTest` and `CommittedMockDbTest` now expect V004. The baseline failures observed during the original W13 service slice were tracked there; the current verification result is recorded in D23.

### D23 — Host Messages verification baseline (2026-09-27)

The Host Messages implementation passes its focused tests, Checkstyle, and packaging build. The full
suite completes 442 tests with one pre-existing failure, `AgentModalTest.escapeAndWindowCloseAlsoRemoveTheScrim`
at line 100; it is unrelated to the messaging changes. Java 25 native-access warnings from SQLite and
JavaFX remain environmental.

### D24 — Host Messages list layout refinement (2026-09-27)

The Host Messages inbox uses a fixed 380px sidebar. Conversation titles and subtitles use JavaFX
ellipsis overrun, while the conversation copy expands to keep any OPEN/RESOLVED badge right-aligned.
Focused layout/controller tests and Checkstyle pass. The full suite remains at 442 passing and one
unrelated pre-existing `AgentModalTest.escapeAndWindowCloseAlsoRemoveTheScrim` failure; the app
launched against `build/acceptance-host-messages-olivia.db`, and the Olivia host authentication path
passed in `HostMessagesControllerTest`.

### D25 — Host Messages horizontal overflow refinement (2026-09-27)

The Host Messages ListView constrains each conversation row to the fixed sidebar width, allows
title/subtitle text to shrink and ellipsize, and removes the horizontal scrollbar from the list
skin so OPEN/RESOLVED badges remain visible at the right edge. Focused UI tests and Checkstyle
pass; the app relaunches cleanly against a fresh disposable mock DB copy.

### D26 — Host ticket creation reversed (2026-09-27)

The former C39 Host ticket-filing decision was removed and replaced by C40. Host Messages no longer
shows `+ New Ticket`, exposes a ticket-filing callback or dialog, or offers a host booking-candidate
query. `TicketService.fileTicket` rejects `Role.HOST`; Guest ticket filing remains unchanged. The
focused Host Messages, controller, and ticket-service tests pass.

### D27 — Wallet Related To mapping corrected (2026-09-27)

`WalletTransactionFormatter.relatedLabel` now maps references by transaction type: `TOP_UP` and
`WITHDRAWAL` display `—`; escrow and payout rows display the booking; `TICKET_REMEDY` displays the
ticket; and `AGENT_OVERRIDE` displays the agent. This prevents user-initiated withdrawals from
being incorrectly labeled as Agent references. Focused formatter tests and Checkstyle pass; the
full suite retains the unrelated `AgentModalTest.escapeAndWindowCloseAlsoRemoveTheScrim` failure.

### D28 — Host listing breadcrumb navigation (2026-09-27)

Host listing detail, create, edit, and booking calendar pages now use the agent-shell breadcrumb
pattern. Detail shows `Listings > <listing>`; create shows `Listings > Create`; edit shows
`Listings > <listing> > Edit`; and calendar shows `Listings > <listing> > Booking Calendar`.
Ancestor crumbs are clickable, while the redundant form title and calendar title/description/back
button were removed. Focused navigation tests and Checkstyle pass; the full suite retains the
unrelated `AgentModalTest.escapeAndWindowCloseAlsoRemoveTheScrim` failure.

### D29 — Host breadcrumb styling ownership (2026-09-27)

Host listing/detail/form/calendar breadcrumbs no longer use `agent-crumb-*` classes from
`agent-theme.css`. They use Host-owned `host-crumb-*` classes defined in
`src/main/resources/com/snoozeshare/ui/host/host-navigation.css`, loaded by the Host shell.
Focused navigation tests and Checkstyle pass; the full suite retains the unrelated AgentModal
failure.

### D30 — Host Edit breadcrumb separator (2026-09-27)

The listing form now conditionally manages a second breadcrumb separator: Create renders
`Listings > Create`, while Edit renders `Listings > <listing> > Edit`. The separator is hidden and
unmanaged for Create so the two modes retain the intended breadcrumb shape. Focused navigation tests
and Checkstyle pass; the full suite retains the unrelated AgentModal failure.

### D31 — Host Messages typography hierarchy (2026-09-27)

The Host Messages side panel now uses smaller Host-owned typography: 18px heading, 14px row title,
and 11px row description. The selected conversation header remains larger at 20px title and 14px
description. These overrides live in `host-navigation.css`, which is loaded directly by the Host
Messages page. Focused tests and Checkstyle pass; the full suite retains the unrelated AgentModal
failure.

### D32 — Host Messages row hierarchy (2026-09-27)

The Host Messages side-panel row now places the OPEN/RESOLVED badge on the same line as the
truncated title, with the truncated description below. Host-owned typography is reduced to a 16px
sidebar heading, 14px row title, 13px row description, 18px conversation title, and 14px
conversation description; the row text remains larger than the 12px conversation bubble body.
Focused Host Messages tests and Checkstyle pass. The full suite has 444 tests with one unrelated
pre-existing `AgentModalTest.escapeAndWindowCloseAlsoRemoveTheScrim` failure at line 100.

### D33 — Host Messages badge clipping correction (2026-09-27)

The badge was present and styled but was laid out beyond the visible ListCell edge: an expanding title
HBox claimed the line before the badge was reserved, and the row preferred width omitted the cell's
18px right padding. The title line now uses a BorderPane with a dedicated right slot for the badge,
and the row width subtracts both horizontal paddings (42px). Focused tests and Checkstyle pass; the
full suite remains 443 passing with the unrelated AgentModal failure.

### D34 — Consolidated Host stylesheet (2026-09-27)

Host shell, Host Messages, and the standalone Host booking-decision dialog no longer reference
`agent-theme.css`. A Host-owned `host-theme.css` preserves the Fall Light tokens and Host/shared
component rules under `.host-root`; the Host shell root now uses `host-root`, while Agent UI remains
on `agent-theme.css`. Host-specific page stylesheets remain separate where already established.
Focused Host ownership tests and Checkstyle pass. The full suite remains 443 passing with the
unrelated `AgentModalTest.escapeAndWindowCloseAlsoRemoveTheScrim` failure.

### D35 — Host Messages typography specificity correction (2026-09-27)

The first Host-theme migration preserved the old 22px sidebar/header and 16px row-title rules under
the more-specific `.host-root` selectors, so the smaller rules in `host-navigation.css` could not
override them. The intended 16px sidebar, 14px row title, 13px row description, 18px conversation
title, and 14px conversation description now live directly in `host-theme.css`; duplicate message
typography rules were removed from `host-navigation.css`. Focused tests and Checkstyle pass; the
full suite remains 443 passing with the unrelated AgentModal failure.

### D36 — Reverse D28: restore listing form titles (2026-09-27)

Per operator confirmation, D28 is reversed. Host listing Create and Edit forms retain their
breadcrumb navigation and now display `Create listing` or `Edit listing` as a page title directly
under the breadcrumb. Detail and calendar pages remain breadcrumb-only as previously decided.
Focused navigation tests and Checkstyle pass; the full suite remains 443 passing with the unrelated
AgentModal failure.

### D37 — Compact listing form actions (2026-09-27)

Create and Edit listing form actions are now left-aligned. The form's outer spacing, card spacing,
grid gaps, card/input padding, and action-button height were reduced so the two-row form is more
likely to fit within the Host shell height while retaining the ScrollPane as a safeguard. Focused
listing/navigation tests and Checkstyle pass; the full suite remains 443 passing with the unrelated
AgentModal failure.

### D21 — Merge of `origin/main` into the W11 branch (2026-09-27)

Resolved by keeping both sides. `BookingServiceImpl` already had a `UserRepository` from W8, so W11's extra constructor parameter was dropped and only the suspended-guest guard remains. `AppContext` builds `AccountGovernanceService` after the W8 wiring. The W8 `hostDecisionMessage` column and W11's V003 both live in `MigrationRunner`; `SchemaParityTest` applies both. The guide's audit trail section is § 4.13, so W11's account-governance section is § 4.14. W8 auto-completion (`completeEligibleBookings`, run at startup) now settles a suspended guest's in-progress stay. **Pre-existing red on `main`, not from W11:** `FileTicketTest.successfullyFilesTicketAndPublishesEvent` and `ShellLayoutTest.hostWalletPageUsesTheSamePageInsetAsListingsAndBookings` fail on `origin/main` too; I fixed the checkstyle violations `main` carried (`ReviewServiceImpl` import order, `FileTicketTest` line wraps). **Re-check after the merge (2026-09-27):** the app launched on a copy of the mock DB with no startup errors and its window opened; a headless drive of `AccountGovernanceService` on that copy suspended Noah Kim (1 `ACCOUNT_SUSPENDED`, 3 `BOOKING_FORCE_CANCELLED`, refund rows) and reactivated him. Nobody clicked through the merged Accounts screen; the UI tests cover it. Note W8 also numbered a deviation D13, colliding with W12's D13 (resolved by renaming W12's to D13.5).

### D19 — `UserService.suspend` replaced by `AccountGovernanceService` (W11, RESOLVED 2026-09-26: stub removed)

The W1 stub `UserService.suspend(userId, agentId)` only flips the status. W11 removes it in favour of `AccountGovernanceService.suspend/reactivate` (reason, cascade, audit). D2 item 1 (reason column) is resolved by C34(d); D2 item 2 stays with W12. See the [W11 spec § 6](docs/superpowers/specs/2026-09-26-w11-account-governance-design.md).


While extending the Design canvas (C11) to show every field the operator asked for, checking
each field against `db/schema.sql` / the architecture proposal §4 turned up two things the
mockups need that the current schema doesn't have a column for:

1. **Suspended-account reason** — the Agent Accounts screen shows a reason under a suspended
   user's status pill (operator-requested), but `users` has only `accountStatus`
   (`ACTIVE`/`SUSPENDED`), no `suspensionReason` column. The mockup shows it anyway as a design
   requirement; whoever builds W11 needs to either add a `users.suspensionReason` column or
   source it from an `audit_log` snapshot (see #2).
2. **Audit log status/reason/amount aren't literal columns** — `audit_log` is generic
   (`actorUserId`, `actionType`, `entityType`, `entityId`, `beforeState`, `afterState`,
   `timestamp`); it has no `status`/`reason`/`amount` fields. The operator asked the Audit Log
   screen to show those, so the mockup treats them as **derived from the entity's
   `beforeState`/`afterState` JSON snapshot** (e.g. a `wallet_transactions` snapshot has
   `amount`; a `tickets` snapshot has `status`+`resolutionReason`) rather than raw columns.
   Whoever builds W9/W12 needs a small projection layer over those snapshots, not a schema
   change.

Not resolved — flagging so W9/W11/W12 don't get built against the mockup's flat columns without
knowing they're derived, and so the missing `users` column is a deliberate open question, not a
missed field.

### D1 — Architecture proposal said SQLite, repo briefly ran H2 — now resolved to SQLite (RESOLVED 2026-09-22)

[`docs/SnoozeShare-Architecture-Proposal.md` §4](docs/SnoozeShare-Architecture-Proposal.md)
opens with "**Embedded relational DB — SQLite via plain JDBC**", but its own very next paragraph
justified the choice using H2-specific facts ("H2 needs zero external services, runs embedded,
has a browser console for debugging"). At bootstrap time the repo had already resolved this
in code the other way: `build.gradle` depended on `com.h2database:h2:2.3.232` and `.gitignore`
excluded H2's file extensions (`*.mv.db`/`*.trace.db`). This was raised as Q2 rather than
silently corrected either direction.

**Resolution:** the operator confirmed SQLite was intended (2026-09-22). `build.gradle` now
depends on `org.xerial:sqlite-jdbc:3.46.1.3` instead of H2, and `.gitignore` now excludes
`*.db`/`*.sqlite3`/`*.db-journal` instead of the H2 extensions. The architecture proposal's
heading text was correct all along; its rationale paragraph is the stale part, not yet edited
(the proposal doc itself is left alone per AGENTS.md — this file is where the correction lives).
No repository/DAO code existed yet, so this was a pure dependency swap, not a migration. Recorded
as C5 in § Architecture 4.5.

### D2 — Feature-specific persistence and transaction policy remain with owning workstreams

W1 supplies every aggregate repository contract and the core JDBC adapters needed by shared auth,
wallet, audit, and integration wiring. Property/availability/booking/ticket/review JDBC behavior
and escrow/remedy transaction policy depend on feature-specific requirements, so W1 does not
invent those implementations. This preserves the approved C13 boundary: W1 owns shared logic;
feature workstreams own feature behavior.

### D4 — SQLite REAL columns lose BigDecimal scale (W2, RESOLVED 2026-09-24)

SQLite stores `baseNightlyRate` as a `REAL` column. When a `BigDecimal("150.00")` round-trips
through `getString()` on an SQLite `REAL`, it comes back as `"150.0"` — same numeric value but
different scale. `BigDecimal.equals` checks scale, so `new BigDecimal("150.00").equals(new
BigDecimal("150.0"))` is false. All monetary assertions in W2 tests use `compareTo` instead of
`equals`. UI display calls `setScale(2, HALF_UP)` before rendering. This is a permanent SQLite
trait, not a one-off fix — any future test that asserts on a monetary BigDecimal read from SQLite
must use `compareTo`, not `equals`.

### D3 — SQLite Java 25 native-access warning is environmental

The full suite passes, but SQLite emits Java 25's warning that native access should be enabled for
the JDBC loader. It is non-fatal in the current runtime and does not change W1 behavior.

### D5 — Manual host blocks use inclusive end dates (W7, RESOLVED 2026-09-26)

The approved W7 spec originally described the shared half-open date convention, but the operator
later requested that manual host blocks include the entered end date and allow a single-date
block. The service preserves the repository's half-open storage/overlap representation by
normalizing a manual block's persisted end to the day after the entered end; the calendar and
override list present the operator-entered inclusive end date. Booking date semantics remain
unchanged.

---

## 10. Record

The Done ledger lives in **[`docs/project-state/done-ledger.md`](docs/project-state/done-ledger.md)**
— every change, big or small, newest first.

- **Latest entry:** 2026-09-28
- **Entries:** 139 (4 backfilled coarsely from git history)

Deviations stay in § Deviations above: those are read every session.
