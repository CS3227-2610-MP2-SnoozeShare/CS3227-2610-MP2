# W16 — Guest UI mockup alignment (design)

Date: 2026-09-29 · Requested by the operator (see PROJECT_STATE § Decisions C49) · Status: built, awaiting operator review (deviations: PROJECT_STATE § Deviations D38)

Visual source of truth: the Claude Design canvas
<https://claude.ai/artifact/PWBCxbfv9e9FGVvY6RKUwd> — boards `Search`, `TripHub`, `ListingDetail`,
`ReviewModal`, `Wallet`, `Tickets`. Fall Light tokens are the ones already in `agent-theme.css`
(the guest shell is `.agent-root`), so no new palette is introduced.

## 1. Scope

1. **Messages (Guest and Host).** Standardise type sizes with the other tabs.
   - Guest sidebar title, row title and row subtitle take the Host values (16 / 14 / 13 px). Guest currently
     inherits 22 / 16 / 13 from `agent-theme.css`.
   - **Both** roles: the right-hand chat header shrinks to the mockup size — padding 16px vertical, title 15px
     bold, subtitle 12px — instead of the current 24px vertical / 18px / 14px (Host) and 20px / 22px / 15px (Guest).
     The header's horizontal inset stays 32px (the Host pages' shared content edge, pinned by `HostPageLayoutTest`).
   - Ticket conversations show a status pill (OPEN / RESOLVED) at the right end of the chat header, where the
     Guest `+ New Ticket` button sits for booking conversations. It has the button's dimensions
     (same font size, padding, height, fixed width). The pill beside the title in the Guest header is removed
     (it moves into this slot). The list-row pill stays; Guest rows gain it too (Host already has it), so both
     roles match.
2. **Guest Search.** Segmented pill search bar (Location, Check-in, Check-out, Guests, Max nightly budget, Search
   button) and a 3-column grid of cards: 130px gradient banner, title, type pill, `N bed · City`, price in the
   accent colour. Unavailable-for-dates cards keep their dimmed treatment and label.
   - New optional filter `maxNightlyRate`: `SearchCriteria` gains a fifth component (the existing 4-argument
     constructor is kept), `JdbcPropertyRepository.findBySearchCriteria` adds `baseNightlyRate <= ?`.
3. **Guest Trips.** One page titled *My trips* with three collapsible sections — **Upcoming**, **Active**,
   **Past** — each a clickable header with a chevron, expanded by default. Cards follow the mockup: 56px
   gradient thumbnail, status pill, title, `dates · Host: Name`, actions on the right.
   - Upcoming: Message host + Cancel (red outline). Active: Message host. Past: Leave a review (filled) for a
     completed, unreviewed stay; cancelled rows are dimmed with a refund note.
   - The existing **File dispute** action is kept (W4) and sits with the other actions.
   - The existing host decision message is kept.
   - **Message host** switches to the Messages tab with that booking's conversation selected.
4. **Listing detail modal.** 960px-max modal with a breadcrumb bar (`Search › Title`, round ✕), 200px gradient
   banner, two columns: left — title + type pill, address, facts strip (max guests, bedrooms, bathrooms,
   check-in, check-out), description, host card, amenity pills, reviews; right — price card with Check-in /
   Check-out date pickers, `N nights × rate`, Total, Book now, escrow note.
   - Reviews and the average rating come from `ListingMetricsService` (already used by the Host detail page);
     "Show all N reviews" toggles beyond the first two.
5. **Review modal.** 480px card: title + ✕, stay summary strip (thumbnail, title, dates · Host), 5 large stars,
   optional comment, full-width *Submit review*.
6. **Wallet.** The top *Available balance* card: `#f0e8d8` background, no border, all text `#381a10`,
   caption 12px bold uppercase, amount 32px bold, `SGD` 13px, hint 11px.
   The wallet page is one shared resource, so Host Wallet changes too.

## 1a. Follow-ups (operator, same day; PROJECT_STATE § Decisions C50)

- **Cancel booking modal** (canvas board `ConfirmCancelBooking`): the Trips Cancel button opens it; it states the refund from
  `BookingService.previewCancellationRefund` (100% more than 48h before check-in, 50% within), the policy note, *Keep booking*
  and *Confirm cancel*.
- **Scroll bars:** all scroll bars in the Guest, Host and Agent themes use the table look (10px, pill thumb, no arrows).
- **Guests selector:** the shared bordered dropdown, like the agent portal's ticket status filter.
- **Empty tables:** the message is one 47px row inside the table body (`EmptyTableRow`), for the wallet, Host requests,
  Dispute queue and Audit log tables; the Accounts list's existing message is restyled as the same row.

## 2. Deliberate differences from the mockup

| Mockup element | Decision | Why |
|---|---|---|
| Trips has no *Pending* group | Pending requests appear under **Upcoming** with a Pending pill and a Cancel button | Dropping them would hide live requests the guest can still cancel; Pending is an upcoming trip that awaits the host |
| Past shows Completed and Cancelled together | Same, cancelled rows dimmed | Matches the mockup |
| Listing detail: *House rules*, "Hosting since / host rating / responds within an hour" | Omitted; host card shows name and initials only, plus "Hosting since YYYY" from `users.createdAt` | The data model has no house rules, host rating or response time; inventing them would be fabricated content |
| Search: gradient banners | A gradient chosen deterministically from the listing id out of the mockup's six palettes | Listings have no images |
| Messages sidebar sizes (mockup 13px) | Host's existing 16/14/13 | Operator asked for "same as the host view" |
| Wallet `SGD` label previously grey (W9) | Dark brown at 80% opacity | Operator asked for dark brown text throughout the card |

## 3. Non-goals

No change to services beyond `SearchCriteria`/repository budget filtering; no schema change; Host and Agent shells
untouched apart from the shared wallet CSS and the Host message header; no new dependency.

## 4. Testing

- `SearchCriteriaTest` / `JdbcPropertyRepositoryTest`: budget filter (new + existing constructor).
- New structural/FX tests: guest trips sections collapse and expand and hold the right bookings (Pending in
  Upcoming); search card grid; listing-detail reviews toggle; review modal star selection; messages status pill
  visible for tickets only and hidden for bookings; header font sizes in CSS.
- Existing suites (`WalletUiStructureTest`, `ShellNavigationTest`, host messages tests) must stay green;
  known pre-existing failures are listed in PROJECT_STATE (§ Deviations D21, D23).
- Manual: run the app against a disposable copy of the mock DB and compare each tab with the board.
