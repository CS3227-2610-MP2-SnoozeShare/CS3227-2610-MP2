# W3 — Booking Execution & Trip Hub (F2) Design Spec

**Status:** Approved via planning session, 2026-09-24
**Author:** Claude Opus 4.6, with Nathan, 2026-09-24
**Workstream:** W3 (F2 — Booking Execution & Trip Hub Workflow)
**Branch:** `w3`
**Backlog items:** F2.1.1, F2.1.2, F2.1.3 (Sprint 1, High), F2.2.1, F2.2.2, F2.3.1, F2.3.2 (Sprint 2, High/Medium)
**Depends on:** W1 (shared foundation), W2 (listing search) — both merged to `main`
**Visual reference:** [UI design system spec](2026-09-23-ui-design-system-design.md) screen 3 (Trip Hub)

---

## 1. Goal

Enable guests to submit booking requests with atomic escrow hold, view and manage their trips in
a tabbed dashboard, and cancel bookings with a policy-based refund. This is the core Guest
transactional flow — the first feature that mutates wallet state from the Guest UI.

**What later workstreams build on:**
- W4 (F3) adds dispute ticket filing from trip cards
- W5 (F4) adds wallet top-up/withdraw (independent)
- W8 (F7) adds host approve/reject UI and trip completion/payout

---

## 2. Constraints & Decisions

| ID | Decision | Why |
|---|---|---|
| Inherits C7 | No guest-side service fee — `totalAmount = nightlyRate × nights` | Operator-confirmed |
| Inherits C8 | REJECTED and CANCELLED_BY_HOST both trigger 100% ESCROW_REFUND | Operator-confirmed |
| Inherits C3 | BookingService calls TransactionService; UI never calls TransactionService | Architecture proposal §3 |
| Inherits C9 | Single-sided ledger — no double-entry, no platform wallet | Operator-confirmed |
| W3-D1 | Wallet writes in `submitRequest` and `cancel` are done inline (not via `WalletLedgerWriter`) | SQLite uses a single shared Connection; `TransactionManager.inTransaction()` commits at end, so nesting it via `WalletLedgerWriter.record()` would cause premature commit. Standalone `TransactionServiceImpl` methods (holdEscrow, refundEscrow) can still use `WalletLedgerWriter` safely. |
| W3-D2 | Trip tab filtering done client-side after fetching all guest bookings | Simpler than multiple DB queries; acceptable for MVP scale |
| W3-D3 | `complete()`, `forceTransition()`, `previewHostEarnings()` are stubbed with `UnsupportedOperationException` | Owned by W8/W10, not this workstream |
| W3-D4 | PENDING bookings get their own tab (not grouped with Upcoming) | Clearer UX — pending means awaiting host decision, upcoming means confirmed |

---

## 3. Scope

### 3.1 TransactionServiceImpl (new)

Implements `TransactionService` interface. Constructor takes `Connection`, repos, `EventBus`.

- `holdEscrow(bookingId)` — look up booking, deduct `totalAmount` from guest wallet via `WalletLedgerWriter`
- `refundEscrow(bookingId, refundAmount)` — credit guest wallet with specified amount
- `historyFor(bookingId)` — delegate to `WalletTransactionRepository.findByBookingId()`
- Remaining methods stubbed (W8/W10)

### 3.2 BookingServiceImpl (new)

Implements `BookingService` interface. Constructor takes `Connection`, all booking-related repos, `EventBus`.

**`submitRequest(guestId, propertyId, start, end)`** — atomic in single `TransactionManager.inTransaction()`:
1. Validate dates, fetch property (verify ACTIVE)
2. Check no overlapping bookings or availability blocks
3. Calculate `totalAmount = baseNightlyRate × ChronoUnit.DAYS.between(start, end)`
4. Save `Booking` record (PENDING, nightlyRateSnapshot = baseNightlyRate)
5. Save `AvailabilityBlock` (source=BOOKING, bookingId)
6. Inline wallet write: deduct escrow from guest wallet, create ESCROW_HOLD transaction
7. After commit: publish event

**`cancel(bookingId, actingGuestId)`** — refund policy:
- PENDING → 100% refund
- CONFIRMED, >48h before check-in → 100% refund
- CONFIRMED, ≤48h before check-in → 50% refund
- Atomic: update booking status + delete availability block + credit wallet (ESCROW_REFUND)

**`decide(bookingId, approve, hostId)`**:
- Approve → CONFIRMED, publish `BookingConfirmedEvent`
- Reject → REJECTED + 100% refund + delete block, publish `BookingCancelledEvent`

**`tripsFor(guestId, filter)`** — fetch all, filter client-side by TripFilter
**`pendingRequestsFor(hostId)`** — delegate to `BookingRepository.findByHostPending()`

### 3.3 Repository change

Add `deleteByBookingId(UUID bookingId)` to `AvailabilityBlockRepository` and its JDBC impl.

### 3.4 Trip Hub UI

**TripDashboardController** + `trip-dashboard.fxml`:
- Tab bar: Pending | Upcoming | Active | Completed | Cancelled
- Trip cards showing property name, dates, status, total amount
- Cancel button on Pending / Upcoming cards with confirmation overlay and refund preview
- Event bus subscriptions for real-time refresh

**Listing detail "Book Now" button**:
- Wire existing disabled `bookButton` in `listing-detail.fxml`
- `handleBook()` calls `bookingService().submitRequest()`
- Success/error feedback via status label

### 3.5 Wallet refresh

`GuestShellController` subscribes to `WalletTransactionRecordedEvent` to refresh sidebar wallet balance.

---

## 4. Review Focus

1. **Atomicity** — `submitRequest` must roll back all three writes (booking, block, wallet) on any failure
2. **No nested transactions** — inline wallet writes in `BookingServiceImpl`, never `WalletLedgerWriter` inside `inTransaction()`
3. **Refund policy edge cases** — exactly 48h boundary, time zone handling (system default)
4. **State machine compliance** — all transitions go through `BookingStateMachine.canTransition()`
5. **BigDecimal scale** — use `compareTo` not `equals` for monetary assertions (deviation D4)
