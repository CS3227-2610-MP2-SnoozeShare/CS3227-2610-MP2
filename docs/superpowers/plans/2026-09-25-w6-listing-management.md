# W6 — Host Listing Management & Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the F5 host listing vertical slice: validated property creation, host-owned listing display, and Active/Inactive publishing control.

**Architecture:** Extend the existing `ListingService` boundary and inject `UserService` plus `AuditService` into `ListingServiceImpl`. Reuse `PropertyRepository` and its SQLite UPSERT, then add a role-isolated Host Listings page loaded inside the existing host shell. All behavior is driven by service tests first, followed by repository integration and JavaFX wiring tests.

**Tech Stack:** Java 25, JavaFX 25/FXML, Gradle, SQLite via plain JDBC, JUnit 5, TestFX-style headless UI tests, existing Checkstyle rules.

**Spec:** `docs/superpowers/specs/2026-09-25-w6-listing-management-design.md`

## Global Constraints

- Preserve the existing Java 25, JavaFX 25, Gradle, SQLite, and plain-JDBC stack.
- Keep `Property` as the domain record; do not add a DTO layer.
- UI code may depend on `ListingService`, `SessionContext`, and `AppContext`, never on repositories or JDBC.
- Only an active `Role.HOST` user may create or mutate a property owned by that host.
- New listings are persisted as `ListingStatus.ACTIVE`.
- Successful create and status-change mutations record exactly one audit entry; an idempotent status request is not a mutation and records none.
- Do not implement calendar blackouts, booking queues, earnings, disputes, drafts, image uploads, or moderation in W6.
- Monetary assertions against SQLite use `BigDecimal.compareTo`, never `BigDecimal.equals`.

## Review Focus

- A suspended or non-host UUID must not create or mutate listings: cover in Task 2 service authorization tests.
- A property ID supplied by another host must not be overwritten: cover in Task 2 ownership tests.
- Repeating the current status must not create an audit record: cover in Task 3 idempotency test.
- Invalid time text must be reported in the form without invoking the service: cover in Task 6 controller/UI test.
- A failed persistence or audit operation must not show a success message: cover in Task 6 error-path test.

### Task 1: Establish the service/test seams

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/ListingService.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java`
- Modify: `src/test/java/com/snoozeshare/service/ListingServiceTest.java`

**Interfaces:**
- Consumes: existing `PropertyRepository`, `AvailabilityService`, `UserService`, and `AuditService` contracts.
- Produces: `ListingService.findByHostId(UUID)` and a constructor for `ListingServiceImpl` that accepts property, availability, user, and audit dependencies.

- [ ] **Step 1: Add the host-owned query contract and constructor seam**

Add `List<Property> findByHostId(UUID hostId)` to `ListingService`. Update `ListingServiceImpl` fields and constructor to retain `PropertyRepository`/`AvailabilityService` and accept `UserService`/`AuditService`. Do not implement create/status behavior yet; leave their existing unsupported behavior until the red tests are present.

- [ ] **Step 2: Update existing listing test construction**

Update `createService(Connection)` in `ListingServiceTest` to construct `UserServiceImpl` with the existing `JdbcUserRepository`/`JdbcWalletRepository`, construct `AuditServiceImpl` with `JdbcAuditLogRepository`, and pass both dependencies to `ListingServiceImpl`. Keep all current search/detail/cost tests unchanged in intent.

- [ ] **Step 3: Run the focused test class**

Run: `./gradlew test --tests com.snoozeshare.service.ListingServiceTest`

Expected: existing tests pass after constructor wiring; no W6 mutation tests exist yet.

- [ ] **Step 4: Commit the contract seam**

Run: `git add src/main/java/com/snoozeshare/service/ListingService.java src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java src/test/java/com/snoozeshare/service/ListingServiceTest.java && git commit -m "refactor: prepare listing service for host management"`

### Task 2: Implement validated host listing creation

**Files:**
- Modify: `src/test/java/com/snoozeshare/service/ListingServiceTest.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java`
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`

**Interfaces:**
- Consumes: `ListingService.create(Property, UUID)`, `UserService.findById(UUID)`, `PropertyRepository.save(Property)`, and `AuditService.record(...)`.
- Produces: validated, active property creation with host authorization and one `LISTING_CREATED` audit row.

