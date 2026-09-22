# Project State — SnoozeShare

**Living document — the source of truth for this project.** Read it in full before doing
anything, then run `git log --oneline -20` to confirm it still matches reality. Update it at
every boundary, not at the end of the session.

- **Phase:** Pre-implementation — project scaffolding only, no feature code written yet
- **Stack:** Java 25, JavaFX 25 (javafx.controls, javafx.fxml), Gradle (application + shadow + checkstyle plugins), SQLite (embedded, file-based, `org.xerial:sqlite-jdbc`) via plain JDBC, JUnit 5 + TestFX for tests
- **Branch:** main (at `d53c621`)
- **Method:** Not yet established — see § Needs a Human (Q1)
- **Last updated:** 2026-09-22 by Claude Sonnet 5 — built and populated the shared mock DB (`db/snoozeshare-mock.db`)
- **Last verified against repo:** 2026-09-22
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
| `docs/superpowers/specs/` | Design specs — one per feature-level workstream (does not exist yet — no workstream has started) |
| `docs/superpowers/plans/` | Implementation plans (does not exist yet) |
| `docs/project-state/done-ledger.md` | The Done ledger — every change, newest first |
| `docs/SnoozeShare-Architecture-Proposal.md` | The pre-existing architecture proposal — module boundaries, package layout, interface contracts, DB schema. Treated as the seed for § Architecture below, not a per-feature spec. |
| `docs/ProductBacklog.md` | The formal product backlog / engineering spec — epics F0–F11, prioritized and sprint-mapped |
| `docs/DeveloperGuide.md` | Handover brief for developers — currently an empty placeholder |
| `docs/UserGuide.md`, `docs/Reflections.md` | End-user guide; reflections on the agentic workflow (not read in full for this bootstrap — out of scope per bootstrapping bounds) |
| `logs/LLM_interactions.md` | LLM interaction log (course requirement) |
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
| S1 | 2026-09-22 (time not tracked) | Claude Sonnet 5 | main | — | Active | Bootstrapped this file; switched DB to SQLite; built and populated the shared mock DB `db/snoozeshare-mock.db` with operator-approved schema/data; no feature (F0–F11) work started yet | 2026-09-22 |

Status vocabulary, used verbatim: `Active` · `Paused` · `Blocked — needs human` (name the
question ID, same as a workstream row).

---

## 3. Workstreams

None have started. The rows below are the backlog's epics (§2 of the architecture proposal is
their shared design; `docs/ProductBacklog.md` is their shared spec source) reframed as
workstreams so future sessions have somewhere to record status. No workstream has a
`docs/superpowers/specs/` or `docs/superpowers/plans/` document yet — per AGENTS.md § 3, one
should be written before coding starts on any of these (all are feature-level or larger).

| ID | Workstream | Status | Spec | Plan | Progress | Guide |
|---|---|---|---|---|---|---|
| W1 | F0 — Auth, Registration & Wallet Provisioning | Not started | — | — | Backlog only: `docs/ProductBacklog.md` §2 | — |
| W2 | F1 — Listing Search & Property Discovery | Not started | — | — | Backlog only: §3 | — |
| W3 | F2 — Booking Execution & Trip Hub (incl. escrow) | Not started | — | — | Backlog only: §3 | — |
| W4 | F3 — Guest Feedback, Disputes & Reviews | Not started | — | — | Backlog only: §3 | — |
| W5 | F4 — Guest Wallet Management (top-up/withdraw) | Not started | — | — | Backlog only: §3 | — |
| W6 | F5 — Host Listing Management & Publishing | Not started | — | — | Backlog only: §4 | — |
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

**How the system is put together, area by area.** Nothing beyond the package skeleton and a
placeholder JavaFX scene exists yet (confirmed by reading `src/main/java/com/snoozeshare/app/
Main.java` and `Launcher.java`, and the fact that every other package under `src/main/java/com/
snoozeshare/` contains only a `package-info.java`). Every area below is therefore marked
*(planned)* and is sourced entirely from
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
`service → repository → infra.db`, `service → infra.events`, everyone → `session`. An ArchUnit
test enforcing this is proposed but not yet added *(planned)*.

### 4.1 App shell & bootstrap *(planned)*

