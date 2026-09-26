# Project State — SnoozeShare

**Living document — the source of truth for this project.** Read it in full before doing
anything, then run `git log --oneline -20` to confirm it still matches reality. Update it at
every boundary, not at the end of the session.

- **Phase:** W7 Host Calendar & Date Overrides — complete
- **Stack:** Java 25, JavaFX 25 (javafx.controls, javafx.fxml), Gradle (application + shadow + checkstyle plugins), SQLite (embedded, file-based, `org.xerial:sqlite-jdbc`) via plain JDBC, JUnit 5 + TestFX for tests
- **Branch:** `w8`
- **Method:** Native inline execution with TDD-first vertical slices, fresh-context whole-branch review at end
- **Last updated:** 2026-09-27 by Codex — W8 optional host rejection message approved for implementation
- **Last verified against repo:** 2026-09-26
- **Developer guide:** `docs/DeveloperGuide.md` seeded and extended with W10 on 2026-09-26 (first write + W10 checkpoint, operator-approved); W1/W2 are `Awaiting confirmation` and not yet documented.

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
| `docs/DeveloperGuide.md` | Handover brief for developers — currently an empty placeholder |
| `docs/UserGuide.md`, `docs/Reflections.md` | End-user guide; reflections on the agentic workflow (not read in full for this bootstrap — out of scope per bootstrapping bounds) |
| `logs/` | Per-agent-session interaction logs named `YYYY-MM-DD_HH-mm-ss_<branch>.md` in SGT |
| `src/main/java/com/snoozeshare/` | Application source — package skeleton only, see § Architecture |
| `config/checkstyle/` | Checkstyle rules enforced on build |
| `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db` | Shared team-reference SQLite DB — draft SQL + the built `.db` file, committed so everyone queries the same data. **Rebuild the `.db` from the two SQL files after any seed edit** (`sqlite3 x.db < schema.sql; sqlite3 x.db < seed-mock-data.sql`); corrected 2026-09-25 to follow C17/C20 (see D6). **Seed timestamps are UTC ISO-8601 ending in `Z`** (as the app writes them via `Instant.toString()`; `JdbcCodecs.instant` rejects zone-less values) — keep that format in any seed edit. **Seed IDs must be valid hex UUIDs** (mock prefixes: users `a`/`b`/`c`, tickets `d`, listings `1`, bookings `2`, wallets `3`, transactions `4`, availability `5`, audit `6`, categories `7`, reviews `8`); non-hex prefixes make `UUID.fromString` throw. Dev/reference artifact, not wired into app startup (see § 4.5) |

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
| S7 | 2026-09-25 | Codex | w7 | W7 scope assessment | Paused | `w7` created from `w6`; booking display maps to W8/F7.1, while W7 remains calendar/date overrides | 2026-09-25 |
| S8 | 2026-09-26 | Codex | w7 | W7 | Paused | W7 complete: host calendar, inclusive manual blocks, removal, validation, and layout delivered; full tests/build pass | 2026-09-26 |
| S9 | 2026-09-26 | Codex | w8 | Host portal UI refinement | Paused | Completed wallet/listing layout, copy, metrics, action sizing, and status-control spacing; focused tests, XML validation, Checkstyle, and diff checks pass; full suite retains two unrelated UI failures | 2026-09-26 |
| S14 | 2026-09-26 | Codex | w8 | W8 — Host Request Queue, Earnings & Disputes | Active | Tasks 1–5 complete; executing final verification and state reconciliation | 2026-09-27 |

Status vocabulary, used verbatim: `Active` · `Paused` · `Blocked — needs human` (name the
question ID, same as a workstream row).

---

## 3. Workstreams

W1 has completed its shared-foundation implementation and is awaiting operator confirmation. The rows below are the backlog's epics (§2 of the architecture proposal is
their shared design; `docs/ProductBacklog.md` is their shared spec source) reframed as
workstreams so future sessions have somewhere to record status. Each started workstream must link
its spec and plan before feature implementation, per AGENTS.md § 3.

