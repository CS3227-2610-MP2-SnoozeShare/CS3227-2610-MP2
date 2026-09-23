# W2 — Listing Search & Property Discovery (F1) Design Spec

**Status:** Approved via brainstorming session, 2026-09-24
**Author:** Claude Opus 4.6, with Nathan, 2026-09-24
**Workstream:** W2 (F1 — Listing Search & Property Discovery)
**Branch:** `w2`
**Backlog items (Sprint 1):** F1.1.1, F1.1.2, F1.2.1, F1.2.2
**Depends on:** W1 (shared foundation) — merged to `main`
**Visual reference:** [UI design system spec](2026-09-23-ui-design-system-design.md) screens 1 (Search) and 2 (Listing Detail)

---

## 1. Goal

Give guests the ability to search and filter available property listings by location, dates, and guest capacity, then view full property details with a dynamic price breakdown. This is the first Guest-facing feature and the entry point for the booking flow (W3).

**What later workstreams can build on:**
- W3 (F2) adds the "Book Now" action on the detail modal
- W4 (F3) adds the reviews section on the detail modal
- W5 (F4) is independent (wallet top-up/withdraw)

---

## 2. Constraints & Decisions

| ID | Decision | Why |
|---|---|---|
| Inherits C7 | No guest-side service fee — `totalAmount = nightlyRate × nights` | Operator-confirmed, recorded in PROJECT_STATE.md |
| Inherits C4 | `Property` record used directly as view model, no DTO layer | Operator-confirmed |
| W2-D1 | Availability filtering done in Java (service layer), not a single SQL JOIN | Simpler to test; acceptable for MVP scale (single-process, small dataset) |
| W2-D2 | Unavailable properties are excluded from search results entirely | Operator-confirmed during brainstorming |
| W2-D3 | No pagination — all matching results returned at once | MVP scale; can be added later if needed |
| W2-D4 | `estimateCost()` kept as a service method despite trivial logic | Already on `ListingService` interface from W1; centralizes pricing if rules change |
| W2-D5 | "Book Now" button on detail modal is present but disabled/placeholder | Owned by W3 (F2), not this workstream |
| W2-D6 | Reviews section on detail modal is placeholder | Owned by W4 (F3.1.3), not this workstream |

---

## 3. Scope

### 3.1 Extend `SearchCriteria`

The existing `SearchCriteria` record has `city` and `guests`. Add date range fields:

```java
public record SearchCriteria(
    String city,
    Integer guests,
    LocalDate startDate,
    LocalDate endDate
) {}
```

All fields are nullable. If dates are omitted, availability filtering is skipped. If city/guests are omitted, those filters are skipped. No filters = all active listings returned.

### 3.2 Extend `PropertyRepository`

Add one method to the existing interface:

```java
List<Property> findBySearchCriteria(SearchCriteria criteria);
```

The JDBC implementation builds a dynamic `WHERE` clause:
- Always: `status = 'ACTIVE'`
- If `city` non-null: `LOWER(city) LIKE LOWER('%' || ? || '%')` (case-insensitive substring)
- If `guests` non-null: `maxGuests >= ?`

Returns all matching `Property` records. Date-based availability filtering is NOT done in SQL — it happens in the service layer (see §3.4).

### 3.3 Implement `AvailabilityService`

Implement the existing `AvailabilityService` interface. W2 implements `isRangeAvailable()` and `blocksFor()` only; `createHostBlock()` is owned by W7 (F6).

**`isRangeAvailable(propertyId, start, end)`:**
1. Call `AvailabilityBlockRepository.findOverlapping(propertyId, start, end)` — checks host-imposed date blocks
2. Call `BookingRepository.findOverlapping(propertyId, start, end)` — checks confirmed bookings
3. Return `true` only if both return empty lists

**`blocksFor(propertyId)`:**
- Delegates to `AvailabilityBlockRepository.findByPropertyId(propertyId)`

### 3.4 Implement `ListingService`

Implement the existing `ListingService` interface. W2 implements `search()`, `getDetail()`, and `estimateCost()` only; `create()` and `updateStatus()` are owned by W6 (F5).

**`search(criteria)`:**
1. Call `PropertyRepository.findBySearchCriteria(criteria)` — SQL-level filtering by city, guests, ACTIVE status
2. If `criteria.startDate()` and `criteria.endDate()` are both non-null, filter the results by calling `AvailabilityService.isRangeAvailable()` for each property, removing unavailable ones
3. Return the filtered `List<Property>`

**`getDetail(propertyId)`:**
- Call `PropertyRepository.findById(propertyId)`
- Throw if not found

**`estimateCost(propertyId, start, end)`:**
1. Fetch property via `PropertyRepository.findById(propertyId)`
2. Compute `nights = ChronoUnit.DAYS.between(start, end)`
3. Return `PriceBreakdown(baseNightlyRate, nights, baseNightlyRate × nights)`

