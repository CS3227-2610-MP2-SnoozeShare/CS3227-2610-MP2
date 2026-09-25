# Project State — SnoozeShare

**Living document — the source of truth for this project.** Read it in full before doing
anything, then run `git log --oneline -20` to confirm it still matches reality. Update it at
every boundary, not at the end of the session.

- **Phase:** Wallet balance refresh fix — implementation complete, awaiting review
- **Stack:** Java 25, JavaFX 25 (javafx.controls, javafx.fxml), Gradle (application + shadow + checkstyle plugins), SQLite (embedded, file-based, `org.xerial:sqlite-jdbc`) via plain JDBC, JUnit 5 + TestFX for tests
- **Branch:** `w6` (Host features — Listing Management & Publishing)
- **Method:** Native inline execution with TDD-first vertical slices, fresh-context whole-branch review at end
- **Last updated:** 2026-09-25 by Codex — fixed booking wallet event publication
- **Last verified against repo:** 2026-09-25
- **Developer guide:** `docs/DeveloperGuide.md` exists but is a one-line placeholder ("To be completed as the project develops") — not yet seeded. See § 5 of AGENTS.md: first-write is due once the first spec is approved.

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
| `db/schema.sql`, `db/seed-mock-data.sql`, `db/snoozeshare-mock.db` | Shared team-reference SQLite DB — draft SQL + the built `.db` file, committed so everyone queries the same data. Dev/reference artifact, not wired into app startup (see § 4.5) |

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
| S4 | 2026-09-25 | Codex | w6 | W6 | In review | W6 implementation and clean build complete; handoff record written, awaiting review. | 2026-09-25 |
| S5 | 2026-09-25 | Codex | w6 | W6 | In review | Booking escrow now publishes the wallet transaction event after commit so the header balance refreshes; clean build verified. | 2026-09-25 |
| S6 | 2026-09-25 | Claude Opus 4.6 | w5 | W5 | Paused | W5 complete: spec, plan, all 6 tasks implemented. All tests pass. Ready for merge to main | 2026-09-25 |

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
| W6 | F5 — Host Listing Management & Publishing | In review | [listing-management design](docs/superpowers/specs/2026-09-25-w6-listing-management-design.md) | [listing-management plan](docs/superpowers/plans/2026-09-25-w6-listing-management.md) | Host listing detail flow complete: clickable cards, full details, Back navigation, and Edit isolation; clean build passed | Awaiting confirmation |
| W7 | F6 — Host Calendar & Date Overrides | Not started | — | — | Backlog only: §4 | — |
| W8 | F7 — Host Request Queue, Earnings & Disputes | Not started | — | — | Backlog only: §4 | — |
| W9 | F8 — Host Wallet Management | Not started | — | — | Backlog only: §4 | — |
| W10 | F9 — Agent Dispute Resolution & State Overrides | Not started | — | — | Backlog only: §5 | — |
| W11 | F10 — Agent Account Governance | Not started | — | — | Backlog only: §5 | — |
| W12 | F11 — Platform Audit Trail & Analytics | Not started | — | — | Backlog only: §5 | — |

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

**Now:** W1 provides the combined auth/register screen, shared CSS, header/sidebar/content shells,
role routing, validation helper, and shared wallet panel boundary. W2 adds the Guest search
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
Per the proposal,
each role gets its own FXML+Controller tree under `ui.<role>`, and `ui.common` holds shared
pieces (`WalletPanelController`, `NavShell`, formatting/validation helpers, shared components)
constructed once per session and embedded into whichever role shell is active. Controllers are
meant to depend only on `service.*` interfaces, never `repository.*` or `infra.db.*` directly.

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

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C3 | unknown | Two separate financial interfaces: `WalletService` (dumb primitive — balance, top-up, withdraw) vs. `TransactionService` (business rules — escrow, 3% fee, refund policy, dispute remedies). UI may call `WalletService` directly only for top-up/withdrawal; `BookingService`/`TicketService` are the only callers of `TransactionService` | Keeps "how do bookings pay out" and "how do I add money to my account" independently testable; centralizes every balance change behind one choke point so wallets can't drift from booking/ticket state | [architecture proposal §3](docs/SnoozeShare-Architecture-Proposal.md) |
| C4 | unknown | Use Java 25 records directly as the domain model passed to JavaFX view models; no separate DTO layer | Over-engineering for MVP scale | [architecture proposal §3](docs/SnoozeShare-Architecture-Proposal.md) |
| C7 | 2026-09-22 | **No guest-side service fee.** `bookings.totalAmount = nightlyRateSnapshot × nights`, full stop. The only platform fee anywhere is the 3% deducted from a host's `BOOKING_PAYOUT` (already specified). Corrects an earlier mock-data draft that had invented a 5% guest fee to fill the doc's undefined `serviceFeeAmount` column — that column is now removed | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C8 | 2026-09-22 | `REJECTED` and `CANCELLED_BY_HOST` bookings both trigger a 100% `ESCROW_REFUND`, same as a guest cancelling >48h out. (Note: `BookingService` in the proposal has no explicit host-initiated-cancel method distinct from `decide(...,approve=false)` — flagged as a spec gap for whoever builds F6.1.2/host cancellation, not resolved by this decision.) | Operator confirmed "good assumption" while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C9 | 2026-09-22 | `TICKET_REMEDY` and `AGENT_OVERRIDE` wallet rows are single-sided — only the wallet actually credited/debited gets a row, no matching entry on the other side. There is no double-entry anywhere in `wallet_transactions`, extending the doc's existing "fees aren't a real platform-wallet transfer" note to these two types as well | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C10 | 2026-09-22 | `wallets.currency` is `"SGD"` for real, not just the doc's illustrative example | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |

### 4.4 Domain

**Now:** Records, enums, validation, and booking/ticket state machines are implemented as pure
Java with zero
JavaFX/JDBC dependencies. `BookingStateMachine.canTransition(from, to, actingRole)` and
`TicketStateMachine` are meant to be the single legality check every role's service call goes
through (guest cancel, host approve/reject, agent force-override all call the same function).

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

Durable rules, harvested from the architecture proposal. None are enforced by tooling yet
(no ArchUnit test exists) — treat as intent until a workstream adds enforcement.

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
| C15 | 2026-09-23 | Execute W1 natively in the existing `w1` checkout rather than creating a separate worktree | Operator explicitly selected the current checkout for execution | Operator conversation, 2026-09-23 |

---

## 9. Deviations & Discoveries

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

---

## 10. Record

The Done ledger lives in **[`docs/project-state/done-ledger.md`](docs/project-state/done-ledger.md)**
— every change, big or small, newest first.

- **Latest entry:** 2026-09-25
- **Entries:** 17 (4 backfilled coarsely from git history, 13 current/history entries)

Deviations stay in § Deviations above: those are read every session.