| ID | Workstream | Status | Spec | Plan | Progress | Guide |
|---|---|---|---|---|---|---|
| W1 | F0 — Auth, Registration & Wallet Provisioning | Done | [shared-foundation design](docs/superpowers/specs/2026-09-23-w1-shared-foundation-design.md) | [shared-foundation plan](docs/superpowers/plans/2026-09-23-w1-shared-foundation.md) | W1 implementation and verification complete; guide confirmation pending | Awaiting confirmation |
| W2 | F1 — Listing Search & Property Discovery | Done | [listing-search design](docs/superpowers/specs/2026-09-24-w2-listing-search-design.md) | [listing-search plan](docs/superpowers/plans/2026-09-24-w2-listing-search.md) | Implementation complete: search/filter, detail modal, price breakdown | Awaiting confirmation |
| W3 | F2 — Booking Execution & Trip Hub (incl. escrow) | Building | [booking-execution design](docs/superpowers/specs/2026-09-24-w3-booking-execution-design.md) | [booking-execution plan](docs/superpowers/plans/2026-09-24-w3-booking-execution.md) | All 9 tasks complete: TransactionServiceImpl, BookingServiceImpl (submit/cancel/decide), AppContext wiring, Trip Hub UI, Book Now button | — |
| W4 | F3 — Guest Feedback, Disputes & Reviews | Not started | — | — | Backlog only: §3 | — |
| W5 | F4 — Guest Wallet Management (top-up/withdraw) | Done | [wallet-management design](docs/superpowers/specs/2026-09-25-w5-wallet-management-design.md) | [wallet-management plan](docs/superpowers/plans/2026-09-25-w5-wallet-management.md) | All 6 tasks complete: dashboard, modal, navigation, CSS, sidebar refresh | Awaiting confirmation |
| W6 | F5 — Host Listing Management & Publishing | Done | [listing-management design](docs/superpowers/specs/2026-09-25-w6-listing-management-design.md) | [listing-management plan](docs/superpowers/plans/2026-09-25-w6-listing-management.md) | Implementation complete: listing CRUD/status/detail flows, host wallet/navigation refinements, wallet refresh fix, and clean build verification | Awaiting confirmation |
| W7 | F6 — Host Calendar & Date Overrides | Done | [host-calendar design](docs/superpowers/specs/2026-09-26-w7-host-calendar-design.md) | [host-calendar plan](docs/superpowers/plans/2026-09-26-w7-host-calendar.md) | Complete: listing calendar, month navigation, colors, all-month overrides, inclusive/single-date blocking, removal, validation, and layout; full tests/build pass | Awaiting confirmation |
| W8 | F7 — Host Request Queue, Earnings & Disputes | In review | [host requests/earnings design](docs/superpowers/specs/2026-09-26-w8-host-requests-earnings-design.md) | [host requests/earnings plan](docs/superpowers/plans/2026-09-26-w8-host-requests-earnings.md) | Tasks 1–5 complete; final whole-branch verification in progress; broader F7.2.2 deferred to W13 | Awaiting confirmation |
| W9 | F8 — Host Wallet Management | Not started | — | — | Backlog only: §4 | — |
| W10 | F9 — Agent Dispute Resolution (F9.2.1 force actions dropped, C22) | Done | [W10 design](docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md) | [W10 plan](docs/superpowers/plans/2026-09-25-w10-agent-dispute-resolution.md) | All 22 tasks done, operator confirmed 2026-09-26; W3 merge handoffs open | Documented 2026-09-26 |
| W11 | F10 — Agent Account Governance | Not started | — | — | Backlog only: §5 | — |
| W12 | F11 — Platform Audit Trail & Analytics | Not started | — | — | Backlog only: §5 | — |
| W13 | Messaging (ticket chat threads; general `MessageService`) — no backlog epic yet, raised by W10 (C21) | Not started | — | — | Not spec'd; W10 depends on its interface only | — |
| W14 | Host Listings Dashboard & Copy Refinement | Done | — | — | Live listing metrics, listing-card redesign, Requests/wallet copy, and layout updates complete; operator explicitly waived new spec/plan | — |

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
Host navigation starts on Listings and includes a non-interactive Messages placeholder; Guest
navigation labels the existing search page as Search and loads it immediately after login.
Host Wallet now replaces the shell center directly, matching Guest Wallet’s full available wallet
area and modal-overlay behavior.
Booking escrow publishes `WalletTransactionRecordedEvent` after its transaction commits, keeping
wallet headers and wallet dashboards synchronized with the persisted balance.
Host Listings, Bookings, listing details, and listing forms now also replace the shell center
directly; their page titles and descriptions are defined inside each page view.
The Host listing create/edit form uses a responsive two-column, four-card layout with
human-readable property/status dropdowns, text-field capacity inputs, two-column amenity tiles,
and create/edit-specific action labels.
The Host Listings page uses a `My listings` heading, wide metric/action cards, and `Requests`
navigation; Host Wallet actions use equal-width `Top up` and `Withdraw` buttons.
Host shell FXML is well-formed and loads successfully through `SceneRouter` after authentication.
Per the proposal,
each role gets its own FXML+Controller tree under `ui.<role>`, and `ui.common` holds shared
pieces (`WalletPanelController`, `NavShell`, formatting/validation helpers, shared components)
constructed once per session and embedded into whichever role shell is active. Controllers are
meant to depend only on `service.*` interfaces, never `repository.*` or `infra.db.*` directly.