### 3.5 JDBC Adapters

W2 provides JDBC implementations for:
- **`JdbcPropertyRepository`** — `findById`, `findByHostId`, `findBySearchCriteria`, `save`
- **`JdbcAvailabilityBlockRepository`** — `findById`, `findByPropertyId`, `findOverlapping`, `save`
- **`JdbcBookingRepository`** (partial) — `findOverlapping` only (needed for availability check). Full booking CRUD is owned by W3.

All adapters follow the existing W1 pattern: `PreparedStatement` + row-mapping lambdas, using the `DatabaseManager` transaction boundary from W1.

### 3.6 Guest Search UI

Follows the validated [UI design system spec](2026-09-23-ui-design-system-design.md) Screen 1.

**Search bar:**
- City: `CustomTextField` with placeholder text
- Check-in / Check-out: JavaFX `DatePicker` controls
- Guests: spinner or `ComboBox`
- Search button triggers the query

**Results area:**
- Scrollable list/grid of `Card` components (design tokens: `shadow-card`, `radius-md`)
- Each card shows: title, city, property type, nightly rate, max guests, amenity chips
- Empty state message when no results match
- All active listings shown when no filters are applied

**Controller:** `GuestSearchController` in `ui.guest.search` — depends only on `ListingService` (never repository or JDBC)

### 3.7 Property Detail Modal

Follows the validated [UI design system spec](2026-09-23-ui-design-system-design.md) Screen 2.

**Content:**
- Full property info: title, description, property type, full address, bedrooms/bathrooms, max guests, check-in/check-out times
- Amenities displayed as chips/badges
- Host info: display name (fetched via `UserRepository.findById(hostId)` — already implemented in W1)
- Price breakdown panel: if dates were selected in search, shows nightly rate × nights = total. If no dates, shows nightly rate only
- "Book Now" button — present but disabled (placeholder for W3)
- Reviews section — placeholder (for W4)

**Rendering:** `ModalPane`/`ModalBox` over dimmed + blurred backdrop per the design spec §5

**Controller:** `ListingDetailController` in `ui.guest.listing` — depends on `ListingService` and `UserService`

---

## 4. Explicit Non-Goals

- **Booking submission** — W3 (F2.1)
- **Guest reviews on detail view** — W4 (F3.1.3)
- **Budget filter (F1.1.3)** — Sprint 2, Medium priority. The `SearchCriteria` field can be added for forward-compat but the UI control and repository clause are not built in this workstream
- **Host-side listing CRUD** — W6 (F5)
- **Host date blocking** — W7 (F6)
- **Pagination or infinite scroll** — MVP scale, not needed
- **UI tests (TestFX)** — can be added later, not required for acceptance

---

## 5. Testing Strategy

### Unit Tests

**`ListingServiceImpl`:**
- Search with city only → filters by city
- Search with dates only → excludes unavailable properties
- Search with city + dates + guests → all filters applied
- Search with no filters → returns all active listings
- `getDetail` happy path returns property
- `getDetail` not found throws
- `estimateCost` computes correctly (rate × nights)

**`AvailabilityServiceImpl`:**
- `isRangeAvailable` returns `false` when host block overlaps
- `isRangeAvailable` returns `false` when confirmed booking overlaps
- `isRangeAvailable` returns `true` when no overlaps exist
- `isRangeAvailable` returns `true` when overlapping booking is in non-blocking status (e.g. CANCELLED)

**`JdbcPropertyRepository`:**
- `findBySearchCriteria` with city substring (case-insensitive)
- `findBySearchCriteria` with guest capacity filter
- `findBySearchCriteria` returns only ACTIVE listings
- `findBySearchCriteria` with no criteria returns all active

### Integration Test

- End-to-end search flow: service → repository → SQLite, using the existing test DB infrastructure from W1

---

## 6. Acceptance Criteria

1. **F1.1.1** — Searching with start/end dates excludes properties that have overlapping availability blocks or confirmed bookings for any date in that range
2. **F1.1.2** — Searching by city filters via case-insensitive substring match; searching by guests filters properties where `maxGuests >= input`
3. **F1.2.1** — Clicking a search result opens a modal showing all property fields (title, description, type, address, capacity, bedrooms, bathrooms, check-in/out times, amenities) and the host's display name
4. **F1.2.2** — When dates are selected, the detail modal shows nightly rate, number of nights, and computed total amount

---

## 7. Follow-Up Artifacts

- **Implementation plan** — `docs/superpowers/plans/2026-09-24-w2-listing-search.md` (next step via `writing-plans` skill)
- **W3 (F2)** depends on this workstream's `ListingService` and detail modal being complete
- **W4 (F3.1.3)** extends the detail modal with a reviews section
