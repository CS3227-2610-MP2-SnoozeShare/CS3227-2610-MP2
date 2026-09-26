# W7 Host Calendar & Date Overrides — Design Specification

**Date:** 2026-09-26  
**Workstream:** W7 / F6 — Host Calendar & Date Overrides  
**Status:** Approved

## Goal

Give hosts a listing-specific booking calendar where they can see availability,
booked dates, and manual blocks, create manual date overrides with an optional
reason, and remove existing manual blocks.

## Scope

The vertical slice includes:

- An “Open booking calendar” button on every Host Listings card.
- A calendar page scoped to the selected listing.
- Month navigation with previous/next controls, month/year label, and a
  seven-column month grid.
- A legend above the calendar at the top right:
  - white = available
  - green = booked
  - red = manually blocked
  - grey = dates belonging to adjacent months
- A right-side manual-block form with `From`, `To`, and optional `Reason`.
- A “Current overrides” list below the form showing **all manual blocks for the
  listing, regardless of the month currently displayed**. Each row shows only
  its start date, end date, and a clickable right-aligned `Remove` text action.
- A back action to return to Host Listings.

W7 does not implement the host request queue, booking approval/decline,
earnings, disputes, or a separate host booking-management page. Existing
booking blocks remain read-only in this page; their source is the persisted
booking/availability state.

## Confirmed behavior

### Date semantics

Date ranges use the existing half-open convention: `from` is inclusive and
`to` is exclusive. The UI labels the second field `To` and validates that it is
after `From`. This matches booking overlap checks and avoids changing existing
booking semantics.

### Creating a manual block

`AvailabilityService.createHostBlock(propertyId, start, end, hostId, reason)`
will:

1. Validate the date range.
2. Load the property and verify that `hostId` owns it.
3. Reject any overlap with an active booking (`PENDING` or `CONFIRMED`, using
   the repository's existing overlap semantics).
4. Reject any overlap with an existing availability block.
5. Persist a `HOST_BLOCK` with a generated ID and nullable trimmed reason.
6. Return the persisted `AvailabilityBlock`.

The operation is atomic from the service caller's perspective. Validation
errors are shown beside the form and do not clear the user's input. A
successful save refreshes both the calendar and the all-month override list.

### Removing a manual block

Each manual override row has a clickable `Remove` text action. Removal is
allowed only for a `HOST_BLOCK` belonging to the selected listing and owned by
the current host. Booking-sourced blocks cannot be removed from this page.
After successful removal, the calendar and override list refresh. The service
rejects an unknown block, a booking-sourced block, or a block belonging to
another property/host.

### Calendar rendering

The page displays the selected listing title and a month grid covering the
current month plus leading/trailing adjacent-month cells needed to complete
the grid. Adjacent-month cells are grey and are not editable. For dates in the
displayed month, booking-sourced blocks take precedence visually over manual
blocks; otherwise a matching `HOST_BLOCK` is red, and unblocked dates are
white. The grid is rebuilt when navigating months or after a mutation.

The page loads all blocks for the selected listing once per refresh. The
current-month color calculation uses those blocks; the override list filters
only by `source == HOST_BLOCK` and never by displayed month.

## Domain and persistence changes

`AvailabilityBlock` gains a nullable `reason` field. The `availability_blocks`
table gains a nullable `reason TEXT` column. The migration used by the app and
the checked-in reference schema are updated consistently. Existing booking
rows continue to use `NULL`.

`AvailabilityBlockRepository` gains:

- `void deleteById(UUID blockId)`

The existing `findByPropertyId` query supplies all blocks needed by the page.
The JDBC row mapper and save/delete operations are updated for `reason`.

## UI boundaries

- `HostListingsController` exposes an `onOpenCalendar` callback and creates a
  button per listing card. The button consumes its click event so it does not
  trigger the card's detail navigation.
- `HostShellController` loads the calendar view and injects the selected
  `Property`, `AppContext`, and back callback.
- `HostCalendarController` owns month navigation, grid rendering, form
  submission, override removal, validation feedback, and refresh state.
- FXML defines the page structure; CSS defines the calendar cell colors,
  legend swatches, override rows, and layout proportions.

Controllers depend only on service interfaces and domain records, following
the repository's UI boundary convention.

## Testing strategy

- Service tests cover ownership, invalid ranges, booking overlap, manual-block
  overlap, trimmed/nullable reason, successful creation, and authorized
  removal.
- Repository tests cover reason round-trip and deleting only the requested
  block.
- UI source/FXML tests cover the listing-card entry point, calendar page
  wiring, legend labels/colors, navigation controls, form fields, and
  all-month override/remove behavior.
- The full Gradle test suite and build run before completion.

## Out of scope

- Editing an existing manual block in place.
- Removing or changing booking-sourced blocks.
- Showing guest identity or request decisions on this page.
- Persisting per-day exceptions distinct from contiguous date ranges.
- Adding a third-party calendar dependency.
