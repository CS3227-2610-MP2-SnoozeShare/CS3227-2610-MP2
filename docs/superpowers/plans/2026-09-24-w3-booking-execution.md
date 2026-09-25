# W3 — Booking Execution & Trip Hub Implementation Plan

**Goal:** Enable guests to book properties (with atomic escrow), view trips in a tabbed dashboard,
and cancel with policy-based refunds.

**Spec:** `docs/superpowers/specs/2026-09-24-w3-booking-execution-design.md`

## Global Constraints

- Java 25 language level, SQLite via plain JDBC
- Only `repository.jdbc.*` may import `java.sql.*`
- UI controllers depend only on `service.*` interfaces
- All state transitions through `BookingStateMachine.canTransition()`
- Wallet writes in composite operations are inline (not via `WalletLedgerWriter`) — see spec W3-D1
- All existing tests must pass after every task
- Use `compareTo` for monetary BigDecimal assertions (deviation D4)

---

### Task 1: TransactionServiceImpl — escrow hold, refund, history

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/TransactionServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/TransactionServiceTest.java`

**Tests:** holdEscrow deducts wallet + creates ESCROW_HOLD; fails on insufficient funds; fails on
missing booking; refundEscrow credits wallet; partial refund (50%); historyFor returns all txns.

**Reuses:** `WalletLedgerWriter`, `BookingRepository`, `WalletRepository`, `WalletTransactionRepository`

---

### Task 2: BookingServiceImpl — submitRequest (atomic)

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/BookingServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/BookingServiceTest.java`

**Tests:** Creates PENDING booking with correct totalAmount; creates BOOKING-sourced block; deducts
escrow; atomic rollback on insufficient funds; fails on overlapping dates; fails on invalid dates
or missing property.

**Critical:** Inline wallet write within `TransactionManager.inTransaction()` — no `WalletLedgerWriter`.

---

### Task 3: BookingServiceImpl — cancel with refund policy

**Files:**
- Modify: `src/main/java/com/snoozeshare/repository/AvailabilityBlockRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepository.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/BookingServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/BookingServiceTest.java`

**Tests:** Cancel PENDING = 100%; cancel CONFIRMED >48h = 100%; cancel CONFIRMED ≤48h = 50%;
fails for terminal state; fails for wrong guest; removes availability block; publishes event.

---

### Task 4: BookingServiceImpl — decide + query methods

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/impl/BookingServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/BookingServiceTest.java`

**Tests:** Approve → CONFIRMED + event; reject → REJECTED + 100% refund + block removed + event;
fails for wrong host / non-PENDING; tripsFor returns filtered bookings; pendingRequestsFor works.

---

### Task 5: AppContext wiring

**Files:**
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`

**Tests:** bookingService() and transactionService() return non-null.

---

### Task 6: Trip Hub UI

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/guest/trips/TripDashboardController.java`
- Create: `src/main/resources/com/snoozeshare/ui/guest/trips/trip-dashboard.fxml`
- Modify: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/common/theme.css`

---

### Task 7: Book Now button

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/guest/listing/ListingDetailController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/guest/listing/listing-detail.fxml`

---

### Task 8: Cancellation UI

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/guest/trips/TripDashboardController.java`

---

### Task 9: Event bus subscriptions

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/guest/trips/TripDashboardController.java`
- Modify: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`
