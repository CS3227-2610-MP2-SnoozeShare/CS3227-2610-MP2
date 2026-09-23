# W1 — Shared Platform Foundation Design

## Status

Approved by the operator on 2026-09-23.

This spec supersedes the narrow interpretation of W1 as only F0 authentication and wallet
provisioning. The interaction-logging design remains a completed W1 support-tooling subtask; this
document defines the product foundation that must be complete before F1–F11 feature work can
proceed in parallel.

## Goal

Deliver one stable, tested platform foundation for SnoozeShare. Every later workstream must be
able to build feature-specific business behavior and UI on top of these contracts without
re-implementing domain rules, persistence plumbing, session handling, wallet invariants, event
delivery, audit recording, application wiring, or test infrastructure.

The foundation is complete when a later workstream can:

1. depend on stable domain, service, and repository contracts;
2. use the real SQLite implementation or a fake repository in tests;
3. publish events and record mutations through shared infrastructure;
4. enforce role and state-transition rules through shared policies; and
5. run its UI inside the common application shell without changing the foundation.

## Constraints and decisions

- Use Java 25 records and enums as the domain model; do not introduce a separate DTO layer.
- Use JavaFX 25 for the desktop UI and FXML where appropriate.
- Use embedded SQLite through plain JDBC; only `repository.jdbc.*` may import `java.sql.*`.
- Use hand-numbered SQL migrations or an equally small migration runner; do not introduce JPA,
  Hibernate, a server, or a message broker.
- Authentication is mocked. Host and Agent registration codes are fixed mock constants, not
  secrets or user-managed configuration.
- Use one-workstream-at-a-time development with TDD-first vertical slices. Parallel feature work
  begins only after this foundation passes its exit criteria.
- Preserve the existing financial decisions: SGD wallets, signed append-only wallet rows,
  atomic balance/ledger writes, informational host payout fees, and single-sided remedy/override
  rows.

## Scope

### 1. Domain kernel

Create the shared domain vocabulary and invariants for all planned aggregates:

- Records: `User`, `Property`, `AvailabilityBlock`, `Booking`, `Wallet`, `WalletTransaction`,
  `Ticket`, `TicketCategory`, `Review`, and `AuditLogEntry`.
- Enums: `Role`, `AccountStatus`, `ListingStatus`, `PropertyType`, `AmenityType`,
  `BookingStatus`, `TicketStatus`, `RemedyType`, and `WalletTransactionType`.
- Shared value/validation rules for UUID identity, non-empty required text, dates and times,
  non-negative monetary values, signed transaction amounts, currency, and rating bounds.
- `BookingStateMachine` and `TicketStateMachine` as the only legal-transition authorities. Later
  services may add workflow orchestration, but may not duplicate transition legality checks.

Domain types remain free of JavaFX and JDBC dependencies.

### 2. Persistence and transaction kernel

Provide a complete persistence boundary for every aggregate:

- migration/bootstrap runner for the users, properties, availability, bookings, wallets,
  wallet transactions, tickets, ticket categories, reviews, and audit tables;
- SQLite connection factory and lifecycle management;
- transaction helper supporting commit/rollback and nested service operations within one
  connection boundary;
- repository interfaces for every aggregate;
- shared JDBC support such as prepared-statement binding, row mapping, enum/time/money codecs,
  generated-ID handling, and resource closing;
- baseline JDBC repositories for the shared reads/writes needed by later services; feature-specific
  query methods may be added only through the corresponding repository interface and implementation;
- deterministic isolated test database setup and cleanup.

The committed `db/schema.sql` and mock database remain reference artifacts. Application startup
must use the migration/bootstrap path rather than relying on the committed mock database.

### 3. Session, authorization, and F0 authentication

Implement:

- `SessionContext` and its mocked in-memory implementation;
- `loginAs(User)`, `logout()`, `currentUser()`, and `currentRole()`;
- centralized role/actor authorization helpers used by services and routing;
- `UserService.register(...)` with unique-email validation, required-field validation, and role
  validation;
- Guest registration without a code;
- Host and Agent registration requiring the fixed mock constants held in one shared class;
- automatic zero-balance SGD wallet creation for successful Guest and Host registration;
- no wallet provisioning for Agents;
- mocked login that sets the active user and role without credential verification.

Registration constants must appear in one implementation location and must not be copied into
FXML, controllers, tests, or seed data as independent literals.

### 4. Wallet and ledger kernel

Implement the shared financial mechanics, without implementing every wallet feature UI:

- one wallet per Guest or Host;
- append-only `WalletTransaction` writes;
- atomic update of `wallets.balance` and insertion of the corresponding transaction row;
- `balanceAfter` snapshots;
- signed amounts: positive credits and negative debits;
- rejection of invalid amounts, insufficient funds, wrong wallet ownership, and duplicate
  related operations where the domain contract requires idempotency;
- shared `WalletService` and `TransactionService` contracts, with the foundation implementation
  covering provisioning and ledger-safe primitives;
