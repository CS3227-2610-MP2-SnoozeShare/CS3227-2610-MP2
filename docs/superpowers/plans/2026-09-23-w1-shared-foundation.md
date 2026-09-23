# W1 Shared Platform Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete shared SnoozeShare foundation so F1–F11 can implement feature-specific behavior in parallel against stable domain, persistence, service, session, event, audit, UI-shell, and test contracts.

**Architecture:** Keep the domain pure Java and dependency-free. Put application behavior behind service interfaces, persistence behind repository interfaces, and JDBC access exclusively in `repository.jdbc.*`. Bootstrap all implementations through `AppContext`; route role-specific screens through `SessionContext` and `SceneRouter`; make wallet writes, events, and audit records transactional and testable.

**Tech Stack:** Java 25, JavaFX 25/FXML, Gradle, SQLite via `org.xerial:sqlite-jdbc`, plain JDBC, JUnit 5, TestFX, Checkstyle.

**Spec:** `docs/superpowers/specs/2026-09-23-w1-shared-foundation-design.md`

## Global Constraints

- Use Java 25 records and enums as the domain model; do not add a DTO layer.
- Use embedded SQLite through plain JDBC; only `repository.jdbc.*` may import `java.sql.*`.
- Use hand-numbered SQL migrations or an equally small migration runner; do not add JPA, Hibernate, a server, or a message broker.
- Authentication is mocked; Host and Agent registration codes are fixed mock constants stored in one implementation location.
- Use one-workstream-at-a-time development with TDD-first vertical slices.
- Keep wallets in SGD, use signed append-only wallet rows, and update wallet balance and ledger rows atomically.
- Keep platform payout fees informational and remedy/override transactions single-sided, per decisions C7–C10.
- UI controllers depend on service interfaces only; they never import repositories, JDBC, or database classes.
- Every task must leave the repository buildable and must end with focused tests plus `git diff --check`.

## Review Focus

- SQLite foreign-key and migration ordering: a fresh database must bootstrap without using `db/snoozeshare-mock.db`.
- Monetary atomicity: a failed ledger insert must not leave a changed wallet balance, and a failed balance update must not leave a ledger row.
- Role/code boundary: Guest registration needs no code; Host and Agent need the correct mock constant; Agent registration never creates a wallet.
- State/event/audit ordering: invalid transitions emit nothing; events and audit records appear only after the durable mutation succeeds.
- JavaFX boundary: role routing must work with mocked services while controllers remain free of repository/JDBC dependencies.

---

### Task 1: Establish domain records, enums, and shared validation

**Files:**
- Create: `src/main/java/com/snoozeshare/domain/enums/Role.java`
- Create: `src/main/java/com/snoozeshare/domain/enums/AccountStatus.java`
- Create: `src/main/java/com/snoozeshare/domain/enums/ListingStatus.java`
- Create: `src/main/java/com/snoozeshare/domain/enums/PropertyType.java`
- Create: `src/main/java/com/snoozeshare/domain/enums/AmenityType.java`
- Create: `src/main/java/com/snoozeshare/domain/enums/BookingStatus.java`
- Create: `src/main/java/com/snoozeshare/domain/enums/TicketStatus.java`
- Create: `src/main/java/com/snoozeshare/domain/enums/RemedyType.java`
- Create: `src/main/java/com/snoozeshare/domain/enums/WalletTransactionType.java`
- Create: `src/main/java/com/snoozeshare/domain/model/User.java`
- Create: `src/main/java/com/snoozeshare/domain/model/Property.java`
- Create: `src/main/java/com/snoozeshare/domain/model/AvailabilityBlock.java`
- Create: `src/main/java/com/snoozeshare/domain/model/Booking.java`
- Create: `src/main/java/com/snoozeshare/domain/model/Wallet.java`
- Create: `src/main/java/com/snoozeshare/domain/model/WalletTransaction.java`
- Create: `src/main/java/com/snoozeshare/domain/model/Ticket.java`
- Create: `src/main/java/com/snoozeshare/domain/model/TicketCategory.java`
- Create: `src/main/java/com/snoozeshare/domain/model/Review.java`
- Create: `src/main/java/com/snoozeshare/domain/model/AuditLogEntry.java`
- Create: `src/main/java/com/snoozeshare/domain/validation/DomainValidation.java`
- Test: `src/test/java/com/snoozeshare/domain/DomainModelTest.java`

