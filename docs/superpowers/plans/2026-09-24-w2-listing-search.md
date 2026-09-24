# W2 — Listing Search & Property Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable guests to search/filter property listings by city, dates, and guest count, view full property details with host info, and see dynamic price breakdowns — with available properties shown before unavailable ones.

**Architecture:** Vertical slice through repository → service → UI layers, following W1's established patterns: `PreparedStatement` + `RowMappers` JDBC adapters, service implementations taking `Connection` + repositories in constructors, JavaFX FXML controllers accessing services via `AppContext`. A new `SearchResult` record wraps `Property` with an availability flag so the UI can partition and style results.

**Tech Stack:** Java 25, SQLite via `org.xerial:sqlite-jdbc`, JavaFX 25, JUnit 5

**Spec:** `docs/superpowers/specs/2026-09-24-w2-listing-search-design.md`

## Global Constraints

- Java 25 language level, no preview features beyond what W1 already uses
- SQLite via plain JDBC — no JPA/Hibernate
- Only `repository.jdbc.*` may import `java.sql.*`
- UI controllers depend only on `service.*` interfaces, never on `repository.*` or `infra.db.*`
- Domain records are used directly as view models (decision C4) — no DTO layer
- No guest-side service fee (decision C7) — `totalAmount = nightlyRate × nights`
- All existing tests must continue to pass after every task
- Follow W1 patterns exactly: `RowMappers` for ResultSet mapping, `JdbcCodecs` for type conversion, `IllegalStateException` wrapping `SQLException`

## Review Focus

1. **Null/empty search criteria** — when all fields are null, `findBySearchCriteria` must return all ACTIVE listings, not crash on null parameter binding. The dynamic WHERE builder must skip null clauses entirely.
2. **Date range edge cases** — a booking for dates [Sep 25, Sep 28] should block a search for [Sep 27, Sep 30] (partial overlap), [Sep 25, Sep 28] (exact match), and [Sep 26, Sep 27] (contained), but NOT [Sep 28, Sep 30] (checkout-day-only touching). The overlap query must use `start < endDate AND end > startDate`.
3. **Amenities round-trip** — amenities are stored as comma-separated TEXT in SQLite but parsed to `Set<AmenityType>` in Java. An empty string or null must produce an empty set, not throw. Unknown enum values in the DB should be handled gracefully.
4. **Cancelled/rejected bookings should not block availability** — `BookingRepository.findOverlapping` must filter to only `PENDING` and `CONFIRMED` statuses, not all booking rows in the date range.
5. **estimateCost with zero or negative nights** — `ChronoUnit.DAYS.between(start, end)` where `start >= end` should throw an `IllegalArgumentException`, not return a negative or zero price.

---

### Task 1: Extend domain and service contracts

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/SearchCriteria.java`
- Create: `src/main/java/com/snoozeshare/service/SearchResult.java`
- Modify: `src/main/java/com/snoozeshare/service/ListingService.java`
- Modify: `src/main/java/com/snoozeshare/repository/PropertyRepository.java`
- Test: `src/test/java/com/snoozeshare/service/SearchCriteriaTest.java`

**Interfaces:**
- Consumes: nothing (foundational task)
- Produces:
  - `SearchCriteria(String city, Integer guests, LocalDate startDate, LocalDate endDate)` — all fields nullable
  - `SearchResult(Property property, boolean available)` — used by `ListingService.search()` and the UI
  - `ListingService.search(SearchCriteria)` returns `List<SearchResult>` (changed from `List<Property>`)
  - `PropertyRepository.findBySearchCriteria(SearchCriteria)` returns `List<Property>`

- [ ] **Step 1: Write tests for SearchCriteria**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SearchCriteriaTest {

    @Test
    void allFieldsNullableByDefault() {
        var criteria = new SearchCriteria(null, null, null, null);

        assertNull(criteria.city());
        assertNull(criteria.guests());
        assertNull(criteria.startDate());
        assertNull(criteria.endDate());
    }

    @Test
    void allFieldsPreserved() {
        var criteria = new SearchCriteria("Singapore", 2,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));

        assertEquals("Singapore", criteria.city());
        assertEquals(2, criteria.guests());
        assertEquals(LocalDate.of(2026, 10, 1), criteria.startDate());
        assertEquals(LocalDate.of(2026, 10, 5), criteria.endDate());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.snoozeshare.service.SearchCriteriaTest" 2>&1 | tail -20`
Expected: Compilation failure — `SearchCriteria` constructor takes only 2 args

- [ ] **Step 3: Extend SearchCriteria with date fields**

Replace `src/main/java/com/snoozeshare/service/SearchCriteria.java`:

```java
package com.snoozeshare.service;

import java.time.LocalDate;

public record SearchCriteria(
        String city,
        Integer guests,
        LocalDate startDate,
        LocalDate endDate
) {
}
```

- [ ] **Step 4: Create SearchResult record**

Create `src/main/java/com/snoozeshare/service/SearchResult.java`:

```java
package com.snoozeshare.service;

import com.snoozeshare.domain.model.Property;

public record SearchResult(
        Property property,
        boolean available
) {
}
```

- [ ] **Step 5: Update ListingService interface**

In `src/main/java/com/snoozeshare/service/ListingService.java`, change the return type of `search`:

```java
package com.snoozeshare.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.model.Property;

public interface ListingService {
    List<SearchResult> search(SearchCriteria criteria);

    Property getDetail(UUID propertyId);

    PriceBreakdown estimateCost(UUID propertyId, LocalDate start, LocalDate end);

    Property create(Property draft, UUID hostId);

    Property updateStatus(UUID propertyId, ListingStatus status, UUID hostId);
}
```

- [ ] **Step 6: Add findBySearchCriteria to PropertyRepository**

In `src/main/java/com/snoozeshare/repository/PropertyRepository.java`, add the method:

```java
package com.snoozeshare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.Property;
import com.snoozeshare.service.SearchCriteria;

public interface PropertyRepository {
    Optional<Property> findById(UUID propertyId);

    List<Property> findByHostId(UUID hostId);

    List<Property> findBySearchCriteria(SearchCriteria criteria);

    Property save(Property property);
}
```

- [ ] **Step 7: Run tests to verify SearchCriteria tests pass and existing tests still pass**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All tests PASS (no code yet references the new `search` return type or `findBySearchCriteria`)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/snoozeshare/service/SearchCriteria.java \
       src/main/java/com/snoozeshare/service/SearchResult.java \
       src/main/java/com/snoozeshare/service/ListingService.java \
       src/main/java/com/snoozeshare/repository/PropertyRepository.java \
       src/test/java/com/snoozeshare/service/SearchCriteriaTest.java
git commit -m "feat: extend search contracts with dates and availability flag

Add startDate/endDate to SearchCriteria, introduce SearchResult record,
update ListingService.search() return type, add findBySearchCriteria
to PropertyRepository.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 2: RowMappers and JDBC adapters for Property, AvailabilityBlock, Booking

**Files:**
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepository.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcBookingRepository.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepositoryTest.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepositoryTest.java`
- Test: `src/test/java/com/snoozeshare/repository/jdbc/JdbcBookingRepositoryTest.java`

**Interfaces:**
- Consumes: `SearchCriteria` (Task 1), `Property`/`AvailabilityBlock`/`Booking` domain records, `JdbcCodecs`, `RowMappers`, `PropertyRepository`/`AvailabilityBlockRepository`/`BookingRepository` interfaces
- Produces:
  - `RowMappers.property(ResultSet)` → `Property`
  - `RowMappers.availabilityBlock(ResultSet)` → `AvailabilityBlock`
  - `RowMappers.booking(ResultSet)` → `Booking`
  - `JdbcPropertyRepository` — `findById`, `findByHostId`, `findBySearchCriteria`, `save`
  - `JdbcAvailabilityBlockRepository` — `findById`, `findByPropertyId`, `findOverlapping`, `save`
  - `JdbcBookingRepository` — `findById`, `findOverlapping` (filters to PENDING+CONFIRMED only), `findByGuest`, `findByHostPending`, `save`

- [ ] **Step 1: Write JdbcPropertyRepository tests**

```java
package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.SearchCriteria;
import com.snoozeshare.domain.enums.AccountStatus;

