# Host Listing Pages Artifact Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or superpowers:subagent-driven-development) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the native Host Listings, New/Edit Listing, and Booking Calendar pages with the approved artifact while preserving existing Host behavior and navigation.

**Architecture:** Keep the Host shell and `host-theme.css` responsible for shared Host chrome and palette tokens. Add page-owned stylesheets for Listings, Listing Form, and Calendar; update each FXML to attach its own stylesheet and use narrowly scoped page classes. Preserve the existing controllers and service boundaries, changing Java code only where dynamic controls need page-specific classes or layout sizing.

**Tech Stack:** Java 25, JavaFX 25, FXML, CSS, JUnit 5, Gradle Checkstyle, SQLite mock DB.

**Spec:** `docs/superpowers/specs/2026-09-28-host-listing-pages-artifact-design.md`

## Global Constraints

- Keep the existing Host top bar and navigation strip unchanged.
- No page stylesheet may import or depend on `agent-theme.css` or Agent-only selectors.
- Preserve existing listing, form, breadcrumb, calendar, date-blocking, and override-removal behavior.
- Do not hardcode artifact sample listings or dates.
- Keep the Booking Calendar right pane fixed at 300px and keep Save/Cancel left-aligned.
- Use artifact colors, spacing, typography, borders, radii, shadows, and control proportions from the approved spec.

## Review Focus

- Dynamic listing action controls must not bubble into listing-detail navigation; cover with the existing controller regression and explicit event-consumption assertions.
- The New/Edit form must retain all `fx:id` fields and both create/edit breadcrumb/title states; cover both FXML structure and controller population tests.
- The form must remain usable at the desktop window height without clipped actions; cover scroll container, grid, and left-aligned action-row structure.
- Calendar cells must retain available/booked/blocked/other-month classes and date semantics; cover existing calendar rendering tests plus the new page stylesheet/FXML assertions.
- Host page FXML must not reference Agent styling; cover all three FXML files and their stylesheet declarations.

### Task 1: Align Host Listings page

**Files:**
- Create: `src/main/resources/com/snoozeshare/ui/host/listings/host-listings.css`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml`
- Modify: `src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java`
- Modify: `src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java`

**Interfaces:**
- Consumes existing `ListingService`, `ListingMetricsService`, navigation callbacks, and dynamic listing row creation.
- Produces an artifact-shaped dynamic listing row with the existing action callbacks and style classes.

- [ ] **Step 1: Add failing structure assertions** for the page-owned stylesheet, artifact listing-page root/title/action classes, 64px image placeholder, metric/action style classes, and absence of Agent stylesheet references.
- [ ] **Step 2: Run the focused Host Listings tests** and verify they fail against the current generic FXML and style ownership.
- [ ] **Step 3: Add `host-listings.css`** with scoped page rules for the page container, title/action row, listing row card, 64px placeholder, identity text, metric groups, active switch/status label, and outline actions using the approved artifact tokens.
- [ ] **Step 4: Update `host-listings.fxml`** to attach the page stylesheet and apply page-specific structural classes without changing controller callbacks or shell navigation.
- [ ] **Step 5: Update `HostListingsController.createCard(Property)`** to use the artifact row dimensions/classes, keep action event filters, and preserve dynamic metrics/status behavior.
- [ ] **Step 6: Run the focused Host Listings tests and Checkstyle** and verify they pass.

### Task 2: Align New/Edit Listing form

**Files:**
- Create: `src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.css`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml`
- Modify: `src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java`
- Modify: `src/test/java/com/snoozeshare/ui/HostBreadcrumbNavigationTest.java`

**Interfaces:**
- Consumes the existing `HostListingFormController` field IDs, callbacks, validation, and create/edit state transitions.
- Produces a two-column, four-card form with artifact-aligned controls and unchanged persistence behavior.

- [ ] **Step 1: Add failing FXML assertions** for the page-owned stylesheet, two-column grid, four card sections, two-column amenities layout, left-aligned action row, retained form title, and all required field IDs.
- [ ] **Step 2: Run the focused form/breadcrumb tests** and verify the current structure/style ownership does not satisfy the artifact contract.
- [ ] **Step 3: Add `host-listing-form.css`** for the scroll page, breadcrumb/title hierarchy, card surfaces, labels, inputs, combo boxes, amenity tiles, error text, and left-aligned actions.
- [ ] **Step 4: Update `host-listing-form.fxml`** to attach the stylesheet, use artifact spacing/card classes, keep Create/Edit breadcrumb and title bindings, and retain the scroll container so actions remain reachable at the target window height.
- [ ] **Step 5: Run focused form, breadcrumb, and FXML-load tests** and verify create/edit field wiring remains intact.
- [ ] **Step 6: Run Checkstyle and `git diff --check`** for the task.

### Task 3: Align Booking Calendar page

**Files:**
- Create: `src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.css`
- Modify: `src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml`
- Modify: `src/test/java/com/snoozeshare/ui/HostCalendarControllerTest.java`
- Modify: `src/test/java/com/snoozeshare/ui/HostBreadcrumbNavigationTest.java`

**Interfaces:**
- Consumes the existing `HostCalendarController` calendar rendering, block-date form, breadcrumbs, and override actions.
- Produces a flexible calendar pane and fixed 300px block/override sidebar with artifact-aligned state classes.

- [ ] **Step 1: Add failing structure assertions** for the page-owned stylesheet, split-pane layout, fixed 300px side panel, month controls, legend, calendar grid, block form, and override section.
- [ ] **Step 2: Run the focused calendar tests** and verify the current page lacks the required page-owned structure/style contract.
- [ ] **Step 3: Add `host-calendar.css`** for the horizontal split, compact breadcrumb/month header, legend swatches, seven-column grid, day states, side-panel form, danger action, and override rows.
- [ ] **Step 4: Update `host-calendar.fxml`** to attach the stylesheet, use the artifact pane hierarchy, preserve the `calendarGrid`, `overridesContainer`, and form IDs, and keep the fixed sidebar width.
- [ ] **Step 5: Run calendar rendering, breadcrumb, and FXML-load tests** and verify existing date behavior remains unchanged.
- [ ] **Step 6: Run Checkstyle and `git diff --check`** for the task.

### Task 4: Whole-branch verification and handoff

**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`

- [ ] **Step 1: Run focused UI tests** for Listings, form, breadcrumbs, and calendar, then run Checkstyle.
- [ ] **Step 2: Run the full Gradle test suite** and record any unrelated baseline failure by exact test name.
- [ ] **Step 3: Start the app against a disposable mock DB copy** using `SNOOZESHARE_DB_URL` and visually inspect Listings, New/Edit Listing, and Booking Calendar with a Host account.
- [ ] **Step 4: Verify Host page FXML/CSS references** contain no `agent-theme.css` or Agent-only selectors.
- [ ] **Step 5: Update `PROJECT_STATE.md`** with completed task status, verification result, session handoff, and any deviations; add a newest-first Done ledger entry.
- [ ] **Step 6: Run `git diff --check` and confirm only scoped changes remain.**