- [ ] **Step 1: Write the failing valid-creation test**

Add a test that registers/seeds an active host, builds a `Property` draft with a null ID and valid fields, calls `service.create(draft, hostId)`, and asserts that the returned property has a generated ID, the host ID, `ACTIVE` status, and is retrievable through `PropertyRepository.findById`.

- [ ] **Step 2: Run the test and verify the expected failure**

Run: `./gradlew test --tests com.snoozeshare.service.ListingServiceTest.validHostCanCreateActiveListing`

Expected: FAIL because `ListingServiceImpl.create` still throws `UnsupportedOperationException`.

- [ ] **Step 3: Add failing validation and authorization tests**

Add one parameterized test for blank required text, null property type/time/rate, negative rate, zero/negative capacity, negative bedrooms, and negative bathrooms. Add tests for a guest actor and a suspended host actor. Assert `IllegalArgumentException` for bad listing data and `IllegalStateException` for actor failures.

- [ ] **Step 4: Run the new tests and verify red**

Run: `./gradlew test --tests com.snoozeshare.service.ListingServiceTest`

Expected: the new tests fail because creation remains unimplemented.

- [ ] **Step 5: Implement minimal creation behavior**

In `ListingServiceImpl.create`:

```java
User host = requireActiveHost(hostId);
validateDraft(draft);
Property saved = new Property(
        draft.propertyId() == null ? UUID.randomUUID() : draft.propertyId(),
        host.userId(), ListingStatus.ACTIVE, draft.title(), draft.description(),
        draft.propertyType(), draft.streetAddress(), draft.city(), draft.region(),
        draft.postalCode(), draft.maxGuests(), draft.bedrooms(), draft.bathrooms(),
        draft.baseNightlyRate(), draft.checkInTime(), draft.checkOutTime(),
        draft.amenities(), draft.createdAt() == null ? Instant.now() : draft.createdAt());
Property persisted = properties.save(saved);
audit.record(host.userId(), "LISTING_CREATED", "PROPERTY", persisted.propertyId(), null, persisted);
return persisted;
```

Use `DomainValidation` for text and numeric checks. If a non-null draft ID is already owned by another host, reject it before saving; if it belongs to the same host, reject it as an attempted update through the create operation. Add `requireActiveHost` using `UserService.findById`, `AuthorizationService.requireRole(Role.HOST, host)`, and an `AccountStatus.ACTIVE` check.

- [ ] **Step 6: Wire the existing application context**

Construct `ListingServiceImpl` with `userService` and `auditService` in `AppContext`, preserving the existing availability dependency.

- [ ] **Step 7: Run the focused and full service suite**

Run: `./gradlew test --tests com.snoozeshare.service.ListingServiceTest`

Expected: PASS.

Run: `./gradlew test`

Expected: PASS; report any unrelated pre-existing failures by test name.

- [ ] **Step 8: Commit creation behavior**

Run: `git add src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java src/main/java/com/snoozeshare/app/AppContext.java src/test/java/com/snoozeshare/service/ListingServiceTest.java && git commit -m "feat: create validated host listings"`

### Task 3: Implement host-owned status control and audit behavior

**Files:**
- Modify: `src/test/java/com/snoozeshare/service/ListingServiceTest.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java`

**Interfaces:**
- Consumes: `ListingService.updateStatus(UUID, ListingStatus, UUID)`, `PropertyRepository.findById/save`, and `AuditService.record`.
- Produces: authorized Active/Inactive transitions, idempotency, ownership protection, and `findByHostId`.

- [ ] **Step 1: Write failing status and query tests**

Add tests that an owning active host can deactivate and reactivate a property; the returned and persisted records contain the requested status; the audit query contains `LISTING_STATUS_CHANGED` with before and after states; a second request for the existing status returns unchanged and adds no audit entry; another host receives `IllegalStateException`; a missing property receives `IllegalArgumentException`; and `findByHostId` returns only the selected host's properties.

- [ ] **Step 2: Run the tests and verify red**

Run: `./gradlew test --tests com.snoozeshare.service.ListingServiceTest`

