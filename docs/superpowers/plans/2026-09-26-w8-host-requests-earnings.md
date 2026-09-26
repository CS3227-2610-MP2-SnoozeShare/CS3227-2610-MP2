# W8 Host Requests, Earnings & Settlement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Host Booking requests page and the F7 request-decision, earnings-preview, and guarded post-dispute settlement flows.

**Architecture:** Extend the existing `BookingService` and repository contracts with host-scoped booking projections, keep approve/reject authorization and escrow behavior in `BookingServiceImpl`, and keep normal payout writes behind `TransactionService.settleBookingCompletion`. A single guarded completion path is reused by page-load/bootstrap sweeps; open-ticket settlement remains owned by W10. The JavaFX page uses a pair of `TableView`s styled with the existing agent queue table rules.

**Tech Stack:** Java 25, JavaFX 25/FXML, SQLite/JDBC, Gradle, JUnit 5, existing EventBus and Fall Light `agent-theme.css`.

**Spec:** `docs/superpowers/specs/2026-09-26-w8-host-requests-earnings-design.md`

## Global Constraints

- W8 covers F7.1.1–F7.1.4, F7.2.1, and F7.3.1; F7.2.2 host response notes/evidence is deferred to W13 by C29.
- Pending requests remain oldest-first; past requests exclude `PENDING` rows.
- Approve confirms without a second escrow debit; reject refunds 100% escrow and removes the booking block atomically.
- Normal completion is eligible only for `CONFIRMED` bookings at checkout plus seven days, and open/`IN_REVIEW` tickets keep escrow held.
- Normal host payout is gross × 0.97; the 3% fee is recorded as informational metadata according to existing wallet conventions.
- Completion must be idempotent and publish `WalletTransactionRecordedEvent` only after commit.
- Use existing service/repository boundaries and no new dependencies, scheduler thread, payout rail, or chat implementation.
- The Host page must match the screenshot’s agent-style table treatment, title/count pill, active/past sections, and contained/outlined actions.

## Review Focus

- A host must never see or decide another host’s booking; test host scoping on pending and past queries in Task 1.
- A rejected or approved request must not create a second escrow hold; test both decision paths in Task 2.
- An open or in-review ticket must prevent settlement while a resolved ticket does not silently trigger W8 logic; test the guard in Task 3.
- Repeated or concurrent completion must create at most one payout; test idempotency and the single-payout invariant in Task 3.
- The page must remain usable with no pending rows, no history rows, long listing names, and no guest ratings; test empty states/fallback rendering in Task 5.

---

### Task 1: Add host booking projections and scoped repository queries

**Files:**
- Create: `src/main/java/com/snoozeshare/service/HostBookingRow.java`
- Modify: `src/main/java/com/snoozeshare/repository/BookingRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcBookingRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/ReviewRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcReviewRepository.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcBookingRepositoryTest.java`
- Create: `src/test/java/com/snoozeshare/repository/jdbc/JdbcReviewRepositoryTest.java`

**Interfaces:**
- Produces `HostBookingRow(Booking booking, String guestDisplayName, String listingTitle, long nights, BigDecimal grossAmount, BigDecimal projectedNetAmount, OptionalDouble guestAverageRating)`.
- Produces `BookingRepository.findByHost(UUID hostId)` for history rows while preserving `findByHostPending(UUID)` oldest-first behavior.
- Produces `ReviewRepository.findByGuestId(UUID guestId)` for guest-average aggregation.

- [ ] **Step 1: Write failing repository tests** for host scoping, pending ordering, history inclusion, review lookup by guest, and zero-review results.
- [ ] **Step 2: Run the repository tests** with `./gradlew test --tests com.snoozeshare.repository.jdbc.JdbcBookingRepositoryTest --tests com.snoozeshare.repository.jdbc.JdbcReviewRepositoryTest`; verify the new methods fail to compile or assert.
- [ ] **Step 3: Implement the repository queries** using property ownership joins and deterministic ordering; return reviews belonging to the requested guest only.
- [ ] **Step 4: Implement `HostBookingRow`** as an immutable service read model with `OptionalDouble` for the no-rating case and `BigDecimal` amounts.
- [ ] **Step 5: Run the focused repository tests** and verify they pass.
- [ ] **Step 6: Commit** with `feat: add host booking projections and queries`.

