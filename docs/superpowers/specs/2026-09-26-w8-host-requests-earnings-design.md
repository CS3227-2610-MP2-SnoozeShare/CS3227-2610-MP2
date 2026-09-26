# W8 Host Request Queue, Earnings & Dispute Settlement Design

**Date:** 2026-09-26
**Status:** Draft for operator review
**Workstream:** W8 / F7 — Host Request Queue, Earnings & Disputes

## 1. Goal

Implement the host-facing reservation workflow for F7:

- show pending guest booking requests with guest, listing, dates, nights, gross amount,
  projected net earnings, and the guest's average review rating;
- let the property owner approve or reject each pending request;
- settle eligible confirmed bookings after checkout plus the seven-day dispute window;
- keep escrow held when an open dispute exists, so W10 can settle it later.

F7.2.2's broader formal host response notes/evidence flow is deferred to W13 Messaging per
decision C29. W8 includes only the optional message attached to a host rejection, as confirmed by
C31; W8 must not add ticket response fields or use chat as a substitute.

## 2. Existing constraints and decisions

- `BookingService` already owns `pendingRequestsFor`, `decide`, `complete`, and
  `previewHostEarnings`; W8 completes those paths rather than creating a parallel host-request
  service.
- Booking submission already creates the booking-sourced availability block and debits guest
  escrow atomically.
- Rejecting a pending request refunds 100% of escrow and removes its availability block.
- Normal completion pays the host the gross booking amount less the 3% host platform fee.
- Ticket resolution follows C20: for a held escrow, guest refund `R` is fee-free and host
  receives `(escrow - R) × 0.97`. W8's normal settlement must not duplicate W10's ticket
  settlement logic.
- An open ticket blocks automatic completion. A resolved ticket is handled by W10's
  `DisputeSettlementService`.
- Wallet writes must remain atomic and publish `WalletTransactionRecordedEvent` only after the
  database transaction commits.
- A rejection message is a nullable `Booking.hostDecisionMessage` persisted with the booking;
  approvals store it as null. Guest booking/trip views may display the message, while W13 owns
  future dispute-response notes/evidence and chat.
- The application has no server scheduler. Automatic completion is therefore an idempotent
  service sweep invoked during booking-related page loads and application bootstrap, not a new
  background thread.

## 3. Host booking requests page

### 3.1 Layout

Replace the current Host Bookings placeholder with a full-page agent-style screen:

- page title: **Booking requests**;
- an accent count pill beside the title, such as `3 pending`;
- an active requests table styled with the existing `.agent-table` CSS, rounded border, warm
  header background, horizontal columns, and full-width layout;
- a short explanatory note: `Net earning = gross − 3% platform fee. Guest rating is their
  average review score, or "No ratings yet".`;
- a **Past requests** section below the active table, using the same table treatment;
- empty states for no pending requests and no past requests.

The active table columns are:

| Column | Display |
|---|---|
| Guest | Guest display name, bolded |
| Listing | Property title |
| Dates | Start–end date range |
| Nights | Number of nights |
| Gross | Gross booking amount, two decimals |
| Net earning | Gross minus 3% fee, bold accent text |
| Guest rating | One decimal plus star, or `No ratings yet` |
| Action | Contained green `Approve` and outlined red `Reject` |

The past table columns are Guest, Listing, Dates, Nights, Total, and Status. Past rows include
host-visible terminal outcomes such as Confirmed, Rejected, Cancelled, and Completed. Pending
rows are excluded from the past table. Buttons are vertically centered and only the owning host
can act on a row.

### 3.2 Interaction behavior

- Clicking Approve or Reject opens a confirmation modal; the service command is not called until
  the modal's confirm action is pressed.
- Reuse the existing `AgentModal` infrastructure so the owner window receives the 50% grey scrim,
  the modal is centered, Escape closes it, and the original scene root is restored afterward.
- The Approve modal is titled **Approve booking request?** and contains a green summary card with
  the listing title, guest/date/nights line, and the gross amount labelled `earning`; below it is
  the notice `The booking moves to CONFIRMED and the guest is notified. Funds stay held in escrow
  until check-in.` The footer has outlined `Cancel` and contained green `Confirm approve` buttons.
- The Reject modal is titled **Reject booking request?** and contains a red summary card with the
  listing title, guest/date/nights line, and the gross amount labelled `to be refunded`; below it
  is the notice `The guest will be notified and the held funds fully refunded (ESCROW_REFUND) —
  no fee is charged for a host rejection.` It also shows a `Message to guest (optional)` field.
  Confirm reject persists the trimmed field value as `Booking.hostDecisionMessage`; blank input
  is stored as null. The footer has outlined `Cancel` and contained red `Confirm reject` buttons.
- Confirm approve calls `BookingService.decide(bookingId, true, currentHostId, null)`; confirm
  reject calls `BookingService.decide(bookingId, false, currentHostId, hostDecisionMessage)`.
- On success, the row leaves the active table, the pending count decrements, and the past table
  reloads. The booking event and wallet event update other views through the existing event bus.
- On failure, the row remains visible and a page-level error is shown; the action is not silently
  retried.