Expected: status tests fail because `updateStatus` is still unsupported and `findByHostId` has no implementation.

- [ ] **Step 3: Implement the minimal status/query behavior**

Implement `findByHostId` as a direct repository delegation. Implement `updateStatus` with active-host validation, target-status validation, property lookup, owner comparison, idempotent early return, `new Property(...)` with only status changed, repository save, and one audit record.

- [ ] **Step 4: Run the focused and full suite**

Run: `./gradlew test --tests com.snoozeshare.service.ListingServiceTest && ./gradlew test`

Expected: PASS.

- [ ] **Step 5: Commit status control**

Run: `git add src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java src/test/java/com/snoozeshare/service/ListingServiceTest.java && git commit -m "feat: manage host listing status"`

### Task 4: Verify and harden JDBC property persistence

**Files:**
- Modify: `src/test/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepositoryTest.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepository.java` only if a test exposes a W6-specific defect.

**Interfaces:**
- Consumes: existing `PropertyRepository.save`, `findById`, and `findByHostId`.
- Produces: regression coverage for all W6 listing fields and status persistence.

- [ ] **Step 1: Add a round-trip test for a newly created property**

Persist a property with non-empty amenities, `ACTIVE` status, decimal rate, and all address/time fields; load it by ID and assert every field, using `compareTo` for the rate.

- [ ] **Step 2: Add a status-update persistence test**

Save an Active property, save a copy with `INACTIVE`, then load it and assert the status is `INACTIVE` while the host ID and listing fields remain unchanged.

- [ ] **Step 3: Run the integration tests**

Run: `./gradlew test --tests com.snoozeshare.repository.jdbc.JdbcPropertyRepositoryTest`

Expected: PASS. If production changes are needed, add only the smallest JDBC fix required by the failing test.

- [ ] **Step 4: Commit persistence coverage**

Run: `git add src/test/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepositoryTest.java src/main/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepository.java && git commit -m "test: cover host listing persistence"`

### Task 5: Add the Host Listings page structure and navigation

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/host/HostShellController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/host/host-shell.fxml`
- Create: `src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java`
- Create: `src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml`
- Modify: `src/test/java/com/snoozeshare/ui/ShellNavigationTest.java`
- Modify: `src/test/java/com/snoozeshare/ui/ShellLayoutTest.java` only if the new content container changes shell layout assertions.

**Interfaces:**
- Consumes: `AppContext.listingService()`, `SessionContext.currentUser()`, and the existing `NavShellController` shell lifecycle.
- Produces: a Listings navigation target with host-only listing content and a controller that can be loaded independently for TestFX.

- [ ] **Step 1: Add structural UI tests**

Assert that the host shell has an `fx:id` content container, the Listings navigation calls `showListings`, the new FXML names `HostListingsController`, and the page includes create-form controls, listing-card container, status/error labels, and a reloadable action surface.

- [ ] **Step 2: Run the UI tests and verify red**

Run: `./gradlew test --tests com.snoozeshare.ui.ShellNavigationTest --tests com.snoozeshare.ui.ShellLayoutTest`

Expected: FAIL because the shell still renders only a static message and no listing page exists.

- [ ] **Step 3: Implement the shell content container**

Change the host shell center from the static message-only VBox to a `StackPane`/`VBox` with an `fx:id` content container. In `HostShellController.showListings`, load `host-listings.fxml` with `FXMLLoader`, inject the shared `AppContext`, and replace the content container children. Keep Dashboard and Bookings behavior unchanged.

- [ ] **Step 4: Implement the initial Listings FXML/controller skeleton**

Create the page with a form, labels, a listing-card `FlowPane`/`VBox`, and buttons. `HostListingsController.setContext(AppContext)` stores the context and calls `reload()`. `reload()` reads the current host UUID and calls `findByHostId`, rendering an empty state when no properties exist.

- [ ] **Step 5: Run structural and application tests**

Run: `./gradlew test --tests com.snoozeshare.ui.ShellNavigationTest --tests com.snoozeshare.ui.ShellLayoutTest --tests com.snoozeshare.app.SceneRouterTest`

Expected: PASS.

- [ ] **Step 6: Commit the page structure**

Run: `git add src/main/java/com/snoozeshare/ui/host/HostShellController.java src/main/resources/com/snoozeshare/ui/host/host-shell.fxml src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml src/test/java/com/snoozeshare/ui/ShellNavigationTest.java src/test/java/com/snoozeshare/ui/ShellLayoutTest.java && git commit -m "feat: add host listings page"`

### Task 6: Complete create form, cards, status toggles, and UI error handling

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml`
- Modify: `src/main/resources/com/snoozeshare/ui/common/theme.css`
- Modify: `src/test/java/com/snoozeshare/ui/ShellNavigationTest.java` or create `src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java` for focused controller behavior.