### Task 2: Complete request decisions and earnings projections

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/BookingService.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/BookingServiceImpl.java`
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`
- Test: `src/test/java/com/snoozeshare/service/BookingServiceTest.java`

**Interfaces:**
- Adds `List<HostBookingRow> pendingRequestRowsFor(UUID hostId)`.
- Adds `List<HostBookingRow> historyRowsFor(UUID hostId)`.
- Keeps `Booking decide(UUID bookingId, boolean approve, UUID hostId)` and `Money previewHostEarnings(UUID bookingId)` as the command/read boundaries.

- [ ] **Step 1: Write failing service tests** for row assembly, gross/net amounts, one-decimal average ratings, `No ratings yet` data, host ownership, approve-without-second-hold, and reject-refund/block-removal behavior.
- [ ] **Step 2: Run `./gradlew test --tests com.snoozeshare.service.BookingServiceTest`** and verify the new row/earnings tests fail.
- [ ] **Step 3: Inject the required read dependencies** (`UserRepository`, `ReviewRepository`) into `BookingServiceImpl`, assemble `HostBookingRow` values from bookings/properties/users/reviews, and preserve the existing oldest-first pending query.
- [ ] **Step 4: Implement `previewHostEarnings(UUID)`** as gross × 0.97 with `BigDecimal` scale/rounding for display, without writing a ledger row.
- [ ] **Step 5: Harden `decide`** so it revalidates `PENDING` state and host ownership inside the existing transaction boundary, preserves one escrow hold, refunds/removes the block only on reject, and publishes the existing events after commit.
- [ ] **Step 6: Update `AppContext` wiring** for the expanded `BookingServiceImpl` constructor.
- [ ] **Step 7: Run the focused service tests** and verify they pass.
- [ ] **Step 8: Commit** with `feat: complete host request decisions and earnings preview`.

### Task 3: Implement guarded normal completion and payout settlement

**Files:**
- Modify: `src/main/java/com/snoozeshare/repository/TicketRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcTicketRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/BookingRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcBookingRepository.java`
- Modify: `src/main/java/com/snoozeshare/service/BookingService.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/BookingServiceImpl.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/TransactionServiceImpl.java`
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`
- Test: `src/test/java/com/snoozeshare/service/BookingServiceTest.java`
- Test: `src/test/java/com/snoozeshare/service/TransactionServiceTest.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcTicketRepositoryTest.java`

**Interfaces:**
- Adds `TicketRepository.findByBookingId(UUID bookingId)`.
- Adds `BookingRepository.findConfirmedEndingOnOrBefore(LocalDate checkoutCutoff)` for the sweep.
- Adds `int BookingService.completeEligibleBookings()`.
- Keeps `Booking complete(UUID bookingId)` as the guarded single-booking operation.
- Implements existing `TransactionService.settleBookingCompletion(UUID bookingId)` as the atomic host payout/completion operation.

- [ ] **Step 1: Write failing tests** for early completion, non-confirmed completion, open/`IN_REVIEW` ticket blocking, eligible completion, net host payout, `BOOKING_PAYOUT` fee metadata, post-commit wallet event, and repeated completion producing one payout.
- [ ] **Step 2: Run the focused Booking/Transaction/Ticket tests** and verify the new completion tests fail against the stubs.
- [ ] **Step 3: Add the ticket-by-booking query** and use it to distinguish open blockers from resolved tickets.
- [ ] **Step 4: Implement `TransactionServiceImpl.settleBookingCompletion(UUID)`** with a single `TransactionManager` transaction that re-reads the booking, finds the listing host wallet, checks for an existing payout, credits gross × 0.97, writes one `BOOKING_PAYOUT`, sets `COMPLETED`/`completedAt`, and publishes the wallet event only after commit.
- [ ] **Step 5: Implement `BookingServiceImpl.complete(UUID)`** as the eligibility/open-ticket guard that delegates to the transaction service and returns the refreshed booking; make repeated completion a safe no-op or documented state rejection without a second payout.
- [ ] **Step 6: Implement `completeEligibleBookings()`** by finding confirmed bookings past checkout + 7 days, continuing over correctly held/open-ticket rows, and reusing `complete(UUID)`.
- [ ] **Step 7: Update AppContext constructor wiring** and run the focused tests until all settlement assertions pass.
- [ ] **Step 8: Commit** with `feat: settle eligible host bookings after dispute window`.

### Task 4: Wire the lifecycle sweep into application/page loading

**Files:**
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`
- Modify: `src/main/java/com/snoozeshare/ui/host/HostShellController.java`
- Modify: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`
- Modify: `src/main/java/com/snoozeshare/ui/guest/trips/TripDashboardController.java`
- Test: `src/test/java/com/snoozeshare/ui/ShellNavigationTest.java`
- Create: `src/test/java/com/snoozeshare/ui/TripDashboardControllerTest.java`

**Interfaces:**
- Consumes `BookingService.completeEligibleBookings()` from Task 3.
- Produces one startup/page-load sweep with no scheduler thread and no duplicate registration.

- [ ] **Step 1: Write failing wiring tests** that exercise the sweep hook once when the app context/page data is loaded and verify normal navigation remains intact.
- [ ] **Step 2: Run the focused shell/trip tests** and verify the new assertions fail.
- [ ] **Step 3: Add a small lifecycle hook** that invokes the idempotent sweep during AppContext startup and before Host Bookings/Guest Trips render data; do not publish user-facing errors for intentionally held rows.
- [ ] **Step 4: Run the focused wiring tests** and verify they pass.
- [ ] **Step 5: Commit** with `feat: trigger guarded booking completion sweep`.

### Task 5: Build the mockup-aligned Host Booking requests page

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/host/bookings/HostBookingsController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/host/bookings/host-bookings.fxml`
- Modify: `src/main/resources/com/snoozeshare/ui/admin/agent-theme.css`
- Modify: `src/main/resources/com/snoozeshare/ui/host/HostShellController.java`
- Create: `src/test/java/com/snoozeshare/ui/HostBookingsControllerTest.java`