**Now:** Does not exist yet. `Main.java` (`src/main/java/com/snoozeshare/app/Main.java`) is a
placeholder JavaFX `Application` that shows a single `Label("SnoozeShare")` — a smoke-test shell,
not real bootstrap. `Launcher.java` exists only so the shaded jar can launch without a
`module-info.java`. `AppContext` (DI wiring) and `SceneRouter` (role-aware navigation) are
specified but not implemented.

| Path | Role |
|---|---|
| `src/main/java/com/snoozeshare/app/Main.java` | JavaFX entry point (placeholder scene only) |
| `src/main/java/com/snoozeshare/app/Launcher.java` | Non-Application main() for the shaded jar |
| `com.snoozeshare.app.AppContext` *(planned)* | Wires services↔repositories, holds singletons |
| `com.snoozeshare.app.SceneRouter` *(planned)* | Role-aware screen navigation off `SessionContext.currentRole()` |

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C1 | unknown (pre-dates this file) | Package-based modularity (`com.snoozeshare.{domain,service,repository,infra,ui.*,events}`) in one Gradle module, not JPMS `module-info.java` | JavaFX + JPMS reflection/`opens`/`exports` fights are painful for a course-scoped MVP; an ArchUnit test can give the same guarantee | [architecture proposal §1](docs/SnoozeShare-Architecture-Proposal.md) |

### 4.2 UI (role-isolated) *(planned)*

**Now:** No FXML or controllers exist yet — only empty `ui.guest.*`, `ui.host.*`, `ui.admin.*`,
`ui.common.*` package directories (each holding just a `package-info.java`). Per the proposal,
each role gets its own FXML+Controller tree under `ui.<role>`, and `ui.common` holds shared
pieces (`WalletPanelController`, `NavShell`, formatting/validation helpers, shared components)
constructed once per session and embedded into whichever role shell is active. Controllers are
meant to depend only on `service.*` interfaces, never `repository.*` or `infra.db.*` directly.

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C2 | unknown | `WalletPanelController` lives in `ui.common`, not duplicated per role | Top-up/withdraw/balance display is identical for guests and hosts | [architecture proposal §2](docs/SnoozeShare-Architecture-Proposal.md) |

### 4.3 Service (application/business logic) *(planned)*

**Now:** No service interfaces or implementations exist yet — only empty `service` and
`service.impl` package directories. The proposal specifies nine service interfaces
(`ListingService`, `AvailabilityService`, `BookingService`, `TicketService`, `WalletService`,
`TransactionService`, `UserService`, `ReviewService`, `AuditService`) with full method
signatures — see [architecture proposal §3.1](docs/SnoozeShare-Architecture-Proposal.md) for the
exact contracts, including which backlog item (F-number) each method backs.

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|
| C3 | unknown | Two separate financial interfaces: `WalletService` (dumb primitive — balance, top-up, withdraw) vs. `TransactionService` (business rules — escrow, 3% fee, refund policy, dispute remedies). UI may call `WalletService` directly only for top-up/withdrawal; `BookingService`/`TicketService` are the only callers of `TransactionService` | Keeps "how do bookings pay out" and "how do I add money to my account" independently testable; centralizes every balance change behind one choke point so wallets can't drift from booking/ticket state | [architecture proposal §3](docs/SnoozeShare-Architecture-Proposal.md) |
| C4 | unknown | Use Java 25 records directly as the domain model passed to JavaFX view models; no separate DTO layer | Over-engineering for MVP scale | [architecture proposal §3](docs/SnoozeShare-Architecture-Proposal.md) |
| C7 | 2026-09-22 | **No guest-side service fee.** `bookings.totalAmount = nightlyRateSnapshot × nights`, full stop. The only platform fee anywhere is the 3% deducted from a host's `BOOKING_PAYOUT` (already specified). Corrects an earlier mock-data draft that had invented a 5% guest fee to fill the doc's undefined `serviceFeeAmount` column — that column is now removed | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C8 | 2026-09-22 | `REJECTED` and `CANCELLED_BY_HOST` bookings both trigger a 100% `ESCROW_REFUND`, same as a guest cancelling >48h out. (Note: `BookingService` in the proposal has no explicit host-initiated-cancel method distinct from `decide(...,approve=false)` — flagged as a spec gap for whoever builds F6.1.2/host cancellation, not resolved by this decision.) | Operator confirmed "good assumption" while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C9 | 2026-09-22 | `TICKET_REMEDY` and `AGENT_OVERRIDE` wallet rows are single-sided — only the wallet actually credited/debited gets a row, no matching entry on the other side. There is no double-entry anywhere in `wallet_transactions`, extending the doc's existing "fees aren't a real platform-wallet transfer" note to these two types as well | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |
| C10 | 2026-09-22 | `wallets.currency` is `"SGD"` for real, not just the doc's illustrative example | Operator confirmed while reviewing generated mock data | Operator conversation, 2026-09-22 |