class JdbcPropertyRepositoryTest {

    @Test
    void saveAndFindById() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            Property property = makeProperty(host.userId(), "Beach House", "Singapore",
                    ListingStatus.ACTIVE, 4);
            repo.save(property);

            var found = repo.findById(property.propertyId());

            assertTrue(found.isPresent());
            assertEquals("Beach House", found.get().title());
            assertEquals("Singapore", found.get().city());
            assertEquals(Set.of(AmenityType.WIFI, AmenityType.KITCHEN), found.get().amenities());
        }
    }

    @Test
    void findBySearchCriteriaFiltersByCitySubstringCaseInsensitive() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            repo.save(makeProperty(host.userId(), "Singapore Apt", "Singapore",
                    ListingStatus.ACTIVE, 2));
            repo.save(makeProperty(host.userId(), "KL Condo", "Kuala Lumpur",
                    ListingStatus.ACTIVE, 2));

            var results = repo.findBySearchCriteria(
                    new SearchCriteria("singapore", null, null, null));

            assertEquals(1, results.size());
            assertEquals("Singapore Apt", results.get(0).title());
        }
    }

    @Test
    void findBySearchCriteriaFiltersByGuestCapacity() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            repo.save(makeProperty(host.userId(), "Small Room", "Singapore",
                    ListingStatus.ACTIVE, 1));
            repo.save(makeProperty(host.userId(), "Big House", "Singapore",
                    ListingStatus.ACTIVE, 6));

            var results = repo.findBySearchCriteria(
                    new SearchCriteria(null, 4, null, null));

            assertEquals(1, results.size());
            assertEquals("Big House", results.get(0).title());
        }
    }

    @Test
    void findBySearchCriteriaReturnsOnlyActiveListings() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            repo.save(makeProperty(host.userId(), "Active", "Singapore",
                    ListingStatus.ACTIVE, 2));
            repo.save(makeProperty(host.userId(), "Inactive", "Singapore",
                    ListingStatus.INACTIVE, 2));

            var results = repo.findBySearchCriteria(
                    new SearchCriteria(null, null, null, null));

            assertEquals(1, results.size());
            assertEquals("Active", results.get(0).title());
        }
    }

    @Test
    void findBySearchCriteriaWithNoCriteriaReturnsAllActive() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var repo = new JdbcPropertyRepository(connection);
            User host = saveHost(users);
            repo.save(makeProperty(host.userId(), "A", "Singapore",
                    ListingStatus.ACTIVE, 2));
            repo.save(makeProperty(host.userId(), "B", "Tokyo",
                    ListingStatus.ACTIVE, 4));

            var results = repo.findBySearchCriteria(
                    new SearchCriteria(null, null, null, null));

            assertEquals(2, results.size());
        }
    }

    private static User saveHost(JdbcUserRepository users) {
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", Instant.now());
        return users.save(host);
    }

    private static Property makeProperty(UUID hostId, String title, String city,
                                          ListingStatus status, int maxGuests) {
        return new Property(UUID.randomUUID(), hostId, status, title,
                "A nice place", PropertyType.APARTMENT, "123 Street", city,
                "Central", "123456", maxGuests, 2, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(AmenityType.WIFI, AmenityType.KITCHEN), Instant.now());
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.snoozeshare.repository.jdbc.JdbcPropertyRepositoryTest" 2>&1 | tail -20`
Expected: Compilation failure — `JdbcPropertyRepository` does not exist

- [ ] **Step 3: Add RowMappers for Property, AvailabilityBlock, and Booking**

Add these methods to `src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java`:

```java
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// Add these methods to the existing RowMappers class:

public static Property property(ResultSet result) throws SQLException {
    return new Property(
            JdbcCodecs.uuid(result.getString("propertyId")),
            JdbcCodecs.uuid(result.getString("hostId")),
            ListingStatus.valueOf(result.getString("status")),
            result.getString("title"),
            result.getString("description"),
            PropertyType.valueOf(result.getString("propertyType")),
            result.getString("streetAddress"),
            result.getString("city"),
            result.getString("region"),
            result.getString("postalCode"),
            result.getInt("maxGuests"),
            result.getInt("bedrooms"),
            result.getDouble("bathrooms"),
            JdbcCodecs.decimal(result.getString("baseNightlyRate")),
            JdbcCodecs.localTime(result.getString("checkInTime")),
            JdbcCodecs.localTime(result.getString("checkOutTime")),
            parseAmenities(result.getString("amenities")),
            JdbcCodecs.instant(result.getString("createdAt")));
}

public static AvailabilityBlock availabilityBlock(ResultSet result) throws SQLException {
    return new AvailabilityBlock(
            JdbcCodecs.uuid(result.getString("blockId")),
            JdbcCodecs.uuid(result.getString("propertyId")),
            JdbcCodecs.localDate(result.getString("startDate")),
            JdbcCodecs.localDate(result.getString("endDate")),
            result.getString("source"),
            JdbcCodecs.uuid(result.getString("bookingId")));
}

public static Booking booking(ResultSet result) throws SQLException {
    return new Booking(
            JdbcCodecs.uuid(result.getString("bookingId")),
            JdbcCodecs.uuid(result.getString("listingId")),
            JdbcCodecs.uuid(result.getString("guestId")),
            JdbcCodecs.localDate(result.getString("startDate")),
            JdbcCodecs.localDate(result.getString("endDate")),
            BookingStatus.valueOf(result.getString("status")),
            JdbcCodecs.decimal(result.getString("nightlyRateSnapshot")),
            JdbcCodecs.decimal(result.getString("totalAmount")),
            JdbcCodecs.instant(result.getString("createdAt")),
            JdbcCodecs.instant(result.getString("decidedAt")),
            JdbcCodecs.instant(result.getString("completedAt")));
}

private static Set<AmenityType> parseAmenities(String value) {
    if (value == null || value.isBlank()) {
        return Set.of();
    }
    return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(AmenityType::valueOf)
            .collect(Collectors.toUnmodifiableSet());
}
```

- [ ] **Step 4: Implement JdbcPropertyRepository**

Create `src/main/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepository.java`:

```java
package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;
import com.snoozeshare.service.SearchCriteria;

public final class JdbcPropertyRepository implements PropertyRepository {

    private final Connection connection;

    public JdbcPropertyRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Property> findById(UUID propertyId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM properties WHERE propertyId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(RowMappers.property(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query property", exception);
        }
    }

    @Override
    public List<Property> findByHostId(UUID hostId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM properties WHERE hostId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(hostId));
            try (var result = statement.executeQuery()) {
                List<Property> properties = new ArrayList<>();
                while (result.next()) {
                    properties.add(RowMappers.property(result));
                }
                return properties;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query properties by host", exception);
        }
    }

    @Override
    public List<Property> findBySearchCriteria(SearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("SELECT * FROM properties WHERE status = 'ACTIVE'");
        List<String> values = new ArrayList<>();
        if (criteria.city() != null) {
            sql.append(" AND LOWER(city) LIKE LOWER('%' || ? || '%')");
            values.add(criteria.city());
        }
        if (criteria.guests() != null) {
            sql.append(" AND maxGuests >= ?");
            values.add(String.valueOf(criteria.guests()));
        }
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
            try (var result = statement.executeQuery()) {
                List<Property> properties = new ArrayList<>();
                while (result.next()) {
                    properties.add(RowMappers.property(result));
                }
                return properties;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search properties", exception);
        }
    }

    @Override
    public Property save(Property property) {
        String amenitiesValue = property.amenities().stream()
                .map(AmenityType::name)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        try (var statement = connection.prepareStatement(
                "INSERT INTO properties (propertyId, hostId, status, title, description, "
                        + "propertyType, streetAddress, city, region, postalCode, maxGuests, "
                        + "bedrooms, bathrooms, baseNightlyRate, checkInTime, checkOutTime, "
                        + "amenities, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?) ON CONFLICT(propertyId) DO UPDATE SET "
                        + "hostId = excluded.hostId, status = excluded.status, "
                        + "title = excluded.title, description = excluded.description, "
                        + "propertyType = excluded.propertyType, "
                        + "streetAddress = excluded.streetAddress, city = excluded.city, "
                        + "region = excluded.region, postalCode = excluded.postalCode, "
                        + "maxGuests = excluded.maxGuests, bedrooms = excluded.bedrooms, "
                        + "bathrooms = excluded.bathrooms, "
                        + "baseNightlyRate = excluded.baseNightlyRate, "
                        + "checkInTime = excluded.checkInTime, "
                        + "checkOutTime = excluded.checkOutTime, "
                        + "amenities = excluded.amenities")) {
            statement.setString(1, JdbcCodecs.uuid(property.propertyId()));
            statement.setString(2, JdbcCodecs.uuid(property.hostId()));
            statement.setString(3, property.status().name());
            statement.setString(4, property.title());
            statement.setString(5, property.description());
            statement.setString(6, property.propertyType().name());
            statement.setString(7, property.streetAddress());
            statement.setString(8, property.city());
            statement.setString(9, property.region());
            statement.setString(10, property.postalCode());
            statement.setInt(11, property.maxGuests());
            statement.setInt(12, property.bedrooms());
            statement.setDouble(13, property.bathrooms());
            statement.setString(14, JdbcCodecs.decimal(property.baseNightlyRate()));
            statement.setString(15, JdbcCodecs.localTime(property.checkInTime()));
            statement.setString(16, JdbcCodecs.localTime(property.checkOutTime()));
            statement.setString(17, amenitiesValue);
            statement.setString(18, JdbcCodecs.instant(property.createdAt()));
            statement.executeUpdate();
            return property;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save property", exception);
        }
    }
}
```

- [ ] **Step 5: Run PropertyRepository tests to verify they pass**

Run: `./gradlew test --tests "com.snoozeshare.repository.jdbc.JdbcPropertyRepositoryTest" 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 6: Write JdbcAvailabilityBlockRepository tests**

```java
package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;

class JdbcAvailabilityBlockRepositoryTest {

    @Test
    void saveAndFindByPropertyId() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var properties = new JdbcPropertyRepository(connection);
            var repo = new JdbcAvailabilityBlockRepository(connection);
            UUID propertyId = seedProperty(users, properties);

            AvailabilityBlock block = new AvailabilityBlock(UUID.randomUUID(), propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    "HOST_BLOCK", null);
            repo.save(block);

            var found = repo.findByPropertyId(propertyId);
            assertEquals(1, found.size());
            assertEquals(block.blockId(), found.get(0).blockId());
        }
    }

    @Test
    void findOverlappingDetectsPartialOverlap() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var properties = new JdbcPropertyRepository(connection);
            var repo = new JdbcAvailabilityBlockRepository(connection);
            UUID propertyId = seedProperty(users, properties);
            repo.save(new AvailabilityBlock(UUID.randomUUID(), propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    "HOST_BLOCK", null));

            var overlapping = repo.findOverlapping(propertyId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 8));

            assertEquals(1, overlapping.size());
        }
    }

    @Test
    void findOverlappingReturnsEmptyWhenNoOverlap() throws Exception {
        try (Connection connection = migratedConnection()) {
            var users = new JdbcUserRepository(connection);
            var properties = new JdbcPropertyRepository(connection);
            var repo = new JdbcAvailabilityBlockRepository(connection);
            UUID propertyId = seedProperty(users, properties);
            repo.save(new AvailabilityBlock(UUID.randomUUID(), propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    "HOST_BLOCK", null));

            var overlapping = repo.findOverlapping(propertyId,
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8));

            assertTrue(overlapping.isEmpty());
        }
    }

    private static UUID seedProperty(JdbcUserRepository users,
                                      JdbcPropertyRepository properties) {
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", Instant.now());
        users.save(host);
        Property property = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Test", "desc", PropertyType.APARTMENT,
                "street", "city", "region", "000000", 2, 1, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(), Instant.now());
        properties.save(property);
        return property.propertyId();
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
```

- [ ] **Step 7: Implement JdbcAvailabilityBlockRepository**

Create `src/main/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepository.java`:

```java
package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcAvailabilityBlockRepository implements AvailabilityBlockRepository {

    private final Connection connection;

    public JdbcAvailabilityBlockRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<AvailabilityBlock> findById(UUID blockId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM availability_blocks WHERE blockId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(blockId));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(RowMappers.availabilityBlock(result))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query availability block", exception);
        }
    }

    @Override
    public List<AvailabilityBlock> findByPropertyId(UUID propertyId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM availability_blocks WHERE propertyId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            try (var result = statement.executeQuery()) {
                List<AvailabilityBlock> blocks = new ArrayList<>();
                while (result.next()) {
                    blocks.add(RowMappers.availabilityBlock(result));
                }
                return blocks;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to query availability blocks by property", exception);
        }
    }

    @Override
    public List<AvailabilityBlock> findOverlapping(UUID propertyId,
                                                    LocalDate start, LocalDate end) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM availability_blocks WHERE propertyId = ? "
                        + "AND startDate < ? AND endDate > ?")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            statement.setString(2, JdbcCodecs.localDate(end));
            statement.setString(3, JdbcCodecs.localDate(start));
            try (var result = statement.executeQuery()) {
                List<AvailabilityBlock> blocks = new ArrayList<>();
                while (result.next()) {
                    blocks.add(RowMappers.availabilityBlock(result));
                }
                return blocks;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to query overlapping availability blocks", exception);
        }
    }

    @Override
    public AvailabilityBlock save(AvailabilityBlock block) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO availability_blocks (blockId, propertyId, startDate, endDate, "
                        + "source, bookingId) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(block.blockId()));
            statement.setString(2, JdbcCodecs.uuid(block.propertyId()));
            statement.setString(3, JdbcCodecs.localDate(block.startDate()));
            statement.setString(4, JdbcCodecs.localDate(block.endDate()));
            statement.setString(5, block.source());
            statement.setString(6, JdbcCodecs.uuid(block.bookingId()));
            statement.executeUpdate();
            return block;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save availability block", exception);
        }
    }
}
```

- [ ] **Step 8: Write JdbcBookingRepository tests**

```java
package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;

