# SnoozeShare — Proposed Architecture

## Assumptions & Constraints
- Single-user desktop process (JavaFX), no distributed deployment — so no need for microservices, message brokers, or horizontal scaling. The main "architecture" problem is **internal modularity**, not distributed systems.
- Auth is mocked (no real credential validation) but role separation must still be enforced in code, not just in the UI.
- ACID correctness matters for booking/escrow (no double-booking, no lost refunds) even in a single-process app.
- Team is doing parallel, agent-assisted development across 3 roles — so **clear module boundaries with interfaces** matter more than raw performance.

## 1. Style: Modular Monolith, Layered

One Java process, one database, but split into **strongly-bounded modules** communicating only through interfaces — not a "big ball of mud" JavaFX app where controllers talk straight to JDBC.

```
┌─────────────────────────────────────────────────────────────┐
│                    app-shell (bootstrap)                    │
│ main(), DI wiring, role-based screen router, SessionContext │
└──────────┬───────────────────┬──────────────────┬───────────┘
           │                   │                  │
    ┌──────▼───────┐   ┌───────▼──────┐   ┌───────▼──────┐
    │   ui-guest   │   │    ui-host   │   │   ui-admin   │  (JavaFX/FXML,
    │ (FXML+Ctrl)  │   │ (FXML+Ctrl)  │   │ (FXML+Ctrl)  │   role-isolated)
    └──────┬───────┘   └───────┬──────┘   └───────┬──────┘
           │                   │                  │
           └────────┬──────────┴────────┬─────────┘
                    │    depends on     │
            ┌───────▼───────────────────▼────────┐
            │      service (application)         │  ← interfaces only
            │  ListingService, BookingService    │    exposed to UI
            │  TicketService, UserService        │
            │  WalletService, TransactionService │
            │  ReviewService, AuditService       │
            └────────┬───────────────────┬───────┘
                     │                   │
            ┌────────▼────────┐ ┌────────▼─────────┐
            │     domain      │ │  events (bus)    │  in-process pub/sub
            │ records+enums+  │ │ BookingConfirmed │  for cross-role UI
            │ state machines  │ │ TicketOpened, …  │  refresh & audit hooks
            └────────┬────────┘ └──────────────────┘
                     │
            ┌────────▼─────────┐
            │   repository     │  ← interfaces, DAO impl behind them
            │ (DAO interfaces) │
            └────────┬─────────┘
                     │
            ┌────────▼─────────┐
            │ infra-db (JDBC)  │  SQLite
            └──────────────────┘
```

**Where wallets fit:** `TransactionService` sits *inside* the service layer, next to `BookingService`/`TicketService`, not below them — it's the only thing allowed to move money. `WalletService` is the lower-level primitive it (and top-up/withdraw UI screens) builds on. Neither is a persistence-layer concept; both are business logic with their own repository underneath, same as everything else in that box.

**Why not JPMS module-info.java boundaries:** JavaFX + JPMS is notoriously painful (reflection, `opens`/`exports` fights, FXML loader access). For a course-scoped MVP, use **package-based modularity** in one Maven/Gradle module instead — `com.snoozeshare.{domain,service,repository,infra,ui.guest,ui.host,ui.admin,ui.common,events}` — and optionally add an **ArchUnit** test that asserts `ui.*` never imports `repository.*` directly, `service.*` never imports `ui.*`, etc. You get the architectural guarantee without the JPMS ceremony.

## 2. Suggested package structure

Single Maven/Gradle module, package-based boundaries (see ArchUnit note above for enforcing them). Everything under `com.snoozeshare`:

