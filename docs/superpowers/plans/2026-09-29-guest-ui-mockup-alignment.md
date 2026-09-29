# W16 — Guest UI mockup alignment (plan)

Spec: [2026-09-29-guest-ui-mockup-alignment-design.md](../specs/2026-09-29-guest-ui-mockup-alignment-design.md)

Native inline execution, one task at a time; build + focused tests after each task.

- [x] **T1 Wallet card** — `wallet.css` `.wallet-balance-*`: borderless, `#381a10`, sizes per spec. Update `WalletUiStructureTest` if it pins old values.
- [x] **T2 Messages** — CSS in `agent-theme.css` (guest) and `host-theme.css` (host): sidebar sizes, header size/padding, status-pill class sized like `.button`. FXML: pill moved to the right slot in `guest-messages.fxml` and `host-messages.fxml`; both controllers set it for ticket rows only; `GuestMessagesController` row cell shows the row pill. Tests.
- [x] **T3 Search** — `SearchCriteria.maxNightlyRate` + repository filter (+ tests); rebuild `guest-search.fxml` and `GuestSearchController` (segmented bar, 3-column grid of cards, gradient helper shared via `ui.guest.GuestVisuals`).
- [x] **T4 Trips** — rebuild `trip-dashboard.fxml` / `TripDashboardController`: collapsible Upcoming/Active/Past, new card layout, host name lookup, Message host callback wired through `GuestShellController.showMessages(bookingId)` and `GuestMessagesController.select(bookingId)`. Tests.
- [x] **T5 Listing detail** — rebuild `listing-detail.fxml` / `ListingDetailController`: breadcrumb bar, banner, two columns, DatePickers driving the price card, reviews from `ListingMetricsService`.
- [x] **T6 Review modal** — rebuild `review-dialog.fxml` / `ReviewDialogController`; the controller gets the stay summary (title, dates, host) from the caller.
- [x] **T7 Verify + record** — `.\gradlew build`; run the app on a disposable mock DB copy and compare with the boards; update PROJECT_STATE (workstream, session row, Done ledger, Deviations).

Guest CSS goes in `agent-theme.css` under `.agent-root` (the guest shell root), next to the existing guest rules, with a
`guest-` class prefix so it cannot collide with Agent screens.

All tasks done 2026-09-29; `.\gradlew build` green (512 tests). Real-app click-through left to the operator.