class JdbcBookingRepositoryTest {

    @Test
    void findOverlappingReturnsOnlyPendingAndConfirmedBookings() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var repo = new JdbcBookingRepository(connection);
            LocalDate oct1 = LocalDate.of(2026, 10, 1);
            LocalDate oct5 = LocalDate.of(2026, 10, 5);

            repo.save(makeBooking(ctx.propertyId, ctx.guestId, oct1, oct5,
                    BookingStatus.CONFIRMED));
            repo.save(makeBooking(ctx.propertyId, ctx.guestId, oct1, oct5,
                    BookingStatus.CANCELLED_BY_GUEST));

            var overlapping = repo.findOverlapping(ctx.propertyId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 8));

            assertEquals(1, overlapping.size());
            assertEquals(BookingStatus.CONFIRMED, overlapping.get(0).status());
        }
    }

    @Test
    void findOverlappingReturnsEmptyWhenNoOverlap() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var repo = new JdbcBookingRepository(connection);
            repo.save(makeBooking(ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    BookingStatus.CONFIRMED));

            var overlapping = repo.findOverlapping(ctx.propertyId,
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8));

            assertTrue(overlapping.isEmpty());
        }
    }

    private static Booking makeBooking(UUID propertyId, UUID guestId,
                                        LocalDate start, LocalDate end,
                                        BookingStatus status) {
        return new Booking(UUID.randomUUID(), propertyId, guestId, start, end,
                status, new BigDecimal("100.00"), new BigDecimal("400.00"),
                Instant.now(), null, null);
    }

    private record TestContext(UUID propertyId, UUID guestId) {
    }

    private static TestContext seedContext(Connection connection) {
        var users = new JdbcUserRepository(connection);
        var properties = new JdbcPropertyRepository(connection);
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", Instant.now());
        users.save(host);
        User guest = new User(UUID.randomUUID(), Role.GUEST, "Guest",
                "guest-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, null, Instant.now());
        users.save(guest);
        Property property = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Test", "desc", PropertyType.APARTMENT,
                "street", "city", "region", "000000", 2, 1, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(), Instant.now());
        properties.save(property);
        return new TestContext(property.propertyId(), guest.userId());
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
```

- [ ] **Step 9: Implement JdbcBookingRepository**

Create `src/main/java/com/snoozeshare/repository/jdbc/JdbcBookingRepository.java`:

```java
package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcBookingRepository implements BookingRepository {

    private final Connection connection;

    public JdbcBookingRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Booking> findById(UUID bookingId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM bookings WHERE bookingId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(bookingId));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(RowMappers.booking(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query booking", exception);
        }
    }

    @Override
    public List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM bookings WHERE listingId = ? "
                        + "AND startDate < ? AND endDate > ? "
                        + "AND status IN ('PENDING', 'CONFIRMED')")) {
            statement.setString(1, JdbcCodecs.uuid(propertyId));
            statement.setString(2, JdbcCodecs.localDate(end));
            statement.setString(3, JdbcCodecs.localDate(start));
            try (var result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(RowMappers.booking(result));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query overlapping bookings", exception);
        }
    }

    @Override
    public List<Booking> findByGuest(UUID guestId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM bookings WHERE guestId = ? ORDER BY createdAt DESC")) {
            statement.setString(1, JdbcCodecs.uuid(guestId));
            try (var result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(RowMappers.booking(result));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query bookings by guest", exception);
        }
    }

    @Override
    public List<Booking> findByHostPending(UUID hostId) {
        try (var statement = connection.prepareStatement(
                "SELECT b.* FROM bookings b JOIN properties p ON b.listingId = p.propertyId "
                        + "WHERE p.hostId = ? AND b.status = 'PENDING' "
                        + "ORDER BY b.createdAt")) {
            statement.setString(1, JdbcCodecs.uuid(hostId));
            try (var result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(RowMappers.booking(result));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query pending bookings by host", exception);
        }
    }

    @Override
    public Booking save(Booking booking) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO bookings (bookingId, listingId, guestId, startDate, endDate, "
                        + "status, nightlyRateSnapshot, totalAmount, createdAt, decidedAt, "
                        + "completedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(bookingId) DO UPDATE SET status = excluded.status, "
                        + "decidedAt = excluded.decidedAt, completedAt = excluded.completedAt")) {
            statement.setString(1, JdbcCodecs.uuid(booking.bookingId()));
            statement.setString(2, JdbcCodecs.uuid(booking.listingId()));
            statement.setString(3, JdbcCodecs.uuid(booking.guestId()));
            statement.setString(4, JdbcCodecs.localDate(booking.startDate()));
            statement.setString(5, JdbcCodecs.localDate(booking.endDate()));
            statement.setString(6, booking.status().name());
            statement.setString(7, JdbcCodecs.decimal(booking.nightlyRateSnapshot()));
            statement.setString(8, JdbcCodecs.decimal(booking.totalAmount()));
            statement.setString(9, JdbcCodecs.instant(booking.createdAt()));
            statement.setString(10, JdbcCodecs.instant(booking.decidedAt()));
            statement.setString(11, JdbcCodecs.instant(booking.completedAt()));
            statement.executeUpdate();
            return booking;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save booking", exception);
        }
    }
}
```

- [ ] **Step 10: Run all repository tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java \
       src/main/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepository.java \
       src/main/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepository.java \
       src/main/java/com/snoozeshare/repository/jdbc/JdbcBookingRepository.java \
       src/test/java/com/snoozeshare/repository/jdbc/JdbcPropertyRepositoryTest.java \
       src/test/java/com/snoozeshare/repository/jdbc/JdbcAvailabilityBlockRepositoryTest.java \
       src/test/java/com/snoozeshare/repository/jdbc/JdbcBookingRepositoryTest.java
git commit -m "feat: add JDBC adapters for Property, AvailabilityBlock, Booking

Implement PropertyRepository with dynamic search criteria filtering,
AvailabilityBlockRepository with overlap detection, and BookingRepository
filtering overlapping queries to PENDING+CONFIRMED statuses only.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: AvailabilityService and ListingService implementations

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/AvailabilityServiceImpl.java`
- Create: `src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/AvailabilityServiceTest.java`
- Test: `src/test/java/com/snoozeshare/service/ListingServiceTest.java`