**W10 Agent screens (built):** `ui.admin` has the canvas-style agent shell (top bar + tab strip: Disputes / Accounts / Audit Log / Categories; Accounts and Audit Log are placeholders) in the "Fall Light" palette (`agent-theme.css`, agent scene only; C24). Screens: `DisputeQueueController` (oldest-first table, All/Unassigned/Mine chips, width-fitted status dropdown, unassigned badge), `DisputeDetailController` (summary card, resizable guest/host chat boxes, one persisted internal-notes field with Save, Assign/Unassign, Accept / Reject / Manual actions; C25), `ResolutionDialogController` and `CategoryDialogController` (modal cards built on `AgentModal` with a 50% grey scrim; live refund/payout preview, reason required; category Add/Edit/Delete; C26) and `CategoryAdminController`. To try it on a copy of the mock DB, set env `SNOOZESHARE_DB_URL` (e.g. `jdbc:sqlite:build/acceptance.db`); `Main` passes it to `AppContext.create(jdbcUrl)`. `AdminUiSmokeTest` and the snapshot tests exercise the screens on the FX toolkit and write `build/ui-snapshots/*.png`.

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
queries) and `TransactionServiceImpl` (holdEscrow, refundEscrow, historyFor — with
settleBookingCompletion/applyTicketRemedy/manualOverride stubbed for W8/W10). `UserService`
gained a `findById(UUID)` method. The proposal specifies nine service interfaces
(`ListingService`, `AvailabilityService`, `BookingService`, `TicketService`, `WalletService`,
`TransactionService`, `UserService`, `ReviewService`, `AuditService`) with full method
signatures — see [architecture proposal §3.1](docs/SnoozeShare-Architecture-Proposal.md) for the
exact contracts, including which backlog item (F-number) each method backs.
W6 extends `ListingServiceImpl` with host-authorized listing creation, detail updates, and
status changes, with validation and audit records.
`ListingMetricsService` supplies host dashboard booking counts and average review ratings with
zero-value fallbacks for listings without activity.

**W10 adds:** `TicketServiceImpl` (queue, assign, notes, resolve, category admin),
`DisputeSettlementServiceImpl` (atomic full-escrow two-sided settlement, C20),
`DisputeQueryServiceImpl` (queue/detail read models for the Agent UI) and a temporary
`InMemoryMessageService` (session-only chat; W13 replaces it).

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C3 | unknown | Two separate financial interfaces: `WalletService` (dumb primitive — balance, top-up, withdraw) vs. `TransactionService` (business rules — escrow, 3% fee, refund policy, dispute remedies). UI may call `WalletService` directly only for top-up/withdrawal; `BookingService`/`TicketService` are the only callers of `TransactionService` | Keeps "how do bookings pay out" and "how do I add money to my account" independently testable; centralizes every balance change behind one choke point so wallets can't drift from booking/ticket state | [architecture proposal §3](docs/SnoozeShare-Architecture-Proposal.md) |
| C4 | unknown | Use Java 25 records directly as the domain model passed to JavaFX view models; no separate DTO layer | Over-engineering for MVP scale | [architecture proposal §3](docs/SnoozeShare-Architecture-Proposal.md) |
| C7 | 2026-09-22 | **No guest-side service fee.** `bookings.totalAmount = nightlyRateSnapshot × nights`, full stop. The only platform fee anywhere is the 3% deducted from a host's `BOOKING_PAYOUT` (already specified). Corrects an earlier mock-data draft that had invented a 5% guest fee to fill the doc's undefined `serviceFeeAmount` column — that column is now removed | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C8 | 2026-09-22 | `REJECTED` and `CANCELLED_BY_HOST` bookings both trigger a 100% `ESCROW_REFUND`, same as a guest cancelling >48h out. (Note: `BookingService` in the proposal has no explicit host-initiated-cancel method distinct from `decide(...,approve=false)` — flagged as a spec gap for whoever builds F6.1.2/host cancellation, not resolved by this decision.) | Operator confirmed "good assumption" while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C9 | 2026-09-22 | *(Superseded for agent ticket resolutions by C20, 2026-09-25 — those are now two-sided: guest refund row + host payout row.)* `TICKET_REMEDY` and `AGENT_OVERRIDE` wallet rows are single-sided — only the wallet actually credited/debited gets a row, no matching entry on the other side. There is no double-entry anywhere in `wallet_transactions`, extending the doc's existing "fees aren't a real platform-wallet transfer" note to these two types as well | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |
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
`User`, `Wallet`, `WalletTransaction`, and `AuditLog` JDBC adapters. W2 adds
`JdbcPropertyRepository` (dynamic WHERE clause building for search, UPSERT for save),
`JdbcAvailabilityBlockRepository` (overlap detection: `startDate < ? AND endDate > ?`), and
`JdbcBookingRepository` (overlap detection filtering PENDING+CONFIRMED only). Shared `RowMappers`
centralizes result-set-to-record mapping with graceful unknown-amenity handling. Remaining
feature-specific aggregate adapters are owned by the workstreams that implement those features. The proposal specifies one
repository interface per aggregate (`UserRepository`,
`PropertyRepository`, `BookingRepository`, `AvailabilityBlockRepository`, `TicketRepository`,
`WalletRepository`, `WalletTransactionRepository`, `ReviewRepository`, `AuditLogRepository`),
each returning/consuming domain records — only `repository.jdbc.*` may import `java.sql.*`.
Schema is specified table-by-table in
[architecture proposal §4](docs/SnoozeShare-Architecture-Proposal.md) (users, properties,
availability_blocks, bookings, wallets, wallet_transactions — an append-only ledger, tickets,
ticket_categories, reviews, audit_log). No migrations exist yet (`infra.db.migration` is still
unbuilt). A hand-written (non-Flyway) copy of this schema plus a full mock dataset has been built
into a **shared, committed reference DB** — `db/schema.sql` / `db/seed-mock-data.sql` /
`db/snoozeshare-mock.db` — for the team to query together; it is a dev/reference artifact only,
not loaded by the application at startup. See § Record.