**Interfaces:**
- `User` includes UUID identity, `Role`, display name, email, `AccountStatus`, nullable registration code, and `Instant createdAt`.
- `Wallet` includes UUID wallet/user IDs, `BigDecimal balance`, `String currency`, and `Instant updatedAt`.
- `WalletTransaction` includes UUID IDs, `WalletTransactionType`, signed `BigDecimal amount`, nullable fee/related IDs, `BigDecimal balanceAfter`, initiator ID, and `Instant createdAt`.
- Use the exact property, availability, booking, ticket, review, and audit fields from the approved W1 spec and architecture proposal.
- `DomainValidation` exposes deterministic checks for required text, email uniqueness input shape, positive/non-negative money, date ranges, rating bounds, and currency.

- [ ] **Step 1: Write failing domain tests** for enum coverage, record construction, required-field rejection, date-range rejection, rating bounds, and signed transaction amounts.
- [ ] **Step 2: Run the focused tests**

Run: `./gradlew test --tests 'com.snoozeshare.domain.*'`

Expected: FAIL because the domain types and validation helper do not exist.

- [ ] **Step 3: Implement the records, enums, and validation helper** with no JavaFX, JDBC, repository, or service imports.
- [ ] **Step 4: Run focused tests and Checkstyle**

Run: `./gradlew test --tests 'com.snoozeshare.domain.*' checkstyleMain checkstyleTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/snoozeshare/domain src/test/java/com/snoozeshare/domain
git commit -m "feat: add shared domain model"
```

### Task 2: Implement shared state machines and service contracts

**Files:**
- Create: `src/main/java/com/snoozeshare/domain/statemachine/BookingStateMachine.java`
- Create: `src/main/java/com/snoozeshare/domain/statemachine/TicketStateMachine.java`
- Create: `src/main/java/com/snoozeshare/service/UserService.java`
- Create: `src/main/java/com/snoozeshare/service/ListingService.java`
- Create: `src/main/java/com/snoozeshare/service/AvailabilityService.java`
- Create: `src/main/java/com/snoozeshare/service/BookingService.java`
- Create: `src/main/java/com/snoozeshare/service/WalletService.java`
- Create: `src/main/java/com/snoozeshare/service/TransactionService.java`
- Create: `src/main/java/com/snoozeshare/service/TicketService.java`
- Create: `src/main/java/com/snoozeshare/service/ReviewService.java`
- Create: `src/main/java/com/snoozeshare/service/AuditService.java`
- Create: `src/main/java/com/snoozeshare/service/requests/NewListingRequest.java`
- Create: `src/main/java/com/snoozeshare/service/requests/NewBookingRequest.java`
- Create: `src/main/java/com/snoozeshare/service/requests/NewTicketRequest.java`
- Test: `src/test/java/com/snoozeshare/domain/statemachine/StateMachineTest.java`

**Interfaces:**
- `BookingStateMachine.canTransition(BookingStatus from, BookingStatus to, Role actingRole)` returns a boolean and is side-effect free.
- `TicketStateMachine.canTransition(TicketStatus from, TicketStatus to, Role actingRole)` returns a boolean and is side-effect free.
- Service interfaces match the signatures in `docs/Snoozeshare-Architecture-Proposal.md` §3.1; methods take explicit actor IDs where the contract requires them.
- Request records contain only the inputs needed by their service and use domain types for dates, money, roles, and enums.