**Interfaces:**
- Consumes: `PropertyRepository.findBySearchCriteria()`, `PropertyRepository.findById()`, `AvailabilityBlockRepository.findOverlapping()`, `BookingRepository.findOverlapping()`, `SearchCriteria`, `SearchResult`, `PriceBreakdown` (all from Tasks 1–2)
- Produces:
  - `AvailabilityServiceImpl.isRangeAvailable(UUID, LocalDate, LocalDate)` → `boolean`
  - `AvailabilityServiceImpl.blocksFor(UUID)` → `List<AvailabilityBlock>`
  - `ListingServiceImpl.search(SearchCriteria)` → `List<SearchResult>` (available first, then unavailable)
  - `ListingServiceImpl.getDetail(UUID)` → `Property`
  - `ListingServiceImpl.estimateCost(UUID, LocalDate, LocalDate)` → `PriceBreakdown`

- [ ] **Step 1: Write AvailabilityService tests**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.impl.AvailabilityServiceImpl;

class AvailabilityServiceTest {

    @Test
    void availableWhenNoBlocksOrBookings() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            AvailabilityService service = new AvailabilityServiceImpl(
                    new JdbcAvailabilityBlockRepository(connection),
                    new JdbcBookingRepository(connection));

            assertTrue(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    @Test
    void unavailableWhenHostBlockOverlaps() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var blocks = new JdbcAvailabilityBlockRepository(connection);
            blocks.save(new AvailabilityBlock(UUID.randomUUID(), ctx.propertyId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 7),
                    "HOST_BLOCK", null));
            AvailabilityService service = new AvailabilityServiceImpl(blocks,
                    new JdbcBookingRepository(connection));

            assertFalse(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    @Test
    void unavailableWhenConfirmedBookingOverlaps() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var bookings = new JdbcBookingRepository(connection);
            bookings.save(new Booking(UUID.randomUUID(), ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 7),
                    BookingStatus.CONFIRMED, new BigDecimal("100.00"),
                    new BigDecimal("400.00"), Instant.now(), null, null));
            AvailabilityService service = new AvailabilityServiceImpl(
                    new JdbcAvailabilityBlockRepository(connection), bookings);

            assertFalse(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    @Test
    void availableWhenOverlappingBookingIsCancelled() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var bookings = new JdbcBookingRepository(connection);
            bookings.save(new Booking(UUID.randomUUID(), ctx.propertyId, ctx.guestId,
                    LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 7),
                    BookingStatus.CANCELLED_BY_GUEST, new BigDecimal("100.00"),
                    new BigDecimal("400.00"), Instant.now(), null, null));
            AvailabilityService service = new AvailabilityServiceImpl(
                    new JdbcAvailabilityBlockRepository(connection), bookings);