```
com.snoozeshare
├── app
│   ├── Main.java                      # entry point, bootstraps DI + primary Stage
│   ├── AppContext.java                # wires services↔repositories, holds singletons
│   └── SceneRouter.java               # role-aware screen navigation (see §3)
│
├── domain                             # pure Java, zero JavaFX/JDBC deps
│   ├── model
│   │   ├── User.java, Property.java, Booking.java
│   │   ├── Wallet.java, WalletTransaction.java
│   │   ├── Ticket.java, TicketCategory.java, Review.java, AuditLogEntry.java
│   │   └── AvailabilityBlock.java
│   ├── enums
│   │   ├── Role.java, ListingStatus.java, PropertyType.java, AmenityType.java
│   │   ├── BookingStatus.java, TicketStatus.java, RemedyType.java
│   │   └── WalletTransactionType.java, AccountStatus.java
│   └── statemachine
│       ├── BookingStateMachine.java
│       └── TicketStateMachine.java
│
├── service                            # application/business logic — interfaces + impl
│   ├── ListingService.java            (+ impl/ListingServiceImpl.java)
│   ├── BookingService.java            (+ impl/BookingServiceImpl.java)
│   ├── AvailabilityService.java       (+ impl/...)
│   ├── TicketService.java             (+ impl/...)
│   ├── WalletService.java             (+ impl/WalletServiceImpl.java)     # top-up/withdraw, balance
│   ├── TransactionService.java        (+ impl/TransactionServiceImpl.java) # escrow/payout/refund orchestration
│   ├── UserService.java               (+ impl/...)
│   ├── ReviewService.java             (+ impl/...)
│   └── AuditService.java              (+ impl/...)
│
├── repository                         # persistence-facing interfaces
│   ├── UserRepository.java, PropertyRepository.java, BookingRepository.java
│   ├── AvailabilityBlockRepository.java, TicketRepository.java
│   ├── WalletRepository.java, WalletTransactionRepository.java
│   ├── ReviewRepository.java, AuditLogRepository.java
│   └── jdbc                           # impls, only this package touches JDBC
│       ├── JdbcUserRepository.java, JdbcPropertyRepository.java, ...
│       └── support/RowMappers.java
│
├── infra
│   ├── db
│   │   ├── DataSourceProvider.java    # SQLite connection factory
│   │   └── migration/V1__init.sql, V2__..., (Flyway-style, or hand-run at startup)
│   └── events
│       ├── EventBus.java              # interface
│       ├── InMemoryEventBus.java      # impl
│       └── events/
│           ├── BookingConfirmedEvent.java, BookingCancelledEvent.java
│           ├── TicketOpenedEvent.java, TicketResolvedEvent.java
│           ├── WalletTransactionRecordedEvent.java
│           └── ListingAvailabilityChangedEvent.java
│
├── session
│   ├── SessionContext.java            # interface: currentUser(), currentRole()
│   └── MockSessionContext.java        # impl backed by mocked login
│
└── ui
    ├── common                         # shared across roles
    │   ├── NavShell.java, ViewModelBase.java, FormatUtil.java (currency/date)
    │   ├── wallet/  WalletPanelController.java, wallet_panel.fxml   # balance + top-up/withdraw, embedded in both guest & host shells
    │   └── components/ (rating stars, date-range picker, amenity checklist)
    ├── guest
    │   ├── search/  SearchController.java, SearchViewModel.java, search.fxml
    │   ├── listing/ ListingDetailController.java, listing_detail.fxml
    │   ├── trips/   TripHubController.java, trip_hub.fxml
    │   └── tickets/ GuestTicketController.java, guest_ticket.fxml
    ├── host
    │   ├── listings/  ListingEditorController.java, listing_editor.fxml
    │   ├── calendar/  CalendarBlockController.java, calendar_block.fxml
    │   ├── requests/  RequestQueueController.java, request_queue.fxml
    │   └── tickets/   HostTicketResponseController.java, host_ticket.fxml
    └── admin
        ├── tickets/   DisputeQueueController.java, dispute_queue.fxml
        ├── overrides/ TransactionOverrideController.java, transaction_override.fxml
        ├── accounts/  AccountGovernanceController.java, accounts.fxml
        └── audit/     AuditLogController.java, audit_log.fxml
```

`WalletPanelController` lives in `ui.common` (not duplicated per role) since top-up/withdraw and balance display are identical for guests and hosts — it's constructed once per logged-in session with just `WalletService` + `SessionContext`, and embedded into whichever role shell is active.

Dependency direction is strictly top-to-bottom: `ui.* → service → domain`, `service → repository → infra.db`, `service → infra.events`, everyone → `session`. Nothing under `ui.*` ever imports `repository.*` or `infra.db.*` directly — that's the rule an ArchUnit test would enforce.

## 3. Cross-component interfaces (the "disconnection" mechanism)