- [ ] **Step 1: Write failing transition tests** for guest cancellation, host decision, agent force transitions, ticket resolution, and invalid role/state combinations.
- [ ] **Step 2: Run the focused state-machine tests**

Run: `./gradlew test --tests 'com.snoozeshare.domain.statemachine.*'`

Expected: FAIL because transition authorities do not exist.

- [ ] **Step 3: Implement the transition tables** as explicit, readable rule sets; reject null/unknown roles and transitions without side effects.
- [ ] **Step 4: Add the service/request interfaces** without implementations or persistence dependencies.
- [ ] **Step 5: Run focused tests and Checkstyle**

Run: `./gradlew test --tests 'com.snoozeshare.domain.statemachine.*' checkstyleMain checkstyleTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/snoozeshare/domain/statemachine src/main/java/com/snoozeshare/service src/test/java/com/snoozeshare/domain/statemachine
git commit -m "feat: define shared state and service contracts"
```

### Task 3: Build SQLite bootstrap, migrations, and transaction infrastructure

**Files:**
- Create: `src/main/java/com/snoozeshare/infra/db/DatabaseConfig.java`
- Create: `src/main/java/com/snoozeshare/infra/db/ConnectionFactory.java`
- Create: `src/main/java/com/snoozeshare/infra/db/TransactionManager.java`
- Create: `src/main/java/com/snoozeshare/infra/db/migration/MigrationRunner.java`
- Create: `src/main/resources/db/migration/V001__foundation.sql`
- Create: `src/test/java/com/snoozeshare/infra/db/DatabaseTestSupport.java`
- Test: `src/test/java/com/snoozeshare/infra/db/DatabaseBootstrapTest.java`

**Interfaces:**
- `ConnectionFactory.open()` returns a configured SQLite `Connection` with foreign keys enabled.
- `TransactionManager.inTransaction(Function<Connection, T> work)` commits on success and rolls back on any exception.
- `MigrationRunner.migrate(Connection connection)` applies each migration exactly once in numeric order and records applied versions.
- `DatabaseTestSupport.openIsolatedDatabase()` returns a temporary migrated database for one test.

- [ ] **Step 1: Write failing bootstrap tests** for a fresh database, foreign-key enforcement, migration ordering, repeat-safe migration, commit, and rollback.
- [ ] **Step 2: Run the focused database tests**

Run: `./gradlew test --tests 'com.snoozeshare.infra.db.*'`

Expected: FAIL because the bootstrap classes and migration do not exist.

- [ ] **Step 3: Add the foundation migration** for all tables and constraints in the approved schema, correcting creation order so referenced tables exist before foreign-key use.
- [ ] **Step 4: Implement connection, migration, transaction, and isolated-test helpers**; keep all JDBC imports in `infra.db` or `repository.jdbc` according to the project boundary.
- [ ] **Step 5: Run the focused tests**

Run: `./gradlew test --tests 'com.snoozeshare.infra.db.*'`