            assertTrue(service.isRangeAvailable(ctx.propertyId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        }
    }

    private record TestContext(UUID propertyId, UUID guestId) {
    }

    private static TestContext seedContext(Connection connection) {
        var users = new JdbcUserRepository(connection);
        var properties = new JdbcPropertyRepository(connection);
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", Instant.now());
        users.save(host);
        User guest = new User(UUID.randomUUID(), Role.GUEST, "Guest",
                "guest-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, null, Instant.now());
        users.save(guest);
        Property property = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Test", "desc", PropertyType.APARTMENT,
                "street", "city", "region", "000000", 2, 1, 1.0,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(), Instant.now());
        properties.save(property);
        return new TestContext(property.propertyId(), guest.userId());
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
```

- [ ] **Step 2: Implement AvailabilityServiceImpl**

Create `src/main/java/com/snoozeshare/service/impl/AvailabilityServiceImpl.java`:

```java
package com.snoozeshare.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.service.AvailabilityService;

public final class AvailabilityServiceImpl implements AvailabilityService {

    private final AvailabilityBlockRepository blocks;
    private final BookingRepository bookings;

    public AvailabilityServiceImpl(AvailabilityBlockRepository blocks,
                                    BookingRepository bookings) {
        this.blocks = blocks;
        this.bookings = bookings;
    }

    @Override
    public boolean isRangeAvailable(UUID propertyId, LocalDate start, LocalDate end) {
        return blocks.findOverlapping(propertyId, start, end).isEmpty()
                && bookings.findOverlapping(propertyId, start, end).isEmpty();
    }

    @Override
    public AvailabilityBlock createHostBlock(UUID propertyId, LocalDate start, LocalDate end,
                                              UUID hostId) {
        throw new UnsupportedOperationException("Owned by W7 (F6)");
    }

    @Override
    public List<AvailabilityBlock> blocksFor(UUID propertyId) {
        return blocks.findByPropertyId(propertyId);
    }
}
```

- [ ] **Step 3: Run AvailabilityService tests**

Run: `./gradlew test --tests "com.snoozeshare.service.AvailabilityServiceTest" 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 4: Write ListingService tests**

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.impl.AvailabilityServiceImpl;
import com.snoozeshare.service.impl.ListingServiceImpl;

class ListingServiceTest {

    @Test
    void searchWithNoCriteriaReturnsAllActive() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            ListingService service = createService(connection);

            var results = service.search(new SearchCriteria(null, null, null, null));

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(SearchResult::available));
        }
    }

    @Test
    void searchByCityFiltersResults() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            ListingService service = createService(connection);

            var results = service.search(
                    new SearchCriteria("singapore", null, null, null));

            assertEquals(1, results.size());
            assertEquals("Singapore Apt", results.get(0).property().title());
        }
    }

    @Test
    void searchWithDatesShowsAvailableBeforeUnavailable() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            var bookings = new JdbcBookingRepository(connection);
            bookings.save(new Booking(UUID.randomUUID(), ctx.singaporeId, ctx.guestId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                    BookingStatus.CONFIRMED, new BigDecimal("150.00"),
                    new BigDecimal("600.00"), Instant.now(), null, null));
            ListingService service = createService(connection);

            var results = service.search(new SearchCriteria(null, null,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));

            assertEquals(2, results.size());
            assertTrue(results.get(0).available());
            assertEquals("Tokyo House", results.get(0).property().title());
            assertFalse(results.get(1).available());
            assertEquals("Singapore Apt", results.get(1).property().title());
        }
    }

    @Test
    void getDetailReturnsProperty() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            ListingService service = createService(connection);

            Property detail = service.getDetail(ctx.singaporeId);

            assertEquals("Singapore Apt", detail.title());
        }
    }

    @Test
    void getDetailThrowsWhenNotFound() throws Exception {
        try (Connection connection = migratedConnection()) {
            ListingService service = createService(connection);

            assertThrows(IllegalArgumentException.class,
                    () -> service.getDetail(UUID.randomUUID()));
        }
    }

    @Test
    void estimateCostComputesCorrectly() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            ListingService service = createService(connection);

            PriceBreakdown breakdown = service.estimateCost(ctx.singaporeId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4));

            assertEquals(new BigDecimal("150.00"), breakdown.nightlyRate());
            assertEquals(3, breakdown.nights());
            assertEquals(new BigDecimal("450.00"), breakdown.totalAmount());
        }
    }

    @Test
    void estimateCostThrowsWhenStartNotBeforeEnd() throws Exception {
        try (Connection connection = migratedConnection()) {
            var ctx = seedContext(connection);
            ListingService service = createService(connection);

            assertThrows(IllegalArgumentException.class,
                    () -> service.estimateCost(ctx.singaporeId,
                            LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)));
            assertThrows(IllegalArgumentException.class,
                    () -> service.estimateCost(ctx.singaporeId,
                            LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3)));
        }
    }

    private record TestContext(UUID singaporeId, UUID tokyoId, UUID guestId) {
    }

    private static TestContext seedContext(Connection connection) {
        var users = new JdbcUserRepository(connection);
        var properties = new JdbcPropertyRepository(connection);
        User host = new User(UUID.randomUUID(), Role.HOST, "Host",
                "host-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, "HOST2026", Instant.now());
        users.save(host);
        User guest = new User(UUID.randomUUID(), Role.GUEST, "Guest",
                "guest-" + UUID.randomUUID() + "@test.com",
                AccountStatus.ACTIVE, null, Instant.now());
        users.save(guest);
        Property singapore = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Singapore Apt", "Nice", PropertyType.APARTMENT,
                "street", "Singapore", "Central", "123456", 2, 1, 1.0,
                new BigDecimal("150.00"), LocalTime.of(14, 0), LocalTime.of(11, 0),
                Set.of(), Instant.now());
        properties.save(singapore);
        Property tokyo = new Property(UUID.randomUUID(), host.userId(),
                ListingStatus.ACTIVE, "Tokyo House", "Great", PropertyType.HOUSE,
                "street", "Tokyo", "Shibuya", "100000", 4, 2, 1.5,
                new BigDecimal("200.00"), LocalTime.of(15, 0), LocalTime.of(10, 0),
                Set.of(), Instant.now());
        properties.save(tokyo);
        return new TestContext(singapore.propertyId(), tokyo.propertyId(), guest.userId());
    }

    private static ListingService createService(Connection connection) {
        var properties = new JdbcPropertyRepository(connection);
        var blocks = new JdbcAvailabilityBlockRepository(connection);
        var bookings = new JdbcBookingRepository(connection);
        var availability = new AvailabilityServiceImpl(blocks, bookings);
        return new ListingServiceImpl(properties, availability);
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
```

- [ ] **Step 5: Implement ListingServiceImpl**

Create `src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java`:

```java
package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.service.AvailabilityService;
import com.snoozeshare.service.ListingService;
import com.snoozeshare.service.PriceBreakdown;
import com.snoozeshare.service.SearchCriteria;
import com.snoozeshare.service.SearchResult;

public final class ListingServiceImpl implements ListingService {

    private final PropertyRepository properties;
    private final AvailabilityService availability;

    public ListingServiceImpl(PropertyRepository properties,
                               AvailabilityService availability) {
        this.properties = properties;
        this.availability = availability;
    }

    @Override
    public List<SearchResult> search(SearchCriteria criteria) {
        List<Property> candidates = properties.findBySearchCriteria(criteria);
        boolean hasDateRange = criteria.startDate() != null && criteria.endDate() != null;
        if (!hasDateRange) {
            return candidates.stream()
                    .map(p -> new SearchResult(p, true))
                    .toList();
        }
        List<SearchResult> available = new ArrayList<>();
        List<SearchResult> unavailable = new ArrayList<>();
        for (Property property : candidates) {
            if (availability.isRangeAvailable(property.propertyId(),
                    criteria.startDate(), criteria.endDate())) {
                available.add(new SearchResult(property, true));
            } else {
                unavailable.add(new SearchResult(property, false));
            }
        }
        List<SearchResult> results = new ArrayList<>(available);
        results.addAll(unavailable);
        return results;
    }

    @Override
    public Property getDetail(UUID propertyId) {
        return properties.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
    }

    @Override
    public PriceBreakdown estimateCost(UUID propertyId, LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates must not be null");
        }
        long nights = ChronoUnit.DAYS.between(start, end);
        if (nights <= 0) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        Property property = getDetail(propertyId);
        BigDecimal totalAmount = property.baseNightlyRate()
                .multiply(BigDecimal.valueOf(nights));
        return new PriceBreakdown(property.baseNightlyRate(), (int) nights, totalAmount);
    }

    @Override
    public Property create(Property draft, UUID hostId) {
        throw new UnsupportedOperationException("Owned by W6 (F5)");
    }

    @Override
    public Property updateStatus(UUID propertyId, ListingStatus status, UUID hostId) {
        throw new UnsupportedOperationException("Owned by W6 (F5)");
    }
}
```

- [ ] **Step 6: Run all tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/snoozeshare/service/impl/AvailabilityServiceImpl.java \
       src/main/java/com/snoozeshare/service/impl/ListingServiceImpl.java \
       src/test/java/com/snoozeshare/service/AvailabilityServiceTest.java \
       src/test/java/com/snoozeshare/service/ListingServiceTest.java
git commit -m "feat: implement AvailabilityService and ListingService

AvailabilityService checks both host blocks and confirmed bookings.
ListingService partitions search results: available first, then
unavailable. estimateCost validates date range and computes rate x nights.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: Wire services into AppContext

**Files:**
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`

