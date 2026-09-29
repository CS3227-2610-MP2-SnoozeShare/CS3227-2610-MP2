# Host Listing Validation and Numeric Fields Design

## Goal

Improve Host listing creation/editing validation and controls while changing the persisted `bathrooms` and `postalCode` property fields to integers in the schema, domain model, repositories, and committed mock database.

## Scope

- Change `properties.bathrooms` from `REAL` to `INTEGER`; change `Property.bathrooms` and all repository/service/UI/test references from `double` to `int`.
- Change `properties.postalCode` from `TEXT` to `INTEGER`; change `Property.postalCode` and all repository/service/UI/test references from `String` to `int`.
- Convert existing mock seed postal values to numeric values, including explicit numeric replacements for international/alphanumeric samples and leading-zero values.
- Rebuild `db/snoozeshare-mock.db` from the updated schema and seed.
- Style the Host active toggle using the Agent category toggle's white-knob left/right behavior, with Host-owned CSS.
- Style visible amenity checkboxes using the Agent audit multi-select option treatment, with an `Optional` subtitle.
- Validate fields in this order: Basic Details, Capacity & Pricing, Location, Amenities.
- Return friendly field names and explicit messages for blank, malformed, and out-of-range values.
- Improve all listing-form placeholders and reduce label/input/card/action spacing so actions remain visible without unnecessary scrolling.
- Let each form card size to its content.

## Data contract

`Property` uses `int postalCode` and `int bathrooms`. Numeric postal codes are stored without leading zeroes because the operator requested an integer schema; the UI displays the stored numeric value. The mock seed uses numeric equivalents for previously non-numeric values.

## Validation rules

- Basic Details: title, description, property type, and status are required.
- Capacity & Pricing: max guests must be positive; bedrooms and bathrooms must be non-negative integers; rate must be present, numeric, and non-negative; check-in/check-out must be present and use `HH:mm` or `HH:mm:ss`; checkout must not be after check-in.
- Location: street address, city, region, and postal code are required; postal code must be a whole number.
- Amenities: optional; an empty selection is valid.

The controller reports the first failure in the section order above. Service validation uses the same friendly field names so non-UI callers receive consistent errors.

## UI design

- The listing status toggle is a `ToggleButton` with a white circular knob graphic, 40x22 proportions, left-aligned when inactive and right-aligned with success coloring when active.
- Amenity options remain directly visible checkboxes, but use the Agent multi-select option appearance: warm inset background, muted border, compact padding, rounded corners, and 12px/13px text.
- Field labels sit close to inputs; cards use content-driven vertical sizing; the action row has a smaller top gap and remains left-aligned.
- Placeholders use examples such as `e.g. Sunset Loft`, `e.g. A bright...`, `e.g. 12 Rua das Flores`, `e.g. 1200`, `e.g. 4`, `e.g. 1`, `e.g. 1`, `e.g. 120.00`, and `e.g. 15:00`.

## Verification

- Service and repository tests cover integer bathroom/postal round trips, numeric mock data, validation order, friendly errors, missing rate/bathroom, and invalid postal input.
- Host UI tests cover the toggle knob structure, optional amenities subtitle, checkbox classes, placeholders, compact spacing, and action visibility structure.
- Rebuild and validate the committed mock database with foreign-key checks and numeric property queries.
- Run focused tests, Checkstyle, and the full suite; record any unrelated baseline failure.