Expected: PASS with a fresh temporary database.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/snoozeshare/infra/db src/main/resources/db/migration src/test/java/com/snoozeshare/infra/db
git commit -m "feat: add sqlite bootstrap and transactions"
```

### Task 4: Implement repository interfaces, JDBC support, and baseline DAOs

**Files:**
- Create: `src/main/java/com/snoozeshare/repository/UserRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/PropertyRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/AvailabilityBlockRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/BookingRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/WalletRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/WalletTransactionRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/TicketRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/TicketCategoryRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/ReviewRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/AuditLogRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcUserRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcBookingRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcWalletRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcWalletTransactionRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcTicketRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcTicketCategoryRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcReviewRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcAuditLogRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/support/JdbcCodecs.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcRepositoryIntegrationTest.java`

**Interfaces:**
- Every repository uses domain records and `Optional`/`List`; no repository interface exposes JDBC types.
- `UserRepository`: `findById`, `findByEmail`, `save`.
- `WalletRepository`: `findByUserId`, `saveBalance`, `insert`.
- `WalletTransactionRepository`: `findByWalletId`, `findByBookingId`, `insert`.
- Aggregate repositories provide `findById`, required owner/foreign-key queries, and `save`/`insert` operations needed by later services.
- `RowMappers` converts SQLite text/numeric values to UUID, Instant, LocalDate, LocalTime, BigDecimal, enums, and amenity sets consistently.

- [ ] **Step 1: Write failing integration tests** for user lookup/save, wallet lookup/save, append-only transaction insert, enum/time/money round trips, and foreign-key rejection.
- [ ] **Step 2: Run the repository tests**

Run: `./gradlew test --tests 'com.snoozeshare.repository.jdbc.*'`

Expected: FAIL because repositories and JDBC support do not exist.

- [ ] **Step 3: Implement repository interfaces and codecs** using domain-only public signatures.
- [ ] **Step 4: Implement baseline JDBC DAOs** with prepared statements, explicit column lists, and try-with-resources.
- [ ] **Step 5: Run integration tests and Checkstyle**

Run: `./gradlew test --tests 'com.snoozeshare.repository.jdbc.*' checkstyleMain checkstyleTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/snoozeshare/repository src/test/java/com/snoozeshare/repository/jdbc
git commit -m "feat: add repository contracts and jdbc adapters"
```

### Task 5: Implement session, authorization, registration, and wallet provisioning

**Files:**
- Create: `src/main/java/com/snoozeshare/session/SessionContext.java`
- Create: `src/main/java/com/snoozeshare/session/MockSessionContext.java`
- Create: `src/main/java/com/snoozeshare/service/impl/UserServiceImpl.java`
- Create: `src/main/java/com/snoozeshare/service/impl/AuthorizationService.java`
- Create: `src/main/java/com/snoozeshare/config/RegistrationCodes.java`
- Create: `src/main/java/com/snoozeshare/service/impl/WalletProvisioningService.java`
- Test: `src/test/java/com/snoozeshare/service/UserServiceTest.java`
- Test: `src/test/java/com/snoozeshare/session/SessionContextTest.java`

**Interfaces:**
- `SessionContext.currentUser()` returns `Optional<User>`; `currentRole()` returns an empty/unauthenticated result when no user is logged in; `loginAs(User)` and `logout()` mutate only session state.
- `RegistrationCodes.HOST_CODE` and `RegistrationCodes.AGENT_CODE` are the only code literals used by registration logic; their values are fixed mock strings and are not read from user input elsewhere.
- `UserServiceImpl.register(displayName, email, role, registrationCode)` returns the persisted `User` or a deterministic validation/domain exception.
- `AuthorizationService.requireRole(Role required, User actor)` and `requireAnyRole(Set<Role>, User actor)` reject unauthorized actors consistently.

- [ ] **Step 1: Write failing tests** for Guest/no-code registration, Host/Agent correct-code registration, wrong/missing codes, duplicate email, Agent-without-wallet, Guest/Host-with-one-wallet, login, logout, and role reads.
- [ ] **Step 2: Run the focused tests**

Run: `./gradlew test --tests 'com.snoozeshare.service.UserServiceTest' --tests 'com.snoozeshare.session.SessionContextTest'`

Expected: FAIL because the session and service implementations do not exist.

- [ ] **Step 3: Implement session and authorization** with no database access in `SessionContext`.
- [ ] **Step 4: Implement registration** using the repository interfaces, a single transaction, and the shared registration constants.
- [ ] **Step 5: Implement wallet provisioning** so successful Guest/Host registration creates one zero-balance SGD wallet and Agent registration creates none.
- [ ] **Step 6: Run focused tests, repository integration tests, and Checkstyle**

Run: `./gradlew test --tests 'com.snoozeshare.service.UserServiceTest' --tests 'com.snoozeshare.session.SessionContextTest' checkstyleMain checkstyleTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/snoozeshare/session src/main/java/com/snoozeshare/service/impl src/main/java/com/snoozeshare/config src/test/java/com/snoozeshare/service src/test/java/com/snoozeshare/session
git commit -m "feat: add mocked auth and wallet provisioning"
```

### Task 6: Implement the wallet and transaction kernel

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/WalletServiceImpl.java`
- Create: `src/main/java/com/snoozeshare/service/impl/TransactionServiceImpl.java`
- Create: `src/main/java/com/snoozeshare/service/impl/WalletLedgerWriter.java`
- Test: `src/test/java/com/snoozeshare/service/WalletLedgerTest.java`
- Test: `src/test/java/com/snoozeshare/service/WalletTransactionAtomicityTest.java`

