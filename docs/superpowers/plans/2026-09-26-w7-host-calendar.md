# W7 Host Calendar & Date Overrides Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax (`[ ]`) for tracking.

**Goal:** Build the host listing-specific booking calendar with month navigation, booked/blocked/available rendering, manual date blocking, all-month override display, and manual-block removal.

**Architecture:** Extend the existing availability block record and JDBC adapter with an optional manual-block reason and deletion by ID. Put host ownership, date-range, booking-overlap, and block-overlap rules in AvailabilityServiceImpl. Add a JavaFX HostCalendarController loaded by HostShellController; keep calendar state and form mutations in that controller while HostListingsController supplies the listing-specific entry callback.

**Tech Stack:** Java 25, JavaFX 25/FXML, SQLite via plain JDBC, Gradle, JUnit 5, source/FXML UI tests, existing CSS.

**Spec:** docs/superpowers/specs/2026-09-26-w7-host-calendar-design.md

## Global Constraints

- Use the existing half-open range convention: from inclusive and to exclusive.
- Only the host who owns the selected property may create or remove manual blocks.
- Reject manual blocks overlapping PENDING/CONFIRMED bookings or any existing availability block.
- The override list shows every HOST_BLOCK for the listing, independent of the displayed calendar month.
- Booking-sourced blocks are read-only and visually take precedence over manual blocks.
- UI controllers depend on services and domain records, never repositories or JDBC.
- Do not add a third-party calendar dependency.
- Use TDD: write each failing test, run it red, implement the smallest green change, then run the relevant suite.

## Review Focus

- Half-open boundary dates: a block ending on the same date another starts must not overlap; test in Task 2.
- Unauthorized host/property access: create and remove must fail without changing persistence; test in Task 2.
- Booking overlap: pending and confirmed bookings reject manual blocks, while terminal booking states do not; test in Task 2.
- All-month override visibility: navigating the calendar must not filter the override list; test in Task 4.
- Booking-vs-manual precedence and adjacent-month styling: booked cells stay green and adjacent cells stay grey; test in Task 4.

---

### Task 1: Extend availability-block persistence with reasons and deletion

**Files:**
- Modify: src/main/java/com/snoozeshare/domain/model/AvailabilityBlock.java
- Modify: src/main/java/com/snoozeshare/repository/AvailabilityBlockRepository.java
- Modify: src/main/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepository.java
- Modify: src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java
- Modify: src/main/resources/db/migration/V001__foundation.sql
- Modify: db/schema.sql
- Modify: db/seed-mock-data.sql
- Modify: every existing constructor call found by rg -n "new AvailabilityBlock" src/main src/test
- Test: src/test/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepositoryTest.java

**Interfaces:**
- Consumes: existing AvailabilityBlock fields and JDBC codecs.
- Produces: AvailabilityBlock(..., String source, UUID bookingId, String reason), deleteById(UUID blockId), and JDBC reason round-trip behavior for later service/UI tasks.

- [ ] Step 1: Write failing repository tests

Add tests that save a HOST_BLOCK with a whitespace-padded maintenance reason and assert the loaded record contains it, save a booking block with null reason and assert it round-trips, and delete one of two blocks while asserting the other remains.

- [ ] Step 2: Run the focused repository test to verify it fails

Run: ./gradlew test --tests com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepositoryTest

Expected: FAIL because AvailabilityBlock has no reason field and the repository has no deleteById implementation.

- [ ] Step 3: Implement the persistence seam

Add reason to the record and repository contract. Update the JDBC INSERT, row mapper, and add this method using DELETE FROM availability_blocks WHERE blockId = ?:

    void deleteById(UUID blockId);

Add nullable reason TEXT to the migration and reference schema, and add NULL to seeded booking/manual rows where needed. Update all record construction sites without changing booking overlap SQL.

- [ ] Step 4: Run the focused repository test to verify it passes

Run: ./gradlew test --tests com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepositoryTest

Expected: PASS, including reason round-trip and targeted deletion.

- [ ] Step 5: Run existing availability and booking tests

Run: ./gradlew test --tests com.snoozeshare.service.AvailabilityServiceTest --tests com.snoozeshare.service.BookingServiceTest

Expected: PASS with existing booking-created blocks using null reason.

- [ ] Step 6: Commit the persistence slice

    git add src/main/java/com/snoozeshare/domain/model/AvailabilityBlock.java src/main/java/com/snoozeshare/repository/AvailabilityBlockRepository.java src/main/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepository.java src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java src/main/resources/db/migration/V001__foundation.sql db/schema.sql db/seed-mock-data.sql src/test/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepositoryTest.java
    git commit -m "feat: persist host availability override reasons"

### Task 2: Implement host manual-block creation and removal rules

**Files:**
- Modify: src/main/java/com/snoozeshare/service/AvailabilityService.java
- Modify: src/main/java/com/snoozeshare/service/impl/AvailabilityServiceImpl.java
- Test: src/test/java/com/snoozeshare/service/AvailabilityServiceTest.java

