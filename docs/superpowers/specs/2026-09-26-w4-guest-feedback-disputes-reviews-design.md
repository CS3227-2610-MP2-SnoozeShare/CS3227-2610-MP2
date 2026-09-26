# W4 — Guest Feedback, Disputes & Reviews — Design Spec

**Date:** 2026-09-26
**Workstream:** W4 (F3 — Guest Feedback, Disputes & Profile Controls)
**Status:** Draft
**Author:** Claude Opus 4.6

---

## 1. Overview

W4 enables guests to file dispute tickets against bookings, view their ticket history, and leave
reviews on completed stays. It builds on the ticket infrastructure delivered by W10 (Agent Dispute
Resolution) and adds the guest-facing entry points that feed the agent queue.

### Backlog items covered

| Item | Description | Priority | Sprint |
|---|---|---|---|
| F3.1.1 | Guest files a dispute ticket with category, title, description (within 7 days of stay end) | Medium | 2 |
| F3.1.2 | Guest specifies requested remedy and supporting text | Medium | 2 |
| F3.1.3 | Guest submits 1–5 star rating and review comment for COMPLETED stays | Low | 3 |

### Out of scope

- Guest↔Agent chat threads (W13 — Messaging)
- Agent-side dispute handling (W10 — Done)
- Host-side ticket filing (W8)
- Review display on listing detail cards (future enhancement)

---

## 2. Integration with existing infrastructure

### W10 provides

- `TicketService` interface with `fileTicket(NewTicketRequest, UUID, Role)` — currently throws
  `UnsupportedOperationException`; W4 implements this method
- `TicketServiceImpl` — W4 adds the `fileTicket` body here
- `TicketRepository` / `JdbcTicketRepository` — persist and query tickets
- `TicketCategoryRepository` — `listCategories()` returns active categories for the filing form
- `Ticket` record, `NewTicketRequest` record, `TicketStatus`, `RemedyType` enums
- `TicketStateMachine` — validates status transitions
- `TicketOpenedEvent` — published when a ticket is filed
- `AuditService` integration patterns

### W4 must build

- `fileTicket()` implementation inside `TicketServiceImpl`
- `TicketRepository.findByRaisedByUserId(UUID)` — new query method for the Support tab
- `ReviewServiceImpl` — implements `ReviewService.submit()`
- `JdbcReviewRepository` — implements `ReviewRepository`
- `AppContext` wiring for `ReviewService` and `ReviewRepository`
- Guest UI: ticket filing modal, Support tab (ticket history + detail), review modal
- Trip Hub: eligibility buttons for "File Dispute" and "Leave Review"

---

## 3. Service layer

### 3.1 `TicketService.fileTicket()` — implementation in existing `TicketServiceImpl`

**Validation rules (all must pass before persisting):**

1. `bookingId` resolves to an existing booking
2. `raisedByUserId` matches `booking.guestId()`
3. Booking status is `CONFIRMED` (stay has ended: `endDate <= today`) or `COMPLETED`
4. Current date is within the dispute window: `today <= booking.endDate() + 7 days`
5. `category` matches the label of an active ticket category (`listCategories()`)
6. `title` is non-blank (trimmed)
7. `description` is non-blank (trimmed)
8. No existing ticket by this user on this booking (query `TicketRepository` — prevents duplicate
   filing; a guest may not file two tickets on the same booking)

**On success:**

- Create a `Ticket` record with status `OPEN`, `assignedAgentId` null, timestamps from injected
  `Clock`
- Persist via `TicketRepository.save()`
- Publish `TicketOpenedEvent` on the event bus
- Record audit entry with action type `TICKET_FILED`
- Return the created `Ticket`

**On failure:** throw `IllegalArgumentException` or `IllegalStateException` with a descriptive
message. The UI catches and displays these.

### 3.2 `ReviewServiceImpl` — new class

Implements `ReviewService.submit(UUID bookingId, UUID guestId, int rating, String comment)`.

**Validation rules:**

1. `bookingId` resolves to an existing booking
2. `guestId` matches `booking.guestId()`
3. Booking status is `COMPLETED`
4. `rating` is between 1 and 5 inclusive
5. No existing review for this booking (`ReviewRepository.findByBookingId()` returns empty)

**On success:**

- Create a `Review` record with a new UUID, timestamp from injected `Clock`
- Persist via `ReviewRepository.save()`
- Record audit entry with action type `REVIEW_SUBMITTED`

**On failure:** throw `IllegalArgumentException` or `IllegalStateException`.

**Dependencies:** `BookingRepository`, `ReviewRepository`, `AuditService`, `Clock`.

### 3.3 `JdbcReviewRepository` — new class

Implements `ReviewRepository`:

- `findByBookingId(UUID bookingId)` — `SELECT * FROM reviews WHERE bookingId = ?`
- `save(Review review)` — `INSERT INTO reviews (...) VALUES (...)`

Follows the same `RowMapper` / `JdbcCodecs` patterns as `JdbcTicketRepository`.

### 3.4 `TicketRepository` — new method

Add `List<Ticket> findByRaisedByUserId(UUID userId)` to the interface.

Implement in `JdbcTicketRepository`:
`SELECT * FROM tickets WHERE raisedByUserId = ? ORDER BY createdAt DESC`