**Interfaces:**
- `WalletLedgerWriter.record(walletId, type, amount, feeAmount, relatedBookingId, relatedTicketId, initiatedBy)` updates the wallet and inserts one transaction in one transaction-manager callback, returning the persisted `WalletTransaction`.
- `WalletService.getWallet`, `balanceOf`, `topUp`, `withdraw`, and `statementFor` use the shared ledger writer; booking/ticket code will later call `TransactionService` for business-specific flows.
- `TransactionService` exposes the approved escrow/refund/payout/remedy/override signatures but does not invent feature policies outside the W1 kernel.

- [ ] **Step 1: Write failing tests** for positive credits, negative debits, insufficient funds, zero/negative invalid inputs, `balanceAfter`, atomic rollback, append-only statements, SGD enforcement, and single-sided rows.
- [ ] **Step 2: Run the focused wallet tests**

Run: `./gradlew test --tests 'com.snoozeshare.service.*Wallet*' --tests 'com.snoozeshare.service.WalletTransactionAtomicityTest'`

Expected: FAIL because the ledger writer and implementations do not exist.

- [ ] **Step 3: Implement the ledger writer** with one transaction boundary and no independent wallet-balance writes.
- [ ] **Step 4: Implement wallet primitives** and deterministic domain exceptions for invalid or unauthorized operations.
- [ ] **Step 5: Run wallet tests, repository tests, and Checkstyle**

Run: `./gradlew test --tests 'com.snoozeshare.service.*Wallet*' --tests 'com.snoozeshare.service.WalletTransactionAtomicityTest' checkstyleMain checkstyleTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/snoozeshare/service/impl src/test/java/com/snoozeshare/service
git commit -m "feat: add atomic wallet ledger kernel"
```

### Task 7: Implement events and audit infrastructure

**Files:**
- Create: `src/main/java/com/snoozeshare/infra/events/DomainEvent.java`
- Create: `src/main/java/com/snoozeshare/infra/events/EventBus.java`
- Create: `src/main/java/com/snoozeshare/infra/events/InProcessEventBus.java`
- Create: `src/main/java/com/snoozeshare/infra/events/events/BookingConfirmedEvent.java`
- Create: `src/main/java/com/snoozeshare/infra/events/events/BookingCancelledEvent.java`
- Create: `src/main/java/com/snoozeshare/infra/events/events/TicketOpenedEvent.java`
- Create: `src/main/java/com/snoozeshare/infra/events/events/TicketResolvedEvent.java`
- Create: `src/main/java/com/snoozeshare/infra/events/events/WalletTransactionRecordedEvent.java`
- Create: `src/main/java/com/snoozeshare/infra/events/events/ListingAvailabilityChangedEvent.java`
- Create: `src/main/java/com/snoozeshare/service/impl/AuditServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/infra/events/InProcessEventBusTest.java`
- Test: `src/test/java/com/snoozeshare/service/AuditServiceTest.java`

**Interfaces:**
- `EventBus.subscribe(Class<T> type, Consumer<T> subscriber)`, `publish(DomainEvent event)`, and `unsubscribe(...)` are type-safe and synchronous.
- `DomainEvent` exposes event identity and creation time; concrete events expose only immutable IDs and relevant actor/entity data.
- `AuditService.record(actorId, actionType, entityType, entityId, before, after)` persists one `AuditLogEntry`; `query(userId, bookingId, actionType)` returns matching entries.