**Interfaces:**
- Consumes: `JdbcPropertyRepository`, `JdbcAvailabilityBlockRepository`, `JdbcBookingRepository`, `AvailabilityServiceImpl`, `ListingServiceImpl` (Tasks 2–3)
- Produces: `AppContext.listingService()` and `AppContext.availabilityService()` accessors

- [ ] **Step 1: Add listing and availability services to AppContext**

In `src/main/java/com/snoozeshare/app/AppContext.java`, add the new fields, instantiate them in the constructor, and add accessor methods:

Add imports:
```java
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.service.AvailabilityService;
import com.snoozeshare.service.ListingService;
import com.snoozeshare.service.impl.AvailabilityServiceImpl;
import com.snoozeshare.service.impl.ListingServiceImpl;
```

Add fields:
```java
private final ListingService listingService;
private final AvailabilityService availabilityService;
```

In the constructor, after existing wiring, add:
```java
JdbcPropertyRepository propertyRepo = new JdbcPropertyRepository(connection);
JdbcAvailabilityBlockRepository blockRepo = new JdbcAvailabilityBlockRepository(connection);
JdbcBookingRepository bookingRepo = new JdbcBookingRepository(connection);
this.availabilityService = new AvailabilityServiceImpl(blockRepo, bookingRepo);
this.listingService = new ListingServiceImpl(propertyRepo, availabilityService);
```

Add accessors:
```java
public ListingService listingService() {
    return listingService;
}

public AvailabilityService availabilityService() {
    return availabilityService;
}
```

- [ ] **Step 2: Run all tests to verify nothing breaks**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/snoozeshare/app/AppContext.java
git commit -m "feat: wire ListingService and AvailabilityService into AppContext

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 5: Guest Search UI

**Files:**
- Create: `src/main/resources/com/snoozeshare/ui/guest/search/guest-search.fxml`
- Create: `src/main/java/com/snoozeshare/ui/guest/search/GuestSearchController.java`
- Modify: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/guest/guest-shell.fxml`
- Modify: `src/main/resources/com/snoozeshare/ui/common/theme.css`

**Interfaces:**
- Consumes: `AppContext.listingService()` (Task 4), `ListingService.search(SearchCriteria)` → `List<SearchResult>`, `SearchCriteria`, `SearchResult`
- Produces: `GuestSearchController` — standalone FXML controller loaded into the guest shell's content area; exposes `setContext(AppContext)` and an `onPropertySelected` callback for opening the detail modal (Task 6)

- [ ] **Step 1: Create the search FXML**

Create `src/main/resources/com/snoozeshare/ui/guest/search/guest-search.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.DatePicker?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.FlowPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.guest.search.GuestSearchController">
    <padding><Insets top="20" right="20" bottom="20" left="20"/></padding>

    <Label text="Search Properties" styleClass="page-title"/>

    <HBox spacing="12" alignment="CENTER_LEFT">
        <TextField fx:id="cityField" promptText="City or location" prefWidth="200"/>
        <DatePicker fx:id="checkInPicker" promptText="Check-in" prefWidth="140"/>
        <DatePicker fx:id="checkOutPicker" promptText="Check-out" prefWidth="140"/>
        <ComboBox fx:id="guestsCombo" promptText="Guests" prefWidth="100"/>
        <Button text="Search" onAction="#handleSearch" styleClass="button"/>
    </HBox>

    <Label fx:id="statusLabel" text="" styleClass="small"/>

    <ScrollPane fitToWidth="true" VBox.vgrow="ALWAYS">
        <FlowPane fx:id="resultsPane" hgap="16" vgap="16">
            <padding><Insets top="8" right="8" bottom="8" left="8"/></padding>
        </FlowPane>
    </ScrollPane>
</VBox>
```

- [ ] **Step 2: Create GuestSearchController**

Create `src/main/java/com/snoozeshare/ui/guest/search/GuestSearchController.java`:

```java
package com.snoozeshare.ui.guest.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.service.SearchCriteria;
import com.snoozeshare.service.SearchResult;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public final class GuestSearchController {

    @FXML private TextField cityField;
    @FXML private DatePicker checkInPicker;
    @FXML private DatePicker checkOutPicker;
    @FXML private ComboBox<Integer> guestsCombo;
    @FXML private Label statusLabel;
    @FXML private FlowPane resultsPane;

    private AppContext context;
    private Consumer<Property> onPropertySelected;

    public void setContext(AppContext context) {
        this.context = context;
    }

    public void setOnPropertySelected(Consumer<Property> callback) {
        this.onPropertySelected = callback;
    }

    @FXML
    private void initialize() {
        guestsCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @FXML
    private void handleSearch() {
        String city = cityField.getText();
        if (city != null && city.isBlank()) {
            city = null;
        }
        Integer guests = guestsCombo.getValue();
        LocalDate checkIn = checkInPicker.getValue();
        LocalDate checkOut = checkOutPicker.getValue();

        SearchCriteria criteria = new SearchCriteria(city, guests, checkIn, checkOut);
        List<SearchResult> results = context.listingService().search(criteria);

        resultsPane.getChildren().clear();
        if (results.isEmpty()) {
            statusLabel.setText("No properties found matching your criteria.");
        } else {
            statusLabel.setText(results.size() + " properties found.");
            for (SearchResult result : results) {
                resultsPane.getChildren().add(createPropertyCard(result));
            }
        }
    }

    private VBox createPropertyCard(SearchResult result) {
        Property property = result.property();
        VBox card = new VBox(6);
        card.setPadding(new Insets(16));
        card.setPrefWidth(280);
        card.getStyleClass().add("property-card");

        Label title = new Label(property.title());
        title.getStyleClass().add("card-title");

        Label location = new Label(property.city() + " · " + property.propertyType().name());
        location.getStyleClass().add("small");

        BigDecimal rate = property.baseNightlyRate().setScale(2, RoundingMode.HALF_UP);
        Label price = new Label("SGD " + rate + " / night");
        price.getStyleClass().add("card-price");

        Label capacity = new Label(property.maxGuests() + " guests · "
                + property.bedrooms() + " bed · " + property.bathrooms() + " bath");
        capacity.getStyleClass().add("small");

        card.getChildren().addAll(title, location, price, capacity);

        if (!result.available()) {
            card.setOpacity(0.5);
            Label unavailable = new Label("Unavailable for selected dates");
            unavailable.getStyleClass().addAll("small", "unavailable-label");
            card.getChildren().add(unavailable);
        }

        card.setOnMouseClicked(event -> {
            if (onPropertySelected != null) {
                onPropertySelected.accept(property);
            }
        });

        return card;
    }
}
```

- [ ] **Step 3: Add property card styles to theme.css**

Append to `src/main/resources/com/snoozeshare/ui/common/theme.css`:

```css
/* Property search cards */
.property-card {
    -fx-background-color: white;
    -fx-background-radius: 8;
    -fx-border-color: #d4dbe7;
    -fx-border-radius: 8;
    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);
    -fx-cursor: hand;
}

