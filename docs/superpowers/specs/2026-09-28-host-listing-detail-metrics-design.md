# Host Listing Detail Metrics and Reviews Design

## Goal

Bring the Host Listing Detail content in line with the approved artifact by making Reviews and the Performance card data-backed, while leaving Host navigation unchanged.

## Scope

- Keep the existing Host shell navigation and listing actions unchanged.
- Expand the listing metrics projection used by Host Listing Detail.
- Show review count, average rating, guest name, and review comment for the listing.
- Calculate trailing 30-day occupancy as booked nights overlapping the trailing 30 calendar days divided by 30, capped at 100%.
- Calculate trailing 30-day earnings as the sum of `BOOKING_PAYOUT` wallet transactions recorded during the trailing 30 days for bookings belonging to the listing.
- Allocate approximately 60% of the content row to listing details and 40% to the Performance card.

## Design

`ListingMetricsService` remains the boundary used by the Host UI. `ListingMetrics` is expanded with immutable review summaries, occupancy percentage, and earnings amount. The JDBC implementation performs the projection using the existing `bookings`, `reviews`, `users`, and `wallet_transactions` tables; no schema changes are required.

The Host Detail controller binds the expanded result to the existing artifact-shaped sections. Review rows render guest display name, star rating, and comment. Performance renders total bookings, occupancy for the trailing 30-day window, and payout earnings for that same recording window. Missing values render as `—` where appropriate.

## Rules

- The trailing window is `[now - 30 days, now]` using the service clock.
- A booking contributes occupancy nights only for the intersection of its stay dates and the trailing window; nights are counted using the existing half-open booking date convention.
- Occupancy is capped at 100%.
- Earnings include only `BOOKING_PAYOUT` rows whose transaction timestamp is within the trailing window and whose related booking belongs to the listing.
- Reviews are ordered newest first, matching the existing review repository ordering.

## Verification

- Service tests cover review projection, guest-name mapping, booking-window overlap, occupancy capping, payout filtering, and empty results.
- Host UI tests cover the expanded Performance layout, 40% card sizing, review row structure, and artifact-aligned labels.
- Focused tests and Checkstyle must pass; the full suite's known unrelated AgentModal failure remains separately reported if present.