- [ ] **Step 1: Write failing tests** for typed subscription, unsubscribe, subscriber failure isolation, publish ordering, audit persistence, and no event/audit on failed mutation.
- [ ] **Step 2: Run the focused tests**

Run: `./gradlew test --tests 'com.snoozeshare.infra.events.*' --tests 'com.snoozeshare.service.AuditServiceTest'`

Expected: FAIL because event and audit implementations do not exist.

- [ ] **Step 3: Implement the event bus** with defensive subscriber iteration and deterministic synchronous delivery.
- [ ] **Step 4: Implement audit persistence** through `AuditLogRepository` and the approved explicit post-success recording convention.
- [ ] **Step 5: Run focused tests and Checkstyle**

Run: `./gradlew test --tests 'com.snoozeshare.infra.events.*' --tests 'com.snoozeshare.service.AuditServiceTest' checkstyleMain checkstyleTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/snoozeshare/infra/events src/main/java/com/snoozeshare/service/impl/AuditServiceImpl.java src/test/java/com/snoozeshare/infra/events src/test/java/com/snoozeshare/service/AuditServiceTest.java
git commit -m "feat: add events and audit infrastructure"
```

### Task 8: Implement application context, role routing, and shared UI shell

**Files:**
- Create: `src/main/java/com/snoozeshare/app/AppContext.java`
- Create: `src/main/java/com/snoozeshare/app/SceneRouter.java`
- Modify: `src/main/java/com/snoozeshare/app/Main.java`
- Create: `src/main/resources/com/snoozeshare/ui/common/nav-shell.fxml`
- Create: `src/main/resources/com/snoozeshare/ui/common/auth.fxml`
- Create: `src/main/resources/com/snoozeshare/ui/common/theme.css`
- Create: `src/main/resources/com/snoozeshare/ui/guest/guest-shell.fxml`
- Create: `src/main/resources/com/snoozeshare/ui/host/host-shell.fxml`
- Create: `src/main/resources/com/snoozeshare/ui/admin/admin-shell.fxml`
- Create: `src/main/java/com/snoozeshare/ui/common/NavShellController.java`
- Create: `src/main/java/com/snoozeshare/ui/common/AuthController.java`
- Create: `src/main/java/com/snoozeshare/ui/common/ValidationMessage.java`
- Create: `src/main/java/com/snoozeshare/ui/common/wallet/WalletPanelController.java`
- Create: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`
- Create: `src/main/java/com/snoozeshare/ui/host/HostShellController.java`
- Create: `src/main/java/com/snoozeshare/ui/admin/AdminShellController.java`
- Test: `src/test/java/com/snoozeshare/app/SceneRouterTest.java`
- Test: `src/test/java/com/snoozeshare/ui/UiDependencyTest.java`

**Interfaces:**
- `AppContext` exposes the shared `SessionContext`, service implementations, repositories, `EventBus`, `AuditService`, and `SceneRouter` as application-scoped dependencies.
- `SceneRouter.routeFor(Role role)` selects Guest, Host, or Agent/Admin root scene; unauthenticated state selects the login/registration entry scene.
- `AuthController` switches between Login and Register modes, shows the role selector only in Register mode, and shows the registration-code field only for Host or Support Agent.
- The user-facing Agent/Admin label is **Support Agent**; the internal package remains `ui.admin`.
- Controllers receive service interfaces and session context through constructors or documented FXML injection; no controller imports repository or JDBC packages.

- [ ] **Step 1: Write failing routing and dependency tests** for unauthenticated state, each role, logout, unknown/null role, and forbidden UI imports.
- [ ] **Step 2: Run the focused tests**

Run: `./gradlew test --tests 'com.snoozeshare.app.SceneRouterTest' --tests 'com.snoozeshare.ui.UiDependencyTest'`

Expected: FAIL because the shell and router do not exist.

- [ ] **Step 3: Implement `AppContext` and `SceneRouter`** using the real foundation services and repositories.
- [ ] **Step 4: Add the combined authentication FXML and controller** with Login/Register modes, conditional role/code controls, accessible labels, and deterministic validation messages.
- [ ] **Step 5: Add minimal FXML root shells and shared UI components** using a top header, left navigation, central content region, shared CSS theme, and a wallet panel in Guest/Host shells only.
- [ ] **Step 6: Wire `Main`** to create the context, show the authentication scene, enforce the approximately 1280×800 minimum window size, and route after mocked login.
- [ ] **Step 7: Run headless UI tests and Checkstyle**

Run: `./gradlew test --tests 'com.snoozeshare.app.SceneRouterTest' --tests 'com.snoozeshare.ui.*' checkstyleMain checkstyleTest`

Expected: PASS in the configured TestFX/headless environment.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/snoozeshare/app src/main/java/com/snoozeshare/ui src/main/resources src/test/java/com/snoozeshare/app src/test/java/com/snoozeshare/ui
git commit -m "feat: add shared application shell and routing"
```