**Interfaces:**
- Consumes: `ListingService.create`, `ListingService.updateStatus`, `ListingService.findByHostId`, and existing JavaFX event handlers.
- Produces: usable create/toggle flow with inline errors, success feedback, and post-mutation reload.

- [ ] **Step 1: Write failing controller/UI tests**

Cover: valid form submission creates a listing and refreshes the cards; blank required fields, invalid number text, and invalid `HH:mm` text show an error without calling the mutation path; a card toggle calls `updateStatus` with the opposite status and reloads; service exceptions show an error and do not show success.

- [ ] **Step 2: Run the tests and verify red**

Run: `./gradlew test --tests com.snoozeshare.ui.HostListingsControllerTest`

Expected: FAIL because form handlers and card rendering are not implemented.

- [ ] **Step 3: Implement parsing and form submission**

Parse text fields into `Property` values using `Integer.parseInt`, `Double.parseDouble`, `new BigDecimal`, `LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))`, and the selected `PropertyType`/amenities. Pass a null property ID and `Instant.now()` to `ListingService.create`; catch `IllegalArgumentException`/`IllegalStateException`, set the error label, and leave the form/card state unchanged on failure.

- [ ] **Step 4: Implement card rendering and status toggle**

Render title, city/type, rate to two decimals, capacity, and an Active/Inactive pill. The toggle button calls `updateStatus(propertyId, oppositeStatus, currentHostId)`, then reloads. Disable or label the button while the request is executing on the JavaFX thread to prevent duplicate clicks.

- [ ] **Step 5: Add focused styles and accessible labels**

Add only listing-specific CSS classes for cards, status pills, form errors, empty state, and compact metadata. Give each action button a descriptive text label; do not alter global shell spacing or palette.

- [ ] **Step 6: Run focused, full, and packaging verification**

Run: `./gradlew test --tests com.snoozeshare.ui.HostListingsControllerTest`

Expected: PASS.

Run: `./gradlew test`

Expected: PASS.

Run: `./gradlew build`

Expected: PASS with no Checkstyle violations.

- [ ] **Step 7: Commit the completed UI flow**

Run: `git add src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml src/main/resources/com/snoozeshare/ui/common/theme.css src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java && git commit -m "feat: complete host listing management UI"`

### Task 7: Whole-branch verification and project-state handoff

**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`

**Interfaces:**
- Consumes: all W6 implementation tasks and their test evidence.
- Produces: an auditable Done/verification record and a clean handoff for review.

- [ ] **Step 1: Run the full verification commands**

Run:

```bash
./gradlew clean test
./gradlew build
git diff --check
git status --short --branch
```

Expected: all tests/build checks pass, diff check is empty, and only intentional W6 documentation changes remain uncommitted before the final state update.

- [ ] **Step 2: Review the complete diff**

Run: `git diff main...HEAD --stat && git diff main...HEAD -- src/main/java src/main/resources src/test/java`

Confirm that no W7/W8 behavior, unrelated refactor, new dependency, or repository/UI boundary violation entered the branch.

- [ ] **Step 3: Update the living project state**

Set W6 to `In review`, record the completed implementation and verification evidence in the W6 Progress cell, mark session S4 as ready for review, and add a newest-first Done ledger entry listing the service, persistence, wiring, UI, and tests. Leave the Developer Guide as `—` until the operator confirms the workstream and proposes a documentation checkpoint.

- [ ] **Step 4: Commit the handoff record**

Run: `git add PROJECT_STATE.md docs/project-state/done-ledger.md && git commit -m "docs: record W6 listing management completion"`

