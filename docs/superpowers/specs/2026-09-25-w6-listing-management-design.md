# W6 — Host Listing Management & Publishing Design

**Date:** 2026-09-25  
**Workstream:** W6 / F5  
**Status:** Approved conversational design; pending written-spec review

## Goal

Give authenticated hosts a complete listing-management flow: create a property with validated listing data, view their own properties, and toggle each listing between `ACTIVE` and `INACTIVE`.

## Scope

This workstream covers the F5 backlog items:

- F5.1.1 — create properties with title, description, address, guest capacity, and base rate.
- F5.1.2 — validate listing inputs before storage, including non-negative pricing and positive capacity.
- F5.2.1 — toggle listing availability between Active and Inactive.

The vertical slice includes the existing `ListingService`, the existing `PropertyRepository`/SQLite adapter, audit calls, `AppContext` wiring, and a Host Listings JavaFX page. A newly created listing is `ACTIVE` by default so it is immediately publishable and discoverable; the host can deactivate it from the same page.

Calendar blackouts, date overrides, booking-request decisions, earnings, and host dispute responses remain in W7/W8. W6 does not add availability-block behavior or modify booking rules.

## Confirmed constraints

- Java 25, JavaFX 25, Gradle, SQLite, and plain JDBC remain unchanged.
- `Property` remains the domain record passed through the service and UI; no DTO layer is introduced.
- UI code depends on `ListingService`, `SessionContext`, and `AppContext`, never on repositories or JDBC.
- Only hosts may create or update listings, and a host may mutate only properties whose `hostId` matches the acting host.
- All mutating service operations record exactly one audit entry.
- Existing `properties` schema fields and `ListingStatus` values (`ACTIVE`, `INACTIVE`) are the source of truth.

## Design

### Service boundary

`ListingService` remains the single application boundary for host listing behavior. It gains a host-owned query method:

```java
List<Property> findByHostId(UUID hostId);
Property create(Property draft, UUID hostId);
Property updateStatus(UUID propertyId, ListingStatus status, UUID hostId);
```

`search`, `getDetail`, and `estimateCost` retain their current behavior. `findByHostId` delegates to `PropertyRepository.findByHostId` and is used only by the Host Listings page.

`ListingServiceImpl` receives `UserService` in addition to the existing property and availability dependencies. It resolves the supplied host ID through `UserService.findById` before every mutation, so role and account-status checks are performed inside the service rather than trusted from the UI.

`create` performs these steps in order:

1. Require a non-null host actor with `Role.HOST` and an `ACTIVE` account.
2. Validate the draft's required text, numeric values, enum/time fields, and date-independent property data.
3. Create a new property identity if the draft has no ID; preserve the host-supplied ID only when it is non-null and not already owned by another host.
4. Normalize the new property to the acting host and `ListingStatus.ACTIVE`.
5. Persist through `PropertyRepository.save`.
6. Record one `LISTING_CREATED` audit entry with `before = null` and `after = saved property`.
7. Return the saved property.

`updateStatus` performs these steps:

1. Require a non-null host actor with `Role.HOST` and an `ACTIVE` account.
2. Require a non-null target status.
3. Load the property or throw `IllegalArgumentException("Property does not exist")`.
4. Reject the operation if the property belongs to another host.
5. Return the existing property unchanged when the requested status already matches; this is an idempotent no-op and does not create an audit row.
6. Save a copy with the requested status.
7. Record one `LISTING_STATUS_CHANGED` audit entry containing the old and new property states.
8. Return the saved property.

### Validation

The service validates before calling the repository:

- `title`, `description`, `streetAddress`, `city`, `region`, and `postalCode` are nonblank.
- `propertyType`, `baseNightlyRate`, `checkInTime`, and `checkOutTime` are non-null.
- `maxGuests` is greater than zero.
- `bedrooms` and `bathrooms` are non-negative.
- `baseNightlyRate` is non-negative.
- `amenities` is treated as an empty set when absent, matching `Property`'s canonical constructor.

The service does not impose a stay-date rule on check-in/check-out times; both are listing metadata and may be equal if the product later decides to support flexible or externally managed hours. The existing database constraints remain the final persistence guard.

Validation and authorization failures are surfaced as `IllegalArgumentException` for invalid listing data and `IllegalStateException` for invalid actor/ownership state, matching existing service conventions.

### Persistence and audit

The existing JDBC `save` UPSERT and `findByHostId` query are reused. No schema migration is needed. `AppContext` constructs one `AuditService` and passes it to `ListingServiceImpl`.

Audit action types are stable strings: `LISTING_CREATED` and `LISTING_STATUS_CHANGED`; entity type is `PROPERTY`; entity ID is the property ID. The audit implementation continues to serialize the record state using its existing representation.

### Host UI

The Host shell's Listings navigation loads a dedicated `host/listings/host-listings.fxml` page and `HostListingsController`. The page contains:

- a listing count/status message;
- a `Create Listing` form for the persisted `Property` fields;
- a list of the current host's property cards;
- an Active/Inactive status pill and toggle button per card;
- inline validation/error feedback and a success message after mutations.

The controller obtains the current host from `SessionContext`, calls `findByHostId` on page load, and reloads after successful create or status change. It never constructs a repository or opens a database connection. The form uses JavaFX controls already used by the project (`TextField`, `TextArea`, `ComboBox`, `Spinner`, and `Button`); time entry is represented with text fields parsed as `HH:mm` because JavaFX has no built-in `TimePicker`.

The existing host shell layout remains role-isolated. W6 may add focused CSS classes for listing cards, status pills, form errors, and empty states, but does not redesign the shared shell.

## Data flow

```text
Host Listings UI
    → SessionContext.currentUser()
    → ListingService.findByHostId / create / updateStatus
    → Authorization + validation
    → PropertyRepository.findByHostId / save
    → SQLite properties table
    → AuditService.record for successful mutations
```

Create and status changes are synchronous single-connection operations, consistent with the current desktop application. A failed validation, authorization check, persistence operation, or audit operation is shown as an error and does not report success to the host.

## Testing strategy

### Service tests

- valid host creation persists an active listing and records `LISTING_CREATED`;
- blank required text, null enum/time fields, negative rate, zero/negative capacity, and negative room/bathroom values are rejected before persistence;
- non-host and suspended-host actors are rejected;
- status changes succeed for the owning host and record before/after audit state;
- another host cannot mutate the property;
- repeating the current status is an idempotent no-op with no audit record;
- `findByHostId` delegates the host-scoped query.

### JDBC integration tests

- created properties round-trip through SQLite with their status and all listing fields;
- Active/Inactive status updates persist;
- database constraints still reject impossible values if a repository is called directly.

Monetary comparisons use `BigDecimal.compareTo`, not `equals`, because SQLite `REAL` values may lose scale.

### UI tests

- the Host shell opens the Listings page;
- the page renders an empty state and host-owned listing cards;
- valid form submission refreshes the list;
- invalid input shows an inline message and does not call the mutation path;
- a status toggle updates the card after the service succeeds.

## Out of scope

- Host calendar/date blocking (W7/F6).
- Booking-request queues or host approval/rejection (W8/F7).
- Host earnings, payout settlement, and wallet withdrawal (W8/W9).
- Image uploads, geocoding, external payment rails, drafts, moderation, and multi-user concurrency beyond SQLite's existing transaction boundary.