- Closing or cancelling a modal leaves the row unchanged. The page reloads on navigation and after
  a confirmed decision.

## 4. Service and repository design

### 4.1 Host request view data

Add a service-layer read model, `HostBookingRequest`, containing the booking, property title,
guest display name, nights, gross amount, projected net amount, and optional guest average
rating. Keep persistence records (`Booking`, `Property`, `User`, `Review`) separate from this UI
projection.

Add a corresponding past-request projection or query result for the Host Bookings page. It may
share the same record with a status field if that keeps the controller simple, but the query must
make the pending/past split explicit.

### 4.2 Booking queries

Extend `BookingRepository` with host-scoped history retrieval. The query joins bookings to
properties, filters by `property.hostId`, and orders newest decisions/created rows consistently.
The existing oldest-first pending query remains the source for the active request queue so the
host sees the oldest request first.

The service layer resolves guest display names and review averages through repositories/services,
not through JavaFX controllers. A guest with no reviews returns an empty rating, rendered as
`No ratings yet`.

### 4.3 Request decisions

Keep `BookingService.decide` as the authorization and state-transition boundary. It must continue
to verify that the acting host owns the listing and that the booking is pending. Add an optional
`hostDecisionMessage` argument; approve ignores it and stores null, while reject trims and stores
it. Approval confirms the booking without a second escrow debit. Rejection refunds the existing
escrow, removes the booking availability block, and stores the optional message in one transaction.

`previewHostEarnings` returns gross × 0.97 using currency-safe `BigDecimal` arithmetic, rounded
to two decimal places for display. It does not write a wallet transaction.

## 5. Completion and settlement

### 5.1 Eligibility

`BookingService.complete(bookingId)` may settle only a `CONFIRMED` booking whose checkout date is
at least seven days in the past. It must reject or no-op for pending, rejected, cancelled,
already-completed, or still-within-window bookings.

Before settling, it checks whether the booking has any open or in-review ticket. If one exists,
the booking remains confirmed and escrow stays held. Resolved tickets are not blockers because W10
owns their settlement path.

### 5.2 Atomic normal settlement

Normal completion performs one transaction:

1. re-read the booking and verify eligibility;
2. verify the guest escrow hold exists and the booking has not already been settled;
3. credit the host wallet with gross × 0.97;
4. write a `BOOKING_PAYOUT` transaction related to the booking, recording the 3% fee as
   informational metadata according to the existing wallet conventions;
5. transition the booking to `COMPLETED` and set `completedAt`;
6. commit, then publish the wallet transaction event.

The operation must be idempotent. A concurrent or repeated completion attempt must not create a
second payout.

### 5.3 Automatic sweep

Add an idempotent `completeEligibleBookings()` service operation that finds confirmed bookings
past the dispute window and invokes the same guarded completion path. Call the sweep during
`AppContext` startup and before rendering Host Bookings/Guest Trips data. This provides automatic
settlement during normal application use without introducing a scheduler thread or external job.

The sweep should continue past an ineligible/open-ticket booking and should report no user-facing
error for a booking that is correctly held. Unexpected persistence failures remain errors.

## 6. Styling and accessibility

- Reuse `.agent-table`, `.agent-count-pill`, and existing status-pill tokens in `agent-theme.css`;
  add narrowly scoped booking-request action classes for the green Approve and red Reject buttons.
- Use green contained Approve buttons and red outlined Reject buttons; do not introduce a second
  color palette.
- Modal cards are white with rounded corners and a light drop shadow; approve/reject summary
  panels use the existing success/danger palette and white text, matching the supplied mockups.
- Modal action buttons are right-aligned, with equal-height Cancel/Confirm controls.
- Use accessible text on action buttons, including the guest/listing context where useful.
- Keep action columns wide enough for both buttons so row content does not shift between rows.
- Preserve full-width behavior and scrolling at the existing 1280×800 minimum window.

## 7. Verification strategy

### Domain/service tests

- pending and past host queries include only the acting host's properties;
- projected net earnings are gross minus 3% and use two-decimal display rounding;
- guest average rating returns one decimal and no-rating fallback;
- host ownership and pending-state checks reject unauthorized decisions;
- approve confirms without a second escrow hold;
- reject refunds escrow and removes the booking block atomically;
- completion rejects early/ineligible bookings;
- completion skips open/in-review tickets;
- normal completion credits the host net of fee and records one payout;
- repeated completion does not double-pay;
- completion publishes the wallet event only after commit.

### UI tests

- Host Bookings FXML exposes the title, pending badge, active/past tables, required columns, and
  action handlers;
- the page uses the agent table style and the approved button/status classes;
- approve/reject callbacks call the host-scoped service with the current host identity;
- empty states and error feedback are present;
- XML validation and Checkstyle pass.

## 8. Out of scope

- F7.2.2 broader structured host response notes/evidence — deferred to W13 by C29; W8's optional
  rejection message is the explicitly approved exception;
- guest/host/agent chat threads — W13 Messaging;
- new payout rails or real payment integration;
- agent ticket resolution behavior — implemented by W10;
- changes to the existing W10 ticket settlement rules.