- event publication after committed wallet changes.

Guest/Host top-up, withdrawal, escrow, payout, refund, remedy, and override policies remain in
their feature workstreams, but all of them must use this kernel.

### 5. Events and audit kernel

Provide the in-process cross-role coordination mechanisms:

- type-safe `DomainEvent` hierarchy and `EventBus` with subscribe, publish, and unsubscribe
  behavior;
- event types for booking confirmation/cancellation, ticket opening/resolution, wallet
  transaction recording, and listing availability changes;
- synchronous in-process delivery with subscriber isolation so one failing subscriber does not
  corrupt the publisher's state;
- `AuditService` and JDBC persistence for actor, action, entity, before-state, after-state, and
  timestamp;
- one consistent audit convention: mutating services explicitly record one audit entry after a
  successful state change, within the same logical operation;
- tests proving events and audit records are emitted only after successful persistence.

### 6. Application shell and shared UI

Implement the minimum shell that all feature UIs can embed:

- `AppContext` for construction and dependency wiring;
- `SceneRouter` driven by `SessionContext.currentRole()`;
- Guest, Host, and Agent/Admin root shells with role-isolated navigation regions;
- shared navigation, validation/error presentation, formatting, and lifecycle conventions;
- shared wallet panel contract/component location under `ui.common`;
- controllers depending on service interfaces only, never repositories or JDBC.

The approved UI choices are:

- Use one authentication entry screen with Login/Register modes. The role selector is shown in
  Register mode, and the registration-code field appears only for Host and Support Agent.
- Keep the internal package name `ui.admin`, but display the role as **Support Agent** everywhere
  user-facing.
- Use a shared top header containing the application name, current user, and logout action; use a
  left navigation region and a central content region in each role shell.
- Embed the shared wallet panel in Guest and Host shells. Support Agents do not receive a wallet
  panel.
- Use one shared JavaFX CSS theme with professional neutral colors, standard control sizes,
  keyboard-accessible forms, and a minimum window size of approximately 1280×800.
- W1 implements authentication screens, shells, navigation, shared validation/error display, and
  placeholder content regions only. Feature screens remain owned by F1–F11.

The shell is not a feature-complete listing, booking, ticket, or governance UI.

### 7. Test and architecture guardrails

Provide reusable verification infrastructure:

- JUnit 5 fixtures for domain, service, repository, and transaction tests;
- fake/in-memory repository implementations for service tests;
- SQLite integration-test helpers using isolated databases;
- TestFX smoke coverage for startup, login, role routing, and logout;
- dependency-direction tests covering UI → service → domain and service → repository → database;
- Gradle tasks/documentation for build, unit tests, integration tests, UI tests, and Checkstyle;
- boundary tests for state machines, wallet atomicity, registration codes, wallet provisioning,
  and audit/event ordering.

## Explicit non-goals

W1 does not implement:

- listing search filters or listing CRUD workflows;
- booking search, booking creation, escrow policy, cancellation policy, or settlement policy;
- host calendar, request queue, earnings, or dispute workflows;
- ticket filing, ticket triage, ticket remedies, or agent override workflows;
- review submission or account-suspension workflows;
- feature-specific FXML screens beyond the common shell and shared components;
- real authentication, payment providers, distributed deployment, double-entry platform fees,
  JPA/Hibernate, microservices, message brokers, or JPMS modules.

## Parallelization contract

After W1 is complete, feature workstreams may proceed independently under these rules:

- feature services depend on shared service/repository interfaces and domain types;
- feature UIs depend on services and shared UI components only;
- all booking and ticket transitions call the shared state machines;
- all money movement calls the shared wallet/transaction kernel;
- all cross-role refresh behavior uses the shared event bus;
- all mutations record audit entries through the shared audit service;
- feature workstreams may extend interfaces only through an explicit reviewed change to this spec
  and the affected feature plan.

## Acceptance criteria

W1 is ready for parallel feature development when:

1. a clean checkout builds and passes Checkstyle and all automated tests;
2. a fresh SQLite database can be created from migrations without the committed mock DB;
3. all planned domain aggregates and shared enums compile with no JavaFX/JDBC leakage into domain;
4. every planned aggregate has a repository contract and a usable JDBC/test seam;
5. Guest, Host, and Agent registration/login/logout behavior passes service and UI smoke tests;
6. successful Guest/Host registration creates exactly one zero-balance SGD wallet;
7. invalid role/code/email/amount/state-transition cases fail deterministically;
8. wallet balance and ledger writes commit or roll back together;
9. event delivery and audit recording occur only after successful mutation;
10. role routing and dependency-direction tests pass; and
11. the implementation plan, test evidence, `PROJECT_STATE.md`, and Done ledger are updated.

## Follow-up artifacts

Once this spec is approved, create an implementation plan that sequences the foundation into
TDD-first vertical slices and identifies the files/packages owned by each slice. The Developer
Guide remains unchanged until the W1 workstream reaches `Done` and the operator confirms the
feature checkpoint.
