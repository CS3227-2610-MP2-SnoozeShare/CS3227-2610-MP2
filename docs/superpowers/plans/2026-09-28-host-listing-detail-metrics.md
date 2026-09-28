# Host Listing Detail Metrics and Reviews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or native inline execution) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Host Listing Detail Reviews and Performance data-backed, calculate trailing 30-day occupancy and payout earnings, and give Performance approximately 40% of the content row.

**Architecture:** Keep `ListingMetricsService` as the UI-facing projection boundary. Expand its immutable result with review summaries, occupancy percentage, and payout earnings; implement the projection in the existing JDBC service using the current bookings, reviews, users, and wallet transaction tables. Bind the expanded result in the existing Host Detail controller without changing shell navigation or database schema.

**Tech Stack:** Java 25, JavaFX 25, JDBC/SQLite, JUnit 5, Gradle Checkstyle.

**Spec:** `docs/superpowers/specs/2026-09-28-host-listing-detail-metrics-design.md`

## Global Constraints

- Leave Host shell navigation and listing actions unchanged.
- Use the existing half-open booking date convention.
- Use the service clock for the trailing 30-day window.
- Earnings include only `BOOKING_PAYOUT` rows recorded during the window for this listing's bookings.
- Occupancy is overlapping booked nights divided by 30, capped at 100%.
- Do not add database tables or invent unavailable values.

## Review Focus

- A booking crossing either boundary contributes only its intersection with the 30-day window.
- Multiple overlapping bookings cannot produce more than 100% occupancy.
- Refunds, escrow holds, and unrelated-listing payouts do not count as earnings.
- A listing with no reviews renders an empty review state without a fabricated rating.
- Guest names and review comments remain safe when comments are empty or whitespace-only.

### Task 1: Expand the listing metrics projection

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/ListingMetrics.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/ListingMetricsServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/ListingMetricsServiceTest.java`

**Interfaces:**
- Produces an immutable `ListingMetrics` containing booking count, average rating, review summaries, occupancy percentage, and earnings amount.
- Keeps `ListingMetricsService.metricsFor(UUID)` unchanged for callers.

- [ ] **Step 1: Write failing service tests** for review projection with guest names/comments, boundary-overlap occupancy, 100% occupancy cap, payout-only earnings filtering, and empty results.
- [ ] **Step 2: Run the focused service tests** and verify they fail because the expanded projection fields/query behavior do not exist.
- [ ] **Step 3: Implement the expanded value types and JDBC projection** using prepared statements, the existing `Clock` convention, and the current schema joins.
- [ ] **Step 4: Run the focused service tests** and verify they pass.
- [ ] **Step 5: Run Checkstyle** for the changed production and test sources.

### Task 2: Bind the expanded data in Host Listing Detail

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/host/listings/HostListingDetailController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.fxml`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listing-detail.css`
- Test: `src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java`

**Interfaces:**
- Consumes the expanded `ListingMetrics` from Task 1.
- Produces the artifact-shaped Reviews rows and Performance values in the existing page.

- [ ] **Step 1: Write failing UI structure assertions** for 40% Performance sizing, review row container/labels, occupancy and earnings bindings, and artifact-aligned empty-state behavior.
- [ ] **Step 2: Run the focused Host UI tests** and verify they fail against the current fixed-width card and placeholder review/performance values.
- [ ] **Step 3: Implement controller binding, dynamic review row creation, 60/40 content sizing, and currency/percentage formatting.**
- [ ] **Step 4: Run the Host UI tests and FXML load test** and verify they pass.
- [ ] **Step 5: Run Checkstyle and `git diff --check`.**

### Task 3: Whole-suite verification and project-state handoff

**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`

- [ ] **Step 1: Run `./gradlew test`.** Record any unrelated baseline failure by exact test name.
- [ ] **Step 2: Run the app against a disposable mock DB copy** and confirm startup succeeds.
- [ ] **Step 3: Update the W6 progress/session row and add a newest-first Done ledger entry** describing the data-backed metrics and verification status.
- [ ] **Step 4: Run `git diff --check` and confirm the working tree contains only scoped changes.**