**W10 adds:** `JdbcTicketRepository` and `JdbcTicketCategoryRepository`, and `MigrationRunner` now
adopts a pre-provisioned database (one with tables but no `schema_history`, such as the mock DB) by
recording the baseline (D10).

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

### 4.7 Session

**Now:** `SessionContext` and `MockSessionContext` provide optional current user, null-safe
unauthenticated role, login, and logout. Specified
as `Optional<User> currentUser()`, `Role currentRole()`, `loginAs(User)` (mocked — sets state, no
credential check), `logout()`. Every service method that needs an actor takes the acting user's
id explicitly rather than reaching into a global, so services stay unit-testable without a live
session.

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
- Every mutating service method has exactly one `AuditService.record(...)` call site (or,
  alternatively, `AuditService` subscribes to domain events — pick one convention project-wide
  once this is actually built; not yet decided in code).
- `wallets.balance` is a denormalized cache of "sum of this wallet's transactions" — it is only
  ever written in the same DB transaction as the triggering `wallet_transactions` row insert,
  never independently.
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
- **Platform fees are informational only** (`feeAmount` column on `BOOKING_PAYOUT` rows) — no
  system-owned platform `Wallet`, no true double-entry transfer. Revisit only if the platform
  ever needs its own reportable balance.
- **Auth is fully mocked** — no real credential validation anywhere; role separation is enforced
  in service/domain code, not by real authentication. This is a stated project constraint, not
  an oversight.

**Explicitly out of scope:** microservices, message brokers, horizontal scaling, JPMS module
boundaries, JPA/Hibernate, a separate DTO layer distinct from domain records.

---

## 7. Needs a Human

| ID | What is needed | What it blocks | Raised |
|---|---|---|---|
| — | No outstanding questions | — | — |

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
| C15 | 2026-09-23 | Execute W1 natively in the existing `w1` checkout rather than creating a separate worktree | Operator explicitly selected the current checkout for execution | Operator conversation, 2026-09-23 |
| C28 | 2026-09-26 | Proceed with Host Listings Dashboard & Copy Refinement without a new design spec or implementation plan | Operator explicitly requested the earlier spec be undone and then asked to carry on; implementation records this waiver | Operator conversation, 2026-09-26 |
| C29 | 2026-09-26 | Defer F7.2.2 structured host dispute response notes/evidence from W8 to W13 Messaging | Operator chose to defer the formal host response path to W13; W8 will not add ticket response fields or conflate the flow with chat | Operator conversation, 2026-09-26 |
| C30 | 2026-09-27 | Host booking approve/reject actions require confirmation modals matching the supplied mockups | Operator requested centered AgentModal-style dialogs with scrim, booking summary cards, explanatory notices, and modal-specific confirm/cancel actions | Operator conversation, 2026-09-27 |
| C31 | 2026-09-27 | W8 persists the optional host rejection message on the booking and exposes it to guest booking/trip views; broader F7.2.2 response notes/evidence remains deferred to W13 | Operator confirmed the proposed nullable `hostDecisionMessage` behavior | Operator conversation, 2026-09-27 |