---

## 4. Guest UI

All new screens follow the existing Fall Light theme (`agent-theme.css` applied via `.agent-root`
on all role shells) and the established UI patterns: modal overlays on `StackPane`, dynamic FXML
loading, CSS classes from `theme.css` and `agent-theme.css`.

### 4.1 Trip Hub changes (`TripDashboardController`)

Add two conditional buttons to each booking card:

**"File Dispute" button:**
- Visible when the booking is eligible for dispute filing:
  - Status is `CONFIRMED` with `endDate <= today`, or status is `COMPLETED`
  - `today <= endDate + 7 days`
  - No existing ticket filed by this guest on this booking
- Opens the ticket filing modal overlay

**"Leave Review" button:**
- Visible when:
  - Status is `COMPLETED`
  - No existing review for this booking
- Opens the review modal overlay

Eligibility checks use `TicketRepository.findByRaisedByUserId()` and
`ReviewRepository.findByBookingId()` at card render time. Both are lightweight queries.

### 4.2 Ticket filing modal

A modal overlay (consistent with existing modal patterns — `WalletActionDialogController`,
`ListingDetailController`) containing a form:

| Field | Control | Validation |
|---|---|---|
| Category | ComboBox, populated from `ticketService.listCategories()` | Required — must select one |
| Title | TextField | Required, non-blank |
| Description | TextArea | Required, non-blank |
| Requested Remedy | ComboBox or RadioButtons: Full Refund, Partial Refund, Other | Required — `HOST_PAYOUT` is not shown to guests |
| Supporting Text | TextArea | Optional |

**Buttons:** Submit (calls `ticketService.fileTicket()`), Cancel (closes modal).

**On success:** close modal, show brief success feedback, Trip Hub refreshes (the "File Dispute"
button disappears for this booking).

**On error:** display the error message inline in the modal (e.g., a red label).

**Styling:** follows the modal card pattern with the Fall Light warm palette, consistent border
radius, padding, and button styles.

### 4.3 Support tab — Ticket history

The existing "Support" tab in `GuestShellController` loads a ticket history screen.

**Ticket list view:**
- Shows all tickets filed by the logged-in guest, newest first
- Each card displays: title, category badge, status badge (Open / In Review / Approved /
  Rejected), filed date
- Status badges use color coding consistent with existing status pills:
  - Open — neutral/grey
  - In Review — blue/info
  - Approved — green/success
  - Rejected — red/warning
- Clicking a card navigates to a detail view

**Ticket detail view:**
- Read-only display of all ticket fields:
  - Title, category, status badge
  - Description
  - Requested remedy (human-readable label)
  - Supporting text (if any)
  - Filed date
  - Resolution reason and resolved date (if resolved)
- Back button returns to the ticket list

**Live refresh:** subscribes to `TicketOpenedEvent` and `TicketResolvedEvent` to update the list
without manual refresh.

### 4.4 Review modal

A small modal overlay for submitting a review:

| Field | Control | Validation |
|---|---|---|
| Rating | 5 clickable star buttons (1–5) or a Spinner/ComboBox | Required, 1–5 |
| Comment | TextArea | Optional |

**Buttons:** Submit (calls `reviewService.submit()`), Cancel.

**On success:** close modal, "Leave Review" button disappears for this booking.

**On error:** display error message inline.

---

## 5. AppContext wiring

Add to `AppContext`:

```
ReviewRepository reviewRepository = new JdbcReviewRepository(dataSource);
ReviewService reviewService = new ReviewServiceImpl(bookingRepository, reviewRepository, auditService, clock);
```

Expose `reviewService()` accessor. Pass to Guest UI controllers that need it
(`TripDashboardController`, the review modal controller).

---

## 6. Domain events

**`TicketOpenedEvent`** — already defined by W10. Published by `fileTicket()` on success.

No new event types needed. The Support tab subscribes to existing `TicketOpenedEvent` and
`TicketResolvedEvent`.

---

## 7. Testing strategy

- **Unit tests** for `fileTicket()` validation (each rule), success path, audit, event publishing
- **Unit tests** for `ReviewServiceImpl` — validation, success, duplicate prevention
- **Integration tests** for `JdbcReviewRepository` — round-trip save/find
- **Integration test** for the full filing flow: guest files ticket → appears in agent queue
- **UI smoke tests** following the `AdminUiSmokeTest` pattern if feasible

---

## 8. Decisions

| ID | Decision | Why |
|---|---|---|
| W4-D1 | `HOST_PAYOUT` remedy type is hidden from the guest filing form | It's a host-side remedy; guests request refunds, not host payouts |
| W4-D2 | One ticket per guest per booking | Prevents spam; if the guest needs to add info they can do so through chat (W13) or a new ticket after resolution |
| W4-D3 | Reviews are write-once, no edit or delete | Matches F3.1.3 scope; editing/moderation is not in the backlog |
| W4-D4 | Support tab shows a flat list, not tabs by status | Guest ticket volume is low; filtering adds complexity without value at MVP scale |
| W4-D5 | Star rating uses clickable buttons, not a slider | Discrete 1–5 values; buttons are clearer and simpler to implement in JavaFX |