| Interaction | Interface type | Why |
|---|---|---|
| UI → business logic | **Service interfaces** (`BookingService`, `TicketService`, …), impls injected via a simple hand-rolled DI (constructor injection wired in `app-shell`, or a lightweight framework like Guice/Spring-lite if the team wants it) | UI modules never see `repository` or `infra-db`. Each role's controllers can be built/tested against a mock service. |
| Service → persistence | **Repository interfaces** (`BookingRepository.findOverlapping(listingId, range)`) implemented by JDBC DAOs | Swappable persistence, testable service layer with in-memory fake repos. |
| Cross-role live updates | **In-process event bus** (simple `Consumer<Event>` pub/sub, or Guava `EventBus`) — e.g. `BookingConfirmedEvent`, `TicketResolvedEvent`, `ListingBlockedEvent` | Guest Trip Hub needs to reflect host decisions; Agent queue needs to reflect new tickets. Since it's one JVM, no need for a real message queue — just decouple "who changed state" from "who needs to refresh." |
| Session/role gating | **`SessionContext` interface** (mocked auth) holding `currentUserId`, `currentRole` | Every service method takes/consults this instead of re-deriving role from raw DB rows; makes “what can Agent X see” a single choke point instead of scattered `if` checks. |
| State transitions | **Domain-owned state machine** (`BookingStatus.canTransitionTo(...)`, `TicketStatus.canTransitionTo(...)`) | Single source of truth for legal transitions, reused by Guest cancel, Host approve/reject, and Agent force-override — prevents each role's UI/service from re-implementing (and diverging on) the rules. |
| Audit trail | **Decorator/explicit hook**: state-changing service methods call `AuditService.record(...)` at the end | Keeps F9 (audit log) centralized without an AOP framework; every mutating service method has exactly one audit call site. |
| Money movement | **`WalletService` + `TransactionService` interfaces**, called service-to-service by `BookingService`/`TicketService` — **never called directly by UI** for anything booking/ticket-related | Centralizes every balance change behind one choke point so wallets can't drift from booking/ticket state. UI only calls `WalletService` directly for the two user-initiated, booking-independent actions: top-up and withdrawal. |