### Task 9: Add full foundation integration and architecture verification

**Files:**
- Create: `src/test/java/com/snoozeshare/foundation/W1FoundationIntegrationTest.java`
- Create: `src/test/java/com/snoozeshare/architecture/LayerDependencyTest.java`
- Modify: `build.gradle` only if a narrowly scoped architecture-test dependency or test task is required
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`

**Interfaces:**
- The integration test exercises migration → registration → wallet provisioning → login → role routing → wallet ledger write → event publication → audit persistence using the real SQLite adapters.
- The layer test enforces `ui.* → service/session/domain`, `service → repository/infra.events/domain`, and `repository.jdbc → infra.db/java.sql` without allowing UI-to-JDBC or domain-to-framework imports.

- [ ] **Step 1: Write the failing end-to-end foundation test** covering Guest, Host, and Agent registration, wallet counts, login/logout, role routing, a committed wallet change, its event, and its audit row.
- [ ] **Step 2: Run the integration test**

Run: `./gradlew test --tests 'com.snoozeshare.foundation.W1FoundationIntegrationTest'`

Expected: FAIL until all previous slices are wired through `AppContext`.

- [ ] **Step 3: Wire missing application dependencies** and make the integration test pass without using the committed mock DB.
- [ ] **Step 4: Add the architecture dependency test** and make any import/package corrections required by the approved architecture.
- [ ] **Step 5: Run the complete verification suite**

Run: `./gradlew clean build test`

Expected: PASS with Checkstyle, unit tests, repository integration tests, architecture tests, and configured TestFX smoke tests.

- [ ] **Step 6: Update the state and Done ledger** with the verified W1 status, test evidence, deviations, and the next-workstream handoff.
- [ ] **Step 7: Commit**

```bash
git add build.gradle PROJECT_STATE.md docs/project-state/done-ledger.md src/test/java/com/snoozeshare/foundation src/test/java/com/snoozeshare/architecture
git commit -m "test: verify complete shared foundation"
```

## Final W1 handoff checklist

- [ ] All tasks 1–9 are complete and each task's focused tests pass.
- [ ] `./gradlew clean build test` passes from a clean checkout.
- [ ] A fresh SQLite database is bootstrapped from migrations; the committed mock DB is not required.
- [ ] Domain has no JavaFX/JDBC imports.
- [ ] UI has no repository/JDBC imports.
- [ ] Registration constants are centralized and Host/Agent validation is covered by tests.
- [ ] Guest/Host registration creates exactly one zero-balance SGD wallet; Agent registration creates none.
- [ ] Wallet balance and transaction writes are atomic and reconstructible.
- [ ] Events and audit rows occur only after successful mutations.
- [ ] W1 spec, plan, `PROJECT_STATE.md`, and Done ledger reflect verified reality.
- [ ] The operator confirms W1 before F1–F11 parallel implementation begins.
