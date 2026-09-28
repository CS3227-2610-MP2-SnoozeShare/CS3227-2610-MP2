# Host Listing Pages Artifact Alignment Design

## Goal

Restyle and structurally align the native JavaFX Host Listings, New/Edit Listing, and Booking Calendar pages with the approved Claude artifact, while preserving the existing Host shell navigation and page behavior.

## Scope

- Apply the artifact's Fall Light layout, spacing, typography, borders, radii, shadows, controls, and color tokens to the three Host pages.
- Keep the existing Host top bar and navigation strip unchanged.
- Keep all existing controller responsibilities and service boundaries: listing loading and actions, create/edit validation and persistence, breadcrumbs, calendar navigation, date blocking, and override removal.
- Keep the existing dynamic data and navigation callbacks; artifact sample content is visual reference only.
- Make each page use Host-owned styles rather than Agent stylesheet selectors.
- Add focused UI regression coverage for the page structure and critical style classes.

## Non-goals

- No changes to the database schema or listing/calendar service contracts.
- No new listing, calendar, or navigation capabilities.
- No changes to the Host shell top bar or navigation bar.
- No hardcoded artifact sample listings or dates.
- No responsive web implementation; JavaFX sizing should preserve the artifact's proportions within the desktop window.

## Design

### Style ownership

The existing `host-theme.css` remains the consolidated Host shell/theme stylesheet. Page-specific rules are moved into or added as Host-owned stylesheets for the three page surfaces, with selectors scoped to page style classes. No page stylesheet will import or depend on `agent-theme.css` or Agent-only selectors.

The artifact color tokens remain the source of truth: warm background `#fdf8f0`, overlay white, inset `#f0e8d8`, foreground `#100604`, muted text `#381a10` / `#6c4434`, border `#c0a080` / `#d8c4a8`, accent `#98300c`, success `#40680c`, and danger `#980c1c` with their corresponding subtle backgrounds.

### Host Listings

The page keeps the `My listings` title and `+ New listing` action. Each dynamic listing becomes an artifact-shaped horizontal row: a 64px image placeholder, listing identity/details, centered booking and rating metrics, an Active/Inactive switch, and outline Calendar/Edit actions. Rows use an overlay background, 1px border, 12px radius, and soft shadow. Existing row actions remain independently clickable and must not trigger listing-detail navigation.

### New/Edit Listing

The page keeps the existing breadcrumb and Create/Edit title below it. The form uses a two-column grid of four cards:

- Basic Details: title, description, property type, status.
- Location: street address, city, region, postal code.
- Capacity & Pricing: guests, bedrooms, bathrooms, rate, check-in, check-out.
- Amenities: two-column bordered amenity tiles.

Cards use white overlay backgrounds, warm borders, 12px radius, soft shadows, 20px padding, and compact artifact typography. Save and Cancel remain left-aligned below the grid, with the page's scroll behavior ensuring the form fits within the available desktop height without clipping.

### Booking Calendar

The page keeps the `Listings > <name> > Booking Calendar` breadcrumb. Content is a horizontal split: a flexible calendar pane and a fixed-width 300px right pane for date blocking and current overrides. The calendar pane includes month navigation, a compact legend, weekday/day grid, and artifact-aligned available/booked/blocked/other-month states. The right pane uses the artifact's compact labels, inputs, danger block action, and separated override list. Existing date semantics and service calls remain unchanged.

## Data and interaction rules

- Listings are loaded from the host's existing `ListingService` query.
- Listing metrics continue to come from `ListingMetricsService`.
- Create/Edit continues to use the existing `HostListingFormController` validation and owner-authorized persistence.
- Breadcrumb actions continue to invoke the existing shell callbacks.
- Calendar day classification continues to use existing availability-block sources and half-open date logic.
- Block and remove actions continue to display status feedback in the existing status label.

## Verification

- Add or update FXML/controller tests for the three page hierarchies, required style classes, breadcrumb/title presence, fixed calendar sidebar width, and listing action controls.
- Verify that no Host page FXML or stylesheet references `agent-theme.css` or Agent-only style selectors.
- Run focused UI tests and Checkstyle.
- Run the full Gradle test suite, recording any unrelated pre-existing failure by exact test name.
- Start the app against a disposable copy of the mock database and visually inspect all three pages using a Host account.

## Risks and mitigations

- Dynamic listing rows are created in Java, so their artifact alignment must be enforced through explicit page-owned style classes and structural tests.
- JavaFX controls do not support all web CSS behavior; use native controls with equivalent sizing, state classes, and accessible text.
- Existing shell-level styles may affect shared class names; page selectors will be scoped narrowly enough to prevent Agent or unrelated Host screens from changing.