**Interfaces:**
- Consumes: PropertyRepository, AvailabilityBlockRepository, BookingRepository, and existing Property.hostId()/booking overlap behavior.
- Produces: createHostBlock(UUID propertyId, LocalDate start, LocalDate end, UUID hostId, String reason), removeHostBlock(UUID blockId, UUID hostId), and unchanged blocksFor(UUID propertyId).

- [ ] Step 1: Write failing service tests

Add tests for invalid start >= end; non-owner creation; pending-booking overlap; confirmed-booking overlap; terminal-booking non-overlap; existing-block overlap; successful creation with trimmed reason; blank reason becoming null; authorized removal; rejection of non-owner removal; rejection of booking-block removal; and unknown block removal.

Use real migrated SQLite repositories and records. Assert the database contains no new/deleted block after rejected operations.

- [ ] Step 2: Run the focused service tests to verify they fail

Run: ./gradlew test --tests com.snoozeshare.service.AvailabilityServiceTest

Expected: FAIL because creation is still unsupported and removal is not defined.

- [ ] Step 3: Implement creation and removal minimally

Add the new service methods. For creation, validate dates, find the property, compare hostId, check bookings.findOverlapping(propertyId, start, end) and blocks.findOverlapping(...), normalize reason with trim() and convert empty text to null, then save a generated AvailabilityBlock with source HOST_BLOCK and bookingId == null. For removal, load the block, require source HOST_BLOCK, load its property, verify the host, then call deleteById.

- [ ] Step 4: Run the focused service tests to verify they pass

Run: ./gradlew test --tests com.snoozeshare.service.AvailabilityServiceTest

Expected: PASS for all creation/removal rules and existing availability tests.

- [ ] Step 5: Run the full service suite

Run: ./gradlew test --tests 'com.snoozeshare.service.*'

Expected: PASS with no regressions to listing or booking behavior.

- [ ] Step 6: Commit the service slice

    git add src/main/java/com/snoozeshare/service/AvailabilityService.java src/main/java/com/snoozeshare/service/impl/AvailabilityServiceImpl.java src/test/java/com/snoozeshare/service/AvailabilityServiceTest.java
    git commit -m "feat: manage host availability overrides"

### Task 3: Add listing-card calendar entry and shell navigation

**Files:**
- Modify: src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java
- Modify: src/main/java/com/snoozeshare/ui/host/HostShellController.java
- Modify: src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java
- Modify: src/test/java/com/snoozeshare/ui/HostShellControllerTest.java if present; otherwise create it

**Interfaces:**
- Consumes: HostListingsController property-card construction and the shell's existing shellRoot navigation.
- Produces: setOnOpenCalendar(Consumer<Property>) and showCalendar(Property) loading host-calendar.fxml with the selected Property and current AppContext.

- [ ] Step 1: Write failing UI wiring tests

Assert that the listings controller exposes setOnOpenCalendar, creates an Open booking calendar button per listing card, consumes that button's click event, and that the shell loads host-calendar.fxml while injecting context/property/back callback.

- [ ] Step 2: Run the focused UI tests to verify they fail

Run: ./gradlew test --tests com.snoozeshare.ui.HostListingsControllerTest --tests com.snoozeshare.ui.HostShellControllerTest

Expected: FAIL because the callback, button, and shell route do not exist.

- [ ] Step 3: Implement callback and navigation

Add the callback field/setter, create the button beside status/edit controls, use an event filter to stop card detail navigation, and wire HostShellController.showCalendar(Property) through FXMLLoader. Inject setContext, setProperty, and setOnBack(this::showListings) into the calendar controller.

- [ ] Step 4: Run the focused UI tests to verify they pass

Run: ./gradlew test --tests com.snoozeshare.ui.HostListingsControllerTest --tests com.snoozeshare.ui.HostShellControllerTest

Expected: PASS.

- [ ] Step 5: Commit the navigation slice

    git add src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java src/main/java/com/snoozeshare/ui/host/HostShellController.java src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java src/test/java/com/snoozeshare/ui/HostShellControllerTest.java
    git commit -m "feat: open listing calendars from host listings"

### Task 4: Build the calendar page and month-state renderer

**Files:**
- Create: src/main/java/com/snoozeshare/ui/host/calendar/HostCalendarController.java
- Create: src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml
- Create: src/test/java/com/snoozeshare/ui/HostCalendarControllerTest.java
- Modify: src/main/java/com/snoozeshare/ui/host/calendar/package-info.java

**Interfaces:**
- Consumes: AppContext.availabilityService().blocksFor(propertyId), selected Property, and LocalDate.
- Produces: controller setters setContext(AppContext), setProperty(Property), setOnBack(Runnable), plus FXML handlers handlePreviousMonth, handleNextMonth, handleBack, and refresh/render methods used by later mutation steps.

- [ ] Step 1: Write failing renderer/FXML tests

Assert the FXML contains previous/next buttons, month/year label, legend labels for Available/Booked/Blocked/Other month, calendar grid, From/To/Reason controls, Current overrides container, and Back action. Assert controller source contains month navigation, blocksFor, all-month HOST_BLOCK filtering, booking-first cell classification, and adjacent-month styling.