DTOs vs domain records: use the **Java 25 records directly** as your domain model (per the backlog's own guidance) and pass them straight to JavaFX view models — no separate DTO layer needed at this scale; that would be over-engineering for an MVP.

### 3.1 UI → Service interfaces (per role, what each backlog item actually calls)

These are the contracts each role's controllers code against. UI never sees a `Connection`, a SQL string, or another role's controller.

```java
public interface ListingService {
    List<Property> search(SearchCriteria criteria);              // F1.1.1–F1.1.3
    Property getDetail(UUID propertyId);                          // F1.2.1
    PriceBreakdown estimateCost(UUID propertyId, LocalDate start, LocalDate end); // F1.2.2, F2.1.1
    Property create(Property draft, UUID hostId);                 // F4.1.1, validated per F4.1.2
    Property updateStatus(UUID propertyId, ListingStatus status, UUID hostId); // F4.2.1
}

public interface AvailabilityService {
    boolean isRangeAvailable(UUID propertyId, LocalDate start, LocalDate end);   // backs F1.1.1, F5.1.2
    AvailabilityBlock createHostBlock(UUID propertyId, LocalDate start, LocalDate end, UUID hostId); // F5.1.1
    List<AvailabilityBlock> blocksFor(UUID propertyId);
}

public interface BookingService {
    Booking submitRequest(UUID guestId, UUID propertyId, LocalDate start, LocalDate end); // F2.1.1 — internally calls TransactionService.holdEscrow (F2.1.2)
    List<Booking> tripsFor(UUID guestId, TripFilter filter);       // F2.2.1
    List<Booking> pendingRequestsFor(UUID hostId);                 // F6.1.1
    Booking decide(UUID bookingId, boolean approve, UUID hostId);  // F6.1.2 — on reject, calls TransactionService.refundEscrow at 100% (confirmed 2026-09-22); on approve, funds stay escrowed, no wallet write
    Booking cancel(UUID bookingId, UUID actingGuestId);            // F2.3.1 — computes policy % (F2.3.2), calls TransactionService.refundEscrow
    Booking complete(UUID bookingId);                              // marks COMPLETED, calls TransactionService.settleBookingCompletion
    Booking forceTransition(UUID bookingId, BookingStatus target, UUID agentId, String reason); // F7.2.1
    Money previewHostEarnings(UUID bookingId);                     // F6.2.1 — read-only calculation for display, no wallet write
}

public interface TicketService {
    Ticket fileTicket(NewTicketRequest req, UUID raisedByUserId, Role raisedByRole); // F3.1.1
    List<TicketCategory> listCategories();                        // user-customizable categories
    List<Ticket> queueForAgent(TicketStatus statusFilter);         // F7.1.1
    Ticket addAgentNote(UUID ticketId, String note, UUID agentId); // F7.1.2
    Ticket addHostResponse(UUID ticketId, String responseText, UUID hostId); // F6.2.2
    Ticket resolve(UUID ticketId, boolean approve, RemedyType remedy, BigDecimal amount, String reason, UUID agentId); // F7.1.2/F7.2.2 — on approve, internally calls TransactionService.applyTicketRemedy
}

public interface WalletService {
    Wallet getWallet(UUID userId);                                  // guests & hosts each have exactly one wallet
    BigDecimal balanceOf(UUID userId);
    WalletTransaction topUp(UUID userId, BigDecimal amount);        // mocked — no real payment gateway, just credits balance
    WalletTransaction withdraw(UUID userId, BigDecimal amount);     // mocked — rejects if amount > balance
    List<WalletTransaction> statementFor(UUID userId);              // wallet-scoped view for the WalletPanel
}

public interface TransactionService {
    WalletTransaction holdEscrow(UUID bookingId);                                  // F2.1.2 — debits guest wallet by booking total at request creation
    WalletTransaction refundEscrow(UUID bookingId, BigDecimal refundAmount);       // F2.3.2 — credits guest wallet per cancellation policy
    WalletTransaction settleBookingCompletion(UUID bookingId);                     // F6.2.1 — credits host wallet net of 3% platform fee
    WalletTransaction applyTicketRemedy(UUID ticketId, RemedyType remedy, BigDecimal amount, UUID agentId); // F3.1.1/F7.1.2 — dispute payout/refund
    WalletTransaction manualOverride(UUID bookingId, BigDecimal amount, boolean creditGuest, UUID agentId, String reason); // F7.2.2 — agent-forced settlement
    List<WalletTransaction> historyFor(UUID bookingId);             // booking-scoped financial trail, superseded LedgerService.historyFor
}

public interface UserService {
    User register(String displayName, String email, Role role, String registrationCode); // Auth spec
    List<User> listByRole(Role role);
    User suspend(UUID userId, UUID agentId);                       // F8.1.1 — cascades via BookingService/ListingService
}

public interface ReviewService {
    Review submit(UUID bookingId, UUID guestId, int rating, String comment); // F3.1.2
}

public interface AuditService {
    void record(UUID actorId, String actionType, String entityType, UUID entityId, Object before, Object after); // F9.1.1
    List<AuditLogEntry> query(UUID userId, UUID bookingId, String actionType); // F9.1.2
}
```

**Why two financial interfaces instead of one:** `WalletService` is a dumb primitive — it only knows about a single wallet's balance and can be called directly by UI for top-up/withdrawal, which have nothing to do with bookings. `TransactionService` is where the actual business rules live (escrow amounts, 3% fee math, refund policy %, dispute remedies) and is the **only** thing `BookingService`/`TicketService` are allowed to call to move money — controllers never call `TransactionService` directly, and never call `WalletService.topUp/withdraw` from inside a booking/ticket flow. That split keeps "how do bookings pay out" and "how do I add money to my account" as two independently testable concerns.

Each `ui.<role>.*Controller` is constructed with only the service interfaces it needs — e.g. `RequestQueueController(BookingService, SessionContext)` never sees `TransactionService` at all (it just calls `bookingService.decide(...)`, which handles money internally), while `WalletPanelController(WalletService, SessionContext)` never sees bookings — wired by `AppContext` at startup.

### 3.2 Service → Repository interfaces

One repository per aggregate, returning/consuming domain records — no SQL or JDBC types leak upward:

```java
public interface BookingRepository {
    Optional<Booking> findById(UUID id);
    List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end); // must run inside a serializable tx
    List<Booking> findByGuest(UUID guestId);
    List<Booking> findByHostPending(UUID hostId);
    Booking save(Booking booking);
}
```
`ListingService`/`AvailabilityService`/`TicketService`/`UserService`/`ReviewService`/`AuditService` each get a matching `*Repository` the same shape. `WalletService` uses `WalletRepository` (one row per user); `TransactionService` uses `WalletTransactionRepository` (append-only log) — `TransactionService` never touches `WalletRepository` directly for booking/ticket flows, it goes through `WalletService` so balance updates and transaction-log writes stay atomic in one place. Only `repository.jdbc.*` classes import `java.sql.*`; everything else depends on the interface.

```java
public interface WalletRepository {
    Optional<Wallet> findByUserId(UUID userId);
    Wallet save(Wallet wallet);                                     // updates balance
}

public interface WalletTransactionRepository {
    WalletTransaction save(WalletTransaction transaction);          // append-only, never updated/deleted
    List<WalletTransaction> findByWalletId(UUID walletId);
    List<WalletTransaction> findByBookingId(UUID bookingId);
}
```
`TransactionServiceImpl.settleBookingCompletion(...)`, for example, runs one DB transaction that: reads the host's `Wallet` via `WalletRepository`, computes gross − 3% fee, calls `walletRepository.save(...)` with the new balance, then `walletTransactionRepository.save(...)` with a `BOOKING_PAYOUT` row referencing the booking — both writes commit or roll back together.

### 3.3 Event bus contract

```java
public interface EventBus {
    void publish(DomainEvent event);
    <T extends DomainEvent> Subscription subscribe(Class<T> type, Consumer<T> handler);
}

public sealed interface DomainEvent permits BookingConfirmedEvent, BookingCancelledEvent,
        TicketOpenedEvent, TicketResolvedEvent, WalletTransactionRecordedEvent,
        ListingAvailabilityChangedEvent { }
```
Example wiring: `BookingServiceImpl.decide(...)` calls `eventBus.publish(new BookingConfirmedEvent(bookingId))` after persisting; `ui.guest.trips.TripHubController` subscribes at construction to refresh its table, and `ui.host.requests.RequestQueueController` never has to know the Guest UI exists — it just publishes and moves on. `AuditServiceImpl` can also subscribe to these same events as an alternative to explicit call sites, if you'd rather centralize audit-on-event instead of audit-in-service (either is fine; pick one convention and stay consistent).

`TransactionServiceImpl` publishes `WalletTransactionRecordedEvent(walletId, transactionId)` after every write — `ui.common.wallet.WalletPanelController` subscribes once and refreshes the balance/statement no matter which flow moved the money (top-up, escrow hold, payout, refund, or agent override), so it doesn't need bespoke refresh logic per trigger.

### 3.4 SessionContext contract

```java
public interface SessionContext {
    Optional<User> currentUser();
    Role currentRole();
    void loginAs(User user);   // mocked "auth" — just sets state, no credential check
    void logout();
}
```
`SceneRouter` reads `SessionContext.currentRole()` after `loginAs(...)` to pick which of `ui.guest`/`ui.host`/`ui.admin` root scene to load; every service method that needs an actor (approve booking, resolve ticket, suspend user) takes the acting user's id explicitly rather than reaching into a global — keeps services unit-testable without a live session.

### 3.5 State machine contract

```java
public final class BookingStateMachine {
    public static boolean canTransition(BookingStatus from, BookingStatus to, Role actingRole) { ... }
}
```
`BookingServiceImpl.decide/cancel/forceTransition` all call this before writing — Guest cancel and Agent force-cancel share the same legality check, just with different `actingRole` permissions (e.g. only AGENT may reach `FORCE_CANCELLED`/`FORCE_COMPLETED`).

## 4. Database

**Embedded relational DB — SQLite via plain JDBC**, with **Flyway** (or hand-numbered SQL scripts if Flyway feels heavy) for schema migrations.

Why relational, not embedded NoSQL/object store:
- Bookings, listings, tickets, wallets, and wallet transactions are inherently relational (FKs: booking→listing→host, booking→guest, ticket→booking, transaction→wallet→user).
- **Overlap prevention** (F1.1.1, F2.1.1, F5.1.2) and **escrow correctness** (F2.1.2, F7.2.2) both want transactional guarantees — `SELECT ... FOR UPDATE`/serializable transactions when creating a booking, so two guests can't double-book the same dates.
- Course-friendly: H2 needs zero external services, runs embedded, has a browser console for debugging, and behaves like a real RDBMS (so SQL you write is portable if this ever became a client-server app).

Skip JPA/Hibernate for an MVP of this size — the entity graph is small and the team benefits more from writing explicit, readable SQL in DAOs than debugging Hibernate lazy-loading/session issues under a deadline. A thin hand-rolled DAO layer (`PreparedStatement` + a small `RowMapper` helper) is plenty, and keeps the repository interfaces above easy to fake in unit tests.

### Schema (fields beyond what you specified)

**users**
```
userId (UUID, PK)
role (Enum: GUEST, HOST, AGENT)
displayName (String)
email (String)
accountStatus (Enum: ACTIVE, SUSPENDED)
registrationCode (String, nullable — required for HOST/AGENT signup per Auth spec)
createdAt (Instant)
```

**properties** — the operator-specified House fields, plus `hostId` FK → users:
```
propertyId (UUID, PK)
hostId (UUID, FK → users)
status (Enum: ACTIVE, INACTIVE)
title (String)
description (String)
propertyType (Enum: APARTMENT, HOUSE, CONDO, PRIVATE_ROOM)
streetAddress (String)
city (String)
region (String)
postalCode (String)
maxGuests (int)
bedrooms (int)
bathrooms (double)
baseNightlyRate (BigDecimal)
checkInTime (LocalTime)
checkOutTime (LocalTime)
amenities (Set<Enum: WIFI, PARKING, AIR_CONDITIONING, KITCHEN, WASHER, WORK_DESK>)
```

**availability_blocks**
```
blockId (UUID, PK)
propertyId (FK)
startDate, endDate (LocalDate)
source (Enum: HOST_BLOCK, BOOKING)   -- distinguishes manual blackout vs. reservation-driven block
bookingId (FK, nullable)             -- set when source = BOOKING
```
(This single table backs both F5.1 manual blocks and booking-driven date blocking — one overlap-check query serves both F1.1.1 and F5.1.2.)

**bookings**
```
bookingId (UUID, PK)
listingId (FK), guestId (FK)
startDate, endDate (LocalDate)
status (Enum: PENDING, CONFIRMED, REJECTED, CANCELLED_BY_GUEST, CANCELLED_BY_HOST, COMPLETED, FORCE_CANCELLED, FORCE_COMPLETED)
-- REJECTED and CANCELLED_BY_HOST both trigger a 100% TransactionService.refundEscrow, same as a guest cancelling >48h out (confirmed 2026-09-22).
-- Note: BookingService below has no explicit host-initiated cancel method distinct from decide(...,approve=false) — a host cancelling an
-- already-CONFIRMED booking (CANCELLED_BY_HOST) isn't yet covered by a named method; flagged as a gap for whoever specs F6.1.2/host cancellation.
nightlyRateSnapshot (BigDecimal)      -- price at time of booking, since host may change baseNightlyRate later
totalAmount (BigDecimal)              -- nightlyRateSnapshot × nights; no separate guest-side fee (confirmed 2026-09-22 — the only platform fee is the 3% deducted from host BOOKING_PAYOUT)
createdAt, decidedAt, completedAt (Instant, nullable)
```

**wallets** (one per Guest/Host — Agents don't need one)
```
walletId (UUID, PK)
userId (FK, unique)
balance (BigDecimal)
currency (String, "SGD" — confirmed 2026-09-22, not just an example)
updatedAt (Instant)
```

**wallet_transactions** (escrow / payouts / refunds / top-up / withdrawal — F2.1.2, F2.3.2, F6.2.1, F7.2.2)
```
transactionId (UUID, PK)
walletId (FK)
type (Enum: TOP_UP, WITHDRAWAL, ESCROW_HOLD, ESCROW_REFUND, BOOKING_PAYOUT, TICKET_REMEDY, AGENT_OVERRIDE)
amount (BigDecimal)                  -- signed: positive = credit, negative = debit
feeAmount (BigDecimal, nullable)     -- 3% platform fee, populated on BOOKING_PAYOUT rows for transparency
balanceAfter (BigDecimal)            -- snapshot, avoids recomputing history for the wallet statement view
relatedBookingId (FK, nullable)
relatedTicketId (FK, nullable)
initiatedBy (FK userId, nullable)    -- set when an agent triggers a manual override
createdAt (Instant)
```
This is the single **append-only ledger** for all money movement — top-up and withdrawal are just transaction types alongside escrow/payout/refund, so the wallet balance is always fully reconstructable from this table, and agent overrides (F7.2.2) are just another row instead of a special case. `wallets.balance` is a denormalized cache of "sum of this wallet's transactions," updated in the same DB transaction as the row insert (see §3.2) — never written independently.

Note: platform fees aren't modeled as money moving to a separate "platform wallet" — for MVP scope, `feeAmount` on the `BOOKING_PAYOUT` row is informational (gross earnings minus what was actually credited), since there's no admin-facing platform-balance feature in this backlog. If that's ever needed, add a system-owned `Wallet` row and make fee deduction a real double-entry transfer.

Confirmed 2026-09-22: this single-sided convention extends to `TICKET_REMEDY` and `AGENT_OVERRIDE` rows too — a guest-favorable remedy/override writes only the guest's wallet row (no matching host debit), and vice versa. There is no double-entry anywhere in `wallet_transactions`; each row stands alone against its own wallet.

**tickets**
```
ticketId (UUID, PK)
bookingId (FK)
raisedByUserId (FK), raisedByRole (Enum: GUEST, HOST)
category (String — user-customizable per spec, so store as String + a separate `ticket_categories` lookup table admins can edit, not a hard Java enum)
title (String), description (Text)
requestedRemedy (Enum: FULL_REFUND, PARTIAL_REFUND, HOST_PAYOUT, OTHER)
supportingText (Text)
status (Enum: OPEN, UNDER_REVIEW, RESOLVED_APPROVED, RESOLVED_REJECTED)
assignedAgentId (FK, nullable)
agentNotes (Text, nullable)
resolutionReason (Text, nullable)
createdAt, resolvedAt (Instant, nullable)
```

**ticket_categories** (supports "predetermined but user-customizable" categories from your Ticket spec)
```
categoryId (PK), label (String), active (boolean)
```

**reviews**
```
reviewId (PK), bookingId (FK), guestId (FK)
rating (int 1-5), comment (Text), createdAt
```

**audit_log** (F9.1)
```
logId (PK)
actorUserId (FK)
actionType (String — e.g. "BOOKING_FORCE_CANCEL", "WALLET_TRANSACTION_OVERRIDE")
entityType (String), entityId (UUID)
beforeState, afterState (JSON/Text snapshot)
timestamp (Instant)
```

## 5. State machines (shared, not duplicated per role)

Put `BookingStateMachine` and `TicketStateMachine` in the `domain` package as pure functions/enums with a `Set<Status> allowedTransitions(Status from)`. Guest cancel, Host approve/reject, and Agent force-override all call the **same** `BookingService.transition(bookingId, targetStatus, actor)` — the service asks the state machine "is this legal for this actor's role," not each UI screen deciding independently. This is the single biggest thing that prevents Iteration 3's "governance/override" work from fighting Iteration 2's booking logic.

## 6. What I'd revisit as scope grows
- If this ever needs multi-instance/server deployment, the `service` layer interfaces become your REST/gRPC boundary almost for free — that's the payoff of keeping UI decoupled from services now.
- If ticket categories or remedy types need real workflow branching (not just a status enum), consider a small rules table instead of hardcoding in `TicketService`.
- If concurrent booking volume ever mattered, `ESCROW_HOLD` + overlap-check would move from `SERIALIZABLE` transaction to a proper optimistic-locking or queueing scheme — not needed for a single-process MVP with mocked auth.
- Top-up/withdraw are currently mocked (`WalletService` just credits/debits balance, no real payment rail). If this ever needed real money movement, that's where a payment gateway integration (Stripe/PayNow/etc.) would slot in — behind the same `WalletService` interface, so `TransactionService` and every booking/ticket flow built on top of it wouldn't need to change.
- Fees are currently informational (`feeAmount` column, no platform wallet). If the platform ever needed its own reportable balance, introduce a system `Wallet` and make `TransactionService.settleBookingCompletion` a true double-entry transfer (debit guest's held escrow, credit host wallet, credit platform wallet) instead of a single credit row.