---

## 9. Deviations & Discoveries

### W10 deviations (OPEN 2026-09-25) — full detail in the [W10 spec § 6](docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md)

- **D5** — W10 adds `DisputeSettlementService` instead of `TransactionService.applyTicketRemedy`/`manualOverride` (single-sided, cannot express C20). Reconcile with W3 at merge.
- **D6 (RESOLVED 2026-09-25)** — The mock DB held rows predating C17/C20. Per operator direction it is now corrected **in place** (`db/seed-mock-data.sql` edited, `db/snoozeshare-mock.db` rebuilt from `schema.sql` + seed, rebuild verified deterministic): open tickets 2 and 3 sit on `CONFIRMED` bookings 9 and 11 with escrow held (payout and reviews for them removed); resolved ticket 1 now pays the host `380 − 3%` on 8/28 with `COMPLETED` at resolution; ticket 4 / booking 13 is a 50/50 manual adjustment (guest `AGENT_OVERRIDE` +165, host `BOOKING_PAYOUT` 160.05) and `COMPLETED`; tickets 1–4 filed inside the 7-day window; wallet 5's pre-existing `balanceAfter` chain fixed. Ledger invariants (balance = Σ tx, running `balanceAfter`, FK check) verified. `FORCE_COMPLETED` no longer appears in the mock data (C22); `FORCE_CANCELLED` remains (booking 12, suspension cascade). Tests still run against a temp *copy* purely so mutating tests never write to the committed file — no normalisation step exists any more. A schema-parity test still guards migration-vs-`schema.sql` drift.
- **D7** — Design artifact differs from decisions: Force actions (C22), newest-first queue (F9.1.1 wins), "Adjust wallet" dropdown (C20), no Accept amount field (added).
- **D8** — `tickets.category` is label text, not an FK; renames don't propagate.
- **D9** — `BookingStateMachine` gains `AGENT` on `CONFIRMED → COMPLETED` (C23); W3 must be told at merge.
- **D10** — `MigrationRunner` adopts a pre-provisioned DB: the mock DB has tables but no `schema_history`, and the app could not open it otherwise. See the [W10 spec § 6](docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md).
- **D11 (SUPERSEDED by C24, 2026-09-25)** — The admin shell is sidebar-based and uses the current navy `theme.css`, not the canvas tabs / Fall Light palette. Accepted; alignment is a separate UI-design workstream. See the [W10 spec § 6](docs/superpowers/specs/2026-09-25-w10-agent-dispute-resolution-design.md).
- **D12** — Accepted minor findings from the W10 code review (not fixed; revisit if they matter): (a) `JdbcTicketRepository` orders by `createdAt` text, so sub-second ties can mis-order; (b) `MigrationRunner` adoption only checks for a `users` table; (c) settlement reads guest/host wallets once, so a self-booking (guest == host) would lose an update; (d) `AuditServiceImpl` stamps `Instant.now()`, not the injected clock; (e) settlement takes escrow from `booking.totalAmount()`, not the `ESCROW_HOLD` row; (f) `DisputeDetailController.load()` still calls `render()` outside the error handler.
- **Found during W10 execution (2026-09-25, all fixed, each in the ledger):** (a) mock seed timestamps lacked the trailing `Z`, so `JdbcCodecs.instant` rejected them; (b) mock seed IDs used non-hex prefixes, so `UUID.fromString` threw (rule now in § Orientation repo map); (c) the baseline build was red from 7 pre-existing W2 checkstyle violations.
- **Backlog edits made 2026-09-25 (operator approved):** `docs/ProductBacklog.md` — F9.2.1 struck as dropped; F9.2.2 reworded to full-escrow settlement; F9.1.1 gains chat threads; F7.3.1 gains the open-ticket guard; new epic F12 Messaging (W13); changelog entry added.
- **Cross-workstream requirements:** listed under § Workstreams → *Handoffs into W3*.

### D2 — Two schema gaps found while grounding the UI mockups against `db/schema.sql` (OPEN 2026-09-23)

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

- **Latest entry:** 2026-09-26
- **Entries:** 60 (4 backfilled coarsely from git history)

Deviations stay in § Deviations above: those are read every session.