.property-card:hover {
    -fx-border-color: #263a5b;
}

.card-title {
    -fx-font-size: 15;
    -fx-font-weight: bold;
}

.card-price {
    -fx-font-size: 14;
    -fx-font-weight: bold;
    -fx-text-fill: #263a5b;
}

.unavailable-label {
    -fx-text-fill: #b42318;
    -fx-font-style: italic;
}
```

- [ ] **Step 4: Modify GuestShellController to load the search view**

Replace the `showExplore()` method in `GuestShellController` to load the search FXML into the content area. The controller needs a reference to the content area from the shell FXML.

Update `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`:

```java
package com.snoozeshare.ui.guest;

import java.io.IOException;

import com.snoozeshare.ui.common.NavShellController;
import com.snoozeshare.ui.guest.search.GuestSearchController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public final class GuestShellController extends NavShellController {

    @FXML
    private BorderPane shellRoot;

    @FXML
    private void showExplore() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/search/guest-search.fxml"));
            Node searchView = loader.load();
            GuestSearchController controller = loader.getController();
            controller.setContext(getContext());
            controller.setOnPropertySelected(property -> {
                // Detail modal — implemented in Task 6
            });
            shellRoot.setCenter(searchView);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load search view", exception);
        }
    }

    @FXML
    private void showMyTrips() {
        displayPage("My Trips", "Your upcoming and past trips will appear here.");
    }

    @FXML
    private void showSupport() {
        displayPage("Support", "Find help with bookings, payments, and stays.");
    }
}
```

- [ ] **Step 5: Expose getContext() in NavShellController**

Add a protected accessor to `NavShellController` so subclasses can access the `AppContext`:

In `src/main/java/com/snoozeshare/ui/common/NavShellController.java`, add:

```java
protected AppContext getContext() {
    return context;
}
```

(where `context` is the existing `AppContext` field — check the exact field name and add the getter if it doesn't exist)

- [ ] **Step 6: Add fx:id="shellRoot" to guest-shell.fxml**

In `src/main/resources/com/snoozeshare/ui/guest/guest-shell.fxml`, add `fx:id="shellRoot"` to the root `BorderPane` element so the controller can reference it.

- [ ] **Step 7: Run the app to visually verify search loads**

Run: `./gradlew run`
Expected: Login → register as Guest → the Explore nav item loads the search view with city field, date pickers, guests combo, and search button. Searching with no filters shows no results (no properties in the fresh DB).

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/com/snoozeshare/ui/guest/search/guest-search.fxml \
       src/main/java/com/snoozeshare/ui/guest/search/GuestSearchController.java \
       src/main/java/com/snoozeshare/ui/guest/GuestShellController.java \
       src/main/java/com/snoozeshare/ui/common/NavShellController.java \
       src/main/resources/com/snoozeshare/ui/guest/guest-shell.fxml \
       src/main/resources/com/snoozeshare/ui/common/theme.css
git commit -m "feat: add guest search UI with property cards

Search bar with city, dates, guests filters. Results displayed as
styled cards with availability indication (muted for unavailable).

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 6: Property Detail Modal

**Files:**
- Create: `src/main/resources/com/snoozeshare/ui/guest/listing/listing-detail.fxml`
- Create: `src/main/java/com/snoozeshare/ui/guest/listing/ListingDetailController.java`
- Modify: `src/main/java/com/snoozeshare/ui/guest/search/GuestSearchController.java` (wire the callback)
- Modify: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java` (wire the detail modal)

**Interfaces:**
- Consumes: `AppContext.listingService().getDetail(UUID)`, `AppContext.listingService().estimateCost(UUID, LocalDate, LocalDate)`, `AppContext.userService()` (for host display name), `Property`, `PriceBreakdown`
- Produces: `ListingDetailController` — FXML controller for the property detail modal; accepts `Property`, optional date range, and `AppContext`

- [ ] **Step 1: Create listing detail FXML**

Create `src/main/resources/com/snoozeshare/ui/guest/listing/listing-detail.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.FlowPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.VBox?>

<ScrollPane fitToWidth="true" maxWidth="700" maxHeight="600"
            xmlns:fx="http://javafx.com/fxml"
            fx:controller="com.snoozeshare.ui.guest.listing.ListingDetailController">
    <VBox spacing="16" styleClass="detail-modal">
        <padding><Insets top="24" right="24" bottom="24" left="24"/></padding>

        <HBox alignment="CENTER_LEFT">
            <Label fx:id="titleLabel" styleClass="page-title"/>
        </HBox>

        <Label fx:id="typeLabel" styleClass="small"/>
        <Label fx:id="addressLabel"/>
        <Label fx:id="descriptionLabel" wrapText="true"/>

        <HBox spacing="24">
            <Label fx:id="capacityLabel"/>
            <Label fx:id="bedroomsLabel"/>
            <Label fx:id="bathroomsLabel"/>
        </HBox>

        <HBox spacing="24">
            <Label fx:id="checkInLabel"/>
            <Label fx:id="checkOutLabel"/>
        </HBox>

        <Label text="Amenities" styleClass="card-title"/>
        <FlowPane fx:id="amenitiesPane" hgap="8" vgap="8"/>

        <Label text="Host" styleClass="card-title"/>
        <Label fx:id="hostLabel"/>

        <VBox fx:id="priceBox" spacing="8" styleClass="price-panel">
            <padding><Insets top="12" right="12" bottom="12" left="12"/></padding>
            <Label fx:id="nightlyRateLabel"/>
            <Label fx:id="nightsLabel"/>
            <Label fx:id="totalLabel" styleClass="card-price"/>
        </VBox>

        <Label text="Reviews" styleClass="card-title"/>
        <Label text="Reviews coming soon." styleClass="small"/>

        <HBox spacing="12" alignment="CENTER_RIGHT">
            <Button fx:id="bookButton" text="Book Now" disable="true" styleClass="button"/>
            <Button text="Close" onAction="#handleClose" styleClass="outline-button"/>
        </HBox>
    </VBox>
</ScrollPane>
```

- [ ] **Step 2: Create ListingDetailController**

Create `src/main/java/com/snoozeshare/ui/guest/listing/ListingDetailController.java`:

```java
package com.snoozeshare.ui.guest.listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.service.PriceBreakdown;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class ListingDetailController {

    @FXML private Label titleLabel;
    @FXML private Label typeLabel;
    @FXML private Label addressLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label capacityLabel;
    @FXML private Label bedroomsLabel;
    @FXML private Label bathroomsLabel;
    @FXML private Label checkInLabel;
    @FXML private Label checkOutLabel;
    @FXML private FlowPane amenitiesPane;
    @FXML private Label hostLabel;
    @FXML private VBox priceBox;
    @FXML private Label nightlyRateLabel;
    @FXML private Label nightsLabel;
    @FXML private Label totalLabel;
    @FXML private Button bookButton;

    private AppContext context;
    private Runnable onClose;

    public void setContext(AppContext context) {
        this.context = context;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a");

    public void populate(Property property, LocalDate checkIn, LocalDate checkOut) {
        titleLabel.setText(property.title());
        typeLabel.setText(property.propertyType().name().replace('_', ' '));
        addressLabel.setText(String.join(", ", property.streetAddress(),
                property.city(), property.region(), property.postalCode()));
        descriptionLabel.setText(property.description());
        capacityLabel.setText(property.maxGuests() + " guests");
        bedroomsLabel.setText(property.bedrooms() + " bedrooms");
        bathroomsLabel.setText(property.bathrooms() + " bathrooms");

        if (property.checkInTime() != null) {
            checkInLabel.setText("Check-in: " + property.checkInTime().format(TIME_FORMAT));
        }
        if (property.checkOutTime() != null) {
            checkOutLabel.setText("Check-out: " + property.checkOutTime().format(TIME_FORMAT));
        }

        amenitiesPane.getChildren().clear();
        for (AmenityType amenity : property.amenities()) {
            Label chip = new Label(amenity.name().replace('_', ' '));
            chip.getStyleClass().add("amenity-chip");
            amenitiesPane.getChildren().add(chip);
        }

        try {
            User host = context.userService().authenticate(null);
            hostLabel.setText("Unknown host");
        } catch (Exception ignored) {
            hostLabel.setText("Unknown host");
        }
        var hostUser = findHost(property);
        if (hostUser != null) {
            hostLabel.setText(hostUser.displayName());
        }

        if (checkIn != null && checkOut != null) {
            PriceBreakdown breakdown = context.listingService()
                    .estimateCost(property.propertyId(), checkIn, checkOut);
            BigDecimal rate = breakdown.nightlyRate().setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = breakdown.totalAmount().setScale(2, RoundingMode.HALF_UP);
            nightlyRateLabel.setText("SGD " + rate + " × " + breakdown.nights() + " nights");
            nightsLabel.setText("");
            totalLabel.setText("Total: SGD " + total);
            priceBox.setVisible(true);
            priceBox.setManaged(true);
        } else {
            BigDecimal rate = property.baseNightlyRate().setScale(2, RoundingMode.HALF_UP);
            nightlyRateLabel.setText("SGD " + rate + " / night");
            nightsLabel.setText("Select dates to see total price");
            totalLabel.setText("");
            priceBox.setVisible(true);
            priceBox.setManaged(true);
        }
    }

    private User findHost(Property property) {
        try {
            return context.userService().listByRole(
                    com.snoozeshare.domain.enums.Role.HOST).stream()
                    .filter(u -> u.userId().equals(property.hostId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception exception) {
            return null;
        }
    }

    @FXML
    private void handleClose() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
```

- [ ] **Step 3: Add detail modal styles to theme.css**

Append to `src/main/resources/com/snoozeshare/ui/common/theme.css`:

```css
/* Detail modal */
.detail-modal {
    -fx-background-color: white;
    -fx-background-radius: 12;
}

.price-panel {
    -fx-background-color: #e8edf5;
    -fx-background-radius: 8;
}

.amenity-chip {
    -fx-background-color: #e8edf5;
    -fx-background-radius: 999;
    -fx-padding: 4 12 4 12;
    -fx-font-size: 11;
}

.modal-overlay {
    -fx-background-color: rgba(0, 0, 0, 0.4);
}
```

- [ ] **Step 4: Wire the detail modal into GuestShellController**

Update the `showExplore()` method in `GuestShellController` to open the detail modal when a property card is clicked:

```java
controller.setOnPropertySelected(property -> {
    showDetailModal(property,
            controller.getCheckIn(), controller.getCheckOut());
});
```

Add a `showDetailModal` method to `GuestShellController`:

```java
private void showDetailModal(Property property, LocalDate checkIn, LocalDate checkOut) {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/snoozeshare/ui/guest/listing/listing-detail.fxml"));
        Node detailView = loader.load();
        ListingDetailController controller = loader.getController();
        controller.setContext(getContext());

        javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane();
        overlay.getStyleClass().add("modal-overlay");
        overlay.getChildren().add(detailView);
        javafx.scene.layout.StackPane.setAlignment(detailView, javafx.geometry.Pos.CENTER);

        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
        root.getChildren().addAll(shellRoot.getCenter(), overlay);
        shellRoot.setCenter(root);

        controller.setOnClose(() -> {
            shellRoot.setCenter(((javafx.scene.layout.StackPane) root)
                    .getChildren().get(0));
        });
        controller.populate(property, checkIn, checkOut);
    } catch (IOException exception) {
        throw new IllegalStateException("Unable to load listing detail", exception);
    }
}
```

- [ ] **Step 5: Add getCheckIn/getCheckOut accessors to GuestSearchController**

Add to `GuestSearchController`:

```java
public LocalDate getCheckIn() {
    return checkInPicker.getValue();
}

public LocalDate getCheckOut() {
    return checkOutPicker.getValue();
}
```

- [ ] **Step 6: Run the app to visually verify the full flow**

Run: `./gradlew run`
Expected: Register as Guest → Explore → search shows cards → clicking a card opens the detail modal overlay with property info, host name, amenity chips, and price breakdown (if dates selected). Close button dismisses the modal.

- [ ] **Step 7: Run all tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/com/snoozeshare/ui/guest/listing/listing-detail.fxml \
       src/main/java/com/snoozeshare/ui/guest/listing/ListingDetailController.java \
       src/main/java/com/snoozeshare/ui/guest/search/GuestSearchController.java \
       src/main/java/com/snoozeshare/ui/guest/GuestShellController.java \
       src/main/resources/com/snoozeshare/ui/common/theme.css
git commit -m "feat: add property detail modal with price breakdown

Clicking a search result opens a modal showing full property details,
host name, amenities, and price breakdown when dates are selected.
Book Now button present but disabled (owned by W3).

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 7: Update PROJECT_STATE.md and done ledger

**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`

**Interfaces:**
- Consumes: nothing
- Produces: updated project state reflecting W2 completion

- [ ] **Step 1: Update W2 status in PROJECT_STATE.md**

Change W2 row to:

```
| W2 | F1 — Listing Search & Property Discovery | Done | [listing-search design](docs/superpowers/specs/2026-09-24-w2-listing-search-design.md) | [listing-search plan](docs/superpowers/plans/2026-09-24-w2-listing-search.md) | Implementation complete: search/filter, detail modal, price breakdown | Awaiting confirmation |
```

- [ ] **Step 2: Add done ledger entry**

Prepend to `docs/project-state/done-ledger.md`:

```markdown
### 2026-09-24 — W2: Listing Search & Property Discovery (F1)

- Extended `SearchCriteria` with `startDate`/`endDate` and introduced `SearchResult` with availability flag
- Implemented `JdbcPropertyRepository` with dynamic search criteria filtering (city substring, guest capacity, ACTIVE only)
- Implemented `JdbcAvailabilityBlockRepository` and `JdbcBookingRepository` (overlap detection, PENDING+CONFIRMED filter)
- Added `RowMappers` for Property, AvailabilityBlock, Booking (including amenities comma-separated parsing)
- Implemented `AvailabilityServiceImpl` (checks both host blocks and confirmed bookings)
- Implemented `ListingServiceImpl` (search with availability partitioning, getDetail, estimateCost)
- Built Guest Search UI with city/dates/guests filters and property result cards
- Built Property Detail modal with full property info, host name, amenities, price breakdown
- Wired all services into `AppContext`
- Backlog items covered: F1.1.1, F1.1.2, F1.2.1, F1.2.2
```

- [ ] **Step 3: Commit**

```bash
git add PROJECT_STATE.md docs/project-state/done-ledger.md
git commit -m "docs: mark W2 as Done and update done ledger

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```
