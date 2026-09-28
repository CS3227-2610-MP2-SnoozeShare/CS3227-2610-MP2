# Host Listing Validation and Numeric Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or superpowers:subagent-driven-development) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert bathrooms and postal codes to integer property fields and make Host listing validation and controls clear, ordered, and artifact-consistent.

**Architecture:** Update the `Property` value contract and JDBC/schema boundary first, then rebuild the committed mock database. Update service validation and the Host form controller as the single UI parsing/ordering boundary, and add Host-owned CSS/controller structure for the Agent-style toggle and amenity options. Existing listing service APIs and navigation callbacks remain unchanged.

**Tech Stack:** Java 25, JavaFX 25, JDBC/SQLite, FXML/CSS, JUnit 5, Gradle Checkstyle.

**Spec:** `docs/superpowers/specs/2026-09-28-host-listing-validation-and-numeric-fields-design.md`

## Global Constraints

- `Property` uses `int postalCode` and `int bathrooms`.
- `properties.postalCode` and `properties.bathrooms` are SQLite `INTEGER` columns.
- Existing mock postal values are converted to numeric equivalents and the committed mock DB is rebuilt.
- Validation order is Basic Details → Capacity & Pricing → Location → Amenities.
- Amenities are optional and empty selection is valid.
- Host styling must remain Host-owned and must not add Agent stylesheet dependencies.
- Preserve existing listing persistence, ownership, status-toggle callbacks, and breadcrumbs.

## Review Focus

- Existing seed postal values include letters and leading zeroes; every row must become a valid integer without breaking foreign keys or repository loading.
- Blank bathroom and rate input must produce field-specific messages instead of `empty String` or an uncaught `NumberFormatException`.
- A form containing multiple invalid sections must report the first error in the specified section order.
- The active toggle must visibly move a white knob between left and right states, not merely change the track color.
- Empty amenities must save successfully while the Optional subtitle and Agent-style option treatment remain visible.

### Task 1: Migrate numeric property fields and mock DB

**Files:**
- Modify: `src/main/java/com/snoozeshare/domain/model/Property.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepository.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java`
- Modify: `db/schema.sql`
- Modify: `db/seed-mock-data.sql`
- Modify: `db/snoozeshare-mock.db`
- Modify: affected repository/service/UI tests and property fixtures

**Interfaces:**
- Produces `Property(..., int postalCode, int bathrooms, ...)`.
- JDBC property round trips bind/read both fields with integer accessors.

- [ ] **Step 1: Add failing repository/service tests** asserting integer bathroom and postal-code round trips and numeric mock values.
- [ ] **Step 2: Run the focused repository/service tests** and verify they fail against the current `String`/`double` contract or schema.
- [ ] **Step 3: Update the domain, JDBC mapping/binding, schema, seed values, and all compile-time fixtures** to use integers.
- [ ] **Step 4: Rebuild `db/snoozeshare-mock.db`** from `db/schema.sql` and `db/seed-mock-data.sql`; run foreign-key and numeric-column checks.
- [ ] **Step 5: Run focused repository/service tests** and verify integer round trips and mock loading pass.

### Task 2: Implement ordered friendly validation

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/host/listings/HostListingFormController.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java`
- Modify: `src/main/java/com/snoozeshare/domain/validation/DomainValidation.java` only if a reusable friendly numeric helper is needed
- Test: `src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java`
- Test: `src/test/java/com/snoozeshare/service/ListingServiceTest.java`

**Interfaces:**
- Form parsing returns integer bathrooms/postal codes and throws friendly `IllegalArgumentException` messages.
- Listing service rejects invalid `Property` values with friendly field names and the same section order.

- [ ] **Step 1: Add failing controller/service tests** for blank/malformed bathroom, missing/negative rate, numeric postal validation, friendly Street Address/Postal Code/Bathrooms/Rate per night messages, and cross-section ordering.
- [ ] **Step 2: Run focused tests** and verify current parsing produces `empty String`, uncaught number-format errors, or the wrong validation order.
- [ ] **Step 3: Implement section-ordered form parsing**: Basic Details, Capacity & Pricing, Location, then optional Amenities; use explicit field-name helpers for integers, decimal rate, numeric postal code, and times.
- [ ] **Step 4: Align service validation order and friendly labels** so non-UI callers receive equivalent errors.
- [ ] **Step 5: Run focused controller/service tests** and verify all invalid cases report the first expected friendly error.

### Task 3: Update Host form structure, placeholders, and controls

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/host/listings/HostListingsController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listings.fxml`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listings.css`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.fxml`
- Modify: `src/main/resources/com/snoozeshare/ui/host/listings/host-listing-form.css`
- Test: `src/test/java/com/snoozeshare/ui/HostListingsControllerTest.java`

**Interfaces:**
- Host Listings status toggle uses a graphic white knob and selected-state alignment.
- Host form keeps all existing field IDs and callbacks while exposing improved placeholders and optional amenity styling.

- [ ] **Step 1: Add failing UI structure/style assertions** for the toggle graphic/alignment classes, Optional amenity subtitle, Agent-style amenity option classes, helpful placeholders, compact label/input spacing, content-sized cards, and reduced action gap.
- [ ] **Step 2: Run the focused Host UI tests** and verify the current controls/placeholders/spacing fail the new contract.
- [ ] **Step 3: Update `HostListingsController`** to create a white knob graphic and selected alignment on the active toggle; keep status persistence and feedback behavior unchanged.
- [ ] **Step 4: Update form FXML/CSS** with helpful placeholders, integer-oriented bathroom/postal prompts, `Optional` amenity subtitle, Agent-style checkbox tiles, tighter label/input spacing, content-driven cards, and visible left-aligned actions.
- [ ] **Step 5: Run focused Host UI/FXML tests** and verify the controls and form structure pass.
- [ ] **Step 6: Run Checkstyle and `git diff --check`.**

### Task 4: Whole-branch verification and handoff

**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`

- [ ] **Step 1: Run focused repository, service, and Host UI tests plus Checkstyle.**
- [ ] **Step 2: Run the full Gradle test suite** and record any unrelated baseline failure.
- [ ] **Step 3: Validate the committed mock DB** with foreign-key checks and integer queries for `bathrooms` and `postalCode`.
- [ ] **Step 4: Start the app against a disposable mock DB copy** and inspect Host Listings and New/Edit Listing with a Host account.
- [ ] **Step 5: Update project state and the newest Done ledger entry** with migration, validation, styling, and verification results.
- [ ] **Step 6: Run `git diff --check` and confirm only scoped changes remain.**