**Interfaces:**
- Controller consumes `AppContext.bookingService()`, `pendingRequestRowsFor(UUID)`, `historyRowsFor(UUID)`, `decide(UUID, boolean, UUID)`, and the current session user.
- FXML exposes `pendingCountLabel`, `pendingTable`, `pastTable`, `feedbackLabel`, and approve/reject action callbacks.

- [ ] **Step 1: Write failing structural/UI tests** for title/count pill, active and past tables, exact screenshot columns/copy, empty states, action callbacks, and agent-table/action style classes.
- [ ] **Step 2: Run `./gradlew test --tests com.snoozeshare.ui.HostBookingsControllerTest`** and verify the page tests fail because the controller/FXML are still placeholders.
- [ ] **Step 3: Create the controller** with `setContext(AppContext)`, `reload()`, host-scoped row loading, cell factories for money/date/rating/status formatting, and approve/reject handlers that reload on success and show errors on failure.
- [ ] **Step 4: Replace the placeholder FXML** with the page title, pending pill, active `TableView`, explanatory note, Past requests heading, past `TableView`, and empty/error labels; keep the existing shell navigation contract.
- [ ] **Step 5: Add narrowly scoped booking action CSS** for green contained Approve and red outlined Reject buttons while reusing `.agent-table`, count-pill, and status-pill tokens.
- [ ] **Step 6: Run focused UI tests and XML validation** with `xmllint --noout src/main/resources/com/snoozeshare/ui/host/bookings/host-bookings.fxml`.
- [ ] **Step 7: Commit** with `feat: add host booking requests page`.

### Task 6: Full verification and documentation handoff

**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`
- Test: all existing tests

- [ ] **Step 1: Run `./gradlew checkstyleMain checkstyleTest`** and fix only W8 violations.
- [ ] **Step 2: Run `./gradlew test`**, record any unrelated baseline failures without masking W8 failures, and confirm all W8-focused tests pass.
- [ ] **Step 3: Run `git diff --check` and XML validation** for every changed FXML file.
- [ ] **Step 4: Update `PROJECT_STATE.md`** with the W8 plan link, progress/status, architecture changes, and any deviations; update the Done ledger only after implementation is complete.
- [ ] **Step 5: Commit** with `docs: record W8 implementation verification`.