- [ ] Step 2: Run the focused UI test to verify it fails

Run: ./gradlew test --tests com.snoozeshare.ui.HostCalendarControllerTest

Expected: FAIL because the controller and FXML do not exist.

- [ ] Step 3: Implement the page shell and renderer

Create a BorderPane/HBox layout where the calendar occupies the majority of the center/left area and the right pane contains the form plus overrides. Use a GridPane with seven columns and complete weeks. Track YearMonth displayedMonth, rebuild the grid on navigation, and create cells from LocalDate values. Apply style classes calendar-cell, calendar-cell-booked, calendar-cell-blocked, and calendar-cell-other-month; keep adjacent cells grey and non-editable. Use booking blocks first, then host blocks, then available.

Render the override list from all blocksFor(propertyId) results filtered only to HOST_BLOCK, sorted by start date, with start/end labels and a right-aligned Remove text control placeholder wired to the controller method added in Task 5.

- [ ] Step 4: Run the focused UI test to verify it passes

Run: ./gradlew test --tests com.snoozeshare.ui.HostCalendarControllerTest

Expected: PASS for page structure and renderer wiring.

- [ ] Step 5: Commit the calendar renderer slice

    git add src/main/java/com/snoozeshare/ui/host/calendar src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml src/test/java/com/snoozeshare/ui/HostCalendarControllerTest.java
    git commit -m "feat: render host booking calendars"

### Task 5: Add blocking form, override removal, and visual styling

**Files:**
- Modify: src/main/java/com/snoozeshare/ui/host/calendar/HostCalendarController.java
- Modify: src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml
- Modify: src/main/resources/com/snoozeshare/ui/common/theme.css
- Modify: src/test/java/com/snoozeshare/ui/HostCalendarControllerTest.java

**Interfaces:**
- Consumes: AvailabilityService.createHostBlock(...) and removeHostBlock(...) from Task 2, current session host ID, and all-month block list from Task 4.
- Produces: form submission and remove actions that refresh calendar and override list without losing input on validation failure.

- [ ] Step 1: Write failing mutation/UI tests

Assert the controller source calls the five-argument createHostBlock, reads From/To/Reason, preserves input on exception, calls removeHostBlock, and reloads all overrides after mutation. Assert CSS defines white/green/red/grey calendar states, top-right legend layout, large calendar layout, and right-aligned remove text styling.

- [ ] Step 2: Run the focused UI test to verify it fails

Run: ./gradlew test --tests com.snoozeshare.ui.HostCalendarControllerTest

Expected: FAIL because mutation handlers and W7 styles are incomplete.

- [ ] Step 3: Implement form and removal behavior

Parse ISO dates from the two date inputs, call the service with the selected property and current host, show the exception message in the form status label, and clear fields only after success. Render each override's Remove text as a button styled like a link; call removeHostBlock with the current host and refresh both blocks and override rows. Keep booking blocks out of the override list and provide no remove control for them.

- [ ] Step 4: Implement CSS and accessibility labels

Add layout styles that give the calendar most available width, keep the form/override panel on the right, place the legend top-right and month controls top-left, and define the four requested cell colors. Add accessible text for navigation, block submission, and removal actions.

- [ ] Step 5: Run the focused UI tests to verify they pass

Run: ./gradlew test --tests com.snoozeshare.ui.HostCalendarControllerTest --tests com.snoozeshare.ui.HostListingsControllerTest

Expected: PASS.

- [ ] Step 6: Commit the mutation and styling slice

    git add src/main/java/com/snoozeshare/ui/host/calendar/HostCalendarController.java src/main/resources/com/snoozeshare/ui/host/calendar/host-calendar.fxml src/main/resources/com/snoozeshare/ui/common/theme.css src/test/java/com/snoozeshare/ui/HostCalendarControllerTest.java src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java
    git commit -m "feat: add host calendar overrides UI"

### Task 6: Integrate, verify, and record W7 completion

**Files:**
- Modify: PROJECT_STATE.md
- Modify: docs/project-state/done-ledger.md
- Modify: docs/superpowers/specs/2026-09-26-w7-host-calendar-design.md only if implementation makes a documented deviation

- [ ] Step 1: Run the complete test suite

Run: ./gradlew test

Expected: PASS for all existing and W7 tests.

- [ ] Step 2: Run the complete build and checkstyle

Run: ./gradlew build

Expected: PASS with no new checkstyle violations.

- [ ] Step 3: Inspect the final diff and verify scope

Run: git diff w6...HEAD --stat and git status --short. Confirm no W8 request-queue behavior, third-party dependency, or unrelated refactor entered the branch.

- [ ] Step 4: Update project state and Done ledger

Set W7 to In review, link this plan, summarize the implemented calendar/override behavior in its Progress cell, update the S8 row, and add a newest-first Done ledger entry with test/build evidence. Set the Developer Guide value to Awaiting confirmation and ask the operator whether to confirm the feature checkpoint before proposing guide changes.

- [ ] Step 5: Commit the verification record

    git add PROJECT_STATE.md docs/project-state/done-ledger.md
    git commit -m "docs: record W7 calendar completion"