### 4.4 Domain *(planned)*

**Now:** No records, enums, or state machines exist yet — only empty `domain.model`,
`domain.enums`, `domain.statemachine` package directories. Specified as pure Java, zero
JavaFX/JDBC dependencies. `BookingStateMachine.canTransition(from, to, actingRole)` and
`TicketStateMachine` are meant to be the single legality check every role's service call goes
through (guest cancel, host approve/reject, agent force-override all call the same function).

| Path | Role |
|---|---|
| `domain.model` *(planned)* | `User`, `Property` (fields: `propertyId`, `hostId`, `status`, `title`, `description`, `propertyType`, `streetAddress`, `city`, `region`, `postalCode`, `maxGuests`, `bedrooms`, `bathrooms`, `baseNightlyRate`, `checkInTime`, `checkOutTime`, `amenities` — operator-specified, see C6), `Booking`, `Wallet`, `WalletTransaction`, `Ticket`, `TicketCategory`, `Review`, `AuditLogEntry`, `AvailabilityBlock` |
| `domain.enums` *(planned)* | `Role`, `ListingStatus`, `PropertyType`, `AmenityType`, `BookingStatus`, `TicketStatus`, `RemedyType`, `WalletTransactionType`, `AccountStatus` |
| `domain.statemachine` *(planned)* | `BookingStateMachine`, `TicketStateMachine` |

### 4.5 Repository & persistence *(planned)*

**Now:** No repository interfaces or JDBC implementations exist yet — only empty `repository`,
`repository.jdbc`, `repository.jdbc.support`, `infra.db`, `infra.db.migration` package
directories. The proposal specifies one repository interface per aggregate (`UserRepository`,
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

### 4.6 Events (in-process bus) *(planned)*

**Now:** No `EventBus` implementation exists yet — only empty `infra.events` and
`infra.events.events` package directories. Specified as a simple `Consumer<Event>` pub/sub
(`EventBus.publish` / `subscribe`) over a sealed `DomainEvent` hierarchy
(`BookingConfirmedEvent`, `BookingCancelledEvent`, `TicketOpenedEvent`, `TicketResolvedEvent`,
`WalletTransactionRecordedEvent`, `ListingAvailabilityChangedEvent`), used so cross-role UI
refresh (e.g. Guest Trip Hub reflecting a Host decision) doesn't require the publisher to know
the subscriber exists.

### 4.7 Session *(planned)*

**Now:** No `SessionContext` exists yet — only an empty `session` package directory. Specified
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
| Q1 | Confirm the development **method** for § header (e.g. subagent-driven development, TDD-first per epic, one workstream at a time) and which epic to spec first — the backlog sequences by sprint (F0 → F1/F2/F4 → F5/F6/F7/F8 in Sprint 1) but no workstream has been started | Nothing is blocked yet since no workstream is `Building`, but the first real session should get an answer before picking W1's spec | 2026-09-22 |

---

## 8. Decisions & Context

No process/priority/tooling decisions have been made by the operator yet in this session or
recorded elsewhere. All decisions found so far shaped specific architecture areas and are
recorded with those areas in § Architecture (C1–C5).

| ID | Date | Decision | Why / who asked | Source |
|---|---|---|---|---|

---

## 9. Deviations & Discoveries

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

---

## 10. Record

The Done ledger lives in **[`docs/project-state/done-ledger.md`](docs/project-state/done-ledger.md)**
— every change, big or small, newest first.

- **Latest entry:** 2026-09-22
- **Entries:** 10 (4 backfilled coarsely from git history, 6 for this session)

Deviations stay in § Deviations above: those are read every session.
