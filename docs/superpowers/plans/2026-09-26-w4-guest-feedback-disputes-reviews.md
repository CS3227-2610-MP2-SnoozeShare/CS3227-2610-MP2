# W4 — Guest Feedback, Disputes & Reviews — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable guests to file dispute tickets, view their ticket history in the Support tab, and leave reviews on completed stays.

**Architecture:** Implement `fileTicket()` in the existing `TicketServiceImpl`, add `ReviewServiceImpl` + `JdbcReviewRepository` for reviews, add `findByRaisedByUserId` to `TicketRepository`, wire new services in `AppContext`, and build three guest UI screens (ticket filing modal, Support tab with ticket list/detail, review modal) plus Trip Hub integration buttons.

**Tech Stack:** Java 25, JavaFX 25, SQLite via JDBC, JUnit 5

**Spec:** `docs/superpowers/specs/2026-09-26-w4-guest-feedback-disputes-reviews-design.md`

## Global Constraints

- Java 25, JavaFX 25, Gradle build with checkstyle
- All monetary values use `BigDecimal` with `compareTo` in tests (not `equals` — D4)
- All timestamps use `Instant` via injected `Clock`, stored as UTC ISO-8601 with trailing `Z`
- UUIDs stored as `TEXT` in SQLite, converted via `JdbcCodecs.uuid()`
- UI follows Fall Light theme (`agent-theme.css` on `.agent-root`)
- Domain records are immutable Java records
- Only `repository.jdbc.*` may import `java.sql.*`
- Audit every mutating service method via `AuditService.record()`
- The `HOST_PAYOUT` remedy type is hidden from the guest filing form (W4-D1)
- One ticket per guest per booking (W4-D2)
- Reviews are write-once, no edit or delete (W4-D3)

## Review Focus

1. **Filing a ticket on a booking the guest doesn't own** — must throw; the UI should never show the button, but the service must reject it independently.
2. **Filing a ticket outside the 7-day window** — `today > endDate + 7` must be rejected even if the UI hides the button.
3. **Submitting a review on a non-COMPLETED booking** — only `COMPLETED` status allows reviews; `FORCE_COMPLETED` does not (per F3.1.3 "status COMPLETED").
4. **Duplicate review on the same booking** — second `submit()` must throw, not silently overwrite.
5. **Filing a ticket when the stay hasn't ended yet** — a `CONFIRMED` booking with `endDate > today` is not eligible even though the status is CONFIRMED.

Each of these is tested in the task that owns the code (Tasks 1 and 3).

---

### Task 1: Implement `fileTicket()` in `TicketServiceImpl` + add `findByRaisedByUserId` to `TicketRepository`

**Files:**
- Modify: `src/main/java/com/snoozeshare/repository/TicketRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/JdbcTicketRepository.java`
- Modify: `src/test/java/com/snoozeshare/testsupport/Fakes.java` (InMemoryTickets)
- Modify: `src/main/java/com/snoozeshare/service/impl/TicketServiceImpl.java`
- Create: `src/test/java/com/snoozeshare/service/FileTicketTest.java`

**Interfaces:**
- Consumes: `TicketRepository`, `TicketCategoryRepository`, `BookingRepository`, `UserRepository`, `AuditService`, `EventBus`, `Clock`, `NewTicketRequest`, `Ticket`, `TicketOpenedEvent`, `DomainValidation.requireText()`
- Produces: `TicketService.fileTicket()` — working implementation; `TicketRepository.findByRaisedByUserId(UUID)` — returns `List<Ticket>` sorted by `createdAt DESC`; `Fakes.InMemoryTickets.findByRaisedByUserId()` — in-memory implementation

- [ ] **Step 1: Add `findByRaisedByUserId` to `TicketRepository` interface**

Add this method to `src/main/java/com/snoozeshare/repository/TicketRepository.java`:

```java
List<Ticket> findByRaisedByUserId(UUID userId);
```

- [ ] **Step 2: Implement `findByRaisedByUserId` in `JdbcTicketRepository`**

Add to `src/main/java/com/snoozeshare/repository/jdbc/JdbcTicketRepository.java`:

```java
@Override
public List<Ticket> findByRaisedByUserId(UUID userId) {
    try (var statement = connection.prepareStatement(
            "SELECT * FROM tickets WHERE raisedByUserId = ? ORDER BY createdAt DESC")) {
        statement.setString(1, JdbcCodecs.uuid(userId));
        try (var result = statement.executeQuery()) {
            List<Ticket> tickets = new ArrayList<>();
            while (result.next()) {
                tickets.add(RowMappers.ticket(result));
            }
            return tickets;
        }
    } catch (SQLException exception) {
        throw new IllegalStateException("Unable to query tickets by user", exception);
    }
}
```

- [ ] **Step 3: Implement `findByRaisedByUserId` in `Fakes.InMemoryTickets`**

Add to `src/test/java/com/snoozeshare/testsupport/Fakes.java` inside the `InMemoryTickets` class:

```java
@Override
public List<Ticket> findByRaisedByUserId(UUID userId) {
    return store.values().stream()
            .filter(t -> t.raisedByUserId().equals(userId))
            .sorted(Comparator.comparing(Ticket::createdAt).reversed())
            .toList();
}
```

- [ ] **Step 4: Write the failing tests for `fileTicket()`**

Create `src/test/java/com/snoozeshare/service/FileTicketTest.java`:

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.DomainEvent;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.TicketOpenedEvent;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.service.impl.TicketServiceImpl;
import com.snoozeshare.service.requests.NewTicketRequest;
import com.snoozeshare.testsupport.Fakes;

class FileTicketTest {

    private final Fakes.InMemoryTickets tickets = new Fakes.InMemoryTickets();
    private final Fakes.InMemoryCategories categories = new Fakes.InMemoryCategories();
    private final Fakes.RecordingAudit audit = new Fakes.RecordingAudit();
    private final Fakes.RecordingSettlement settlement = new Fakes.RecordingSettlement();
    private final List<DomainEvent> publishedEvents = new ArrayList<>();

    private final UUID guestId = UUID.randomUUID();
    private final UUID listingId = UUID.randomUUID();
    // Clock fixed to Sep 10, 2026 — booking endDate Sep 5 is within the 7-day window
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

    private TicketService service;
    private Booking validBooking;

    private static User guest(UUID id) {
        return new User(id, Role.GUEST, "Guest", "guest@test.com",
                AccountStatus.ACTIVE, null, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        validBooking = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.CONFIRMED, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"), null);

        categories.save(new TicketCategory(UUID.randomUUID(), "Cleanliness", true));

        var users = new Fakes.StubUsers().with(guest(guestId));
        BookingRepository bookings = stubBookings(validBooking);

        EventBus eventBus = new EventBus() {
            @Override
            public <T extends DomainEvent> Subscription subscribe(Class<T> type,
                    java.util.function.Consumer<T> subscriber) {
                return () -> {};
            }

            @Override
            public void publish(DomainEvent event) {
                publishedEvents.add(event);
            }
        };

        service = new TicketServiceImpl(tickets, categories, bookings, users,
                settlement, audit, clock, eventBus);
    }

    private NewTicketRequest validRequest() {
        return new NewTicketRequest(validBooking.bookingId(), "Cleanliness",
                "Dirty bathroom", "The bathroom was not cleaned",
                RemedyType.FULL_REFUND, "See photos");
    }

    @Test
    void successfullyFilesTicketAndPublishesEvent() {
        Ticket filed = service.fileTicket(validRequest(), guestId, Role.GUEST);

        assertNotNull(filed.ticketId());
        assertEquals(validBooking.bookingId(), filed.bookingId());
        assertEquals(guestId, filed.raisedByUserId());
        assertEquals(Role.GUEST, filed.raisedByRole());
        assertEquals("Cleanliness", filed.category());
        assertEquals("Dirty bathroom", filed.title());
        assertEquals("The bathroom was not cleaned", filed.description());
        assertEquals(RemedyType.FULL_REFUND, filed.requestedRemedy());
        assertEquals("See photos", filed.supportingText());
        assertEquals(TicketStatus.OPEN, filed.status());
        assertNotNull(filed.createdAt());

        assertTrue(audit.actions().contains("TICKET_FILED"));
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.get(0) instanceof TicketOpenedEvent);
    }

    @Test
    void rejectsWhenBookingDoesNotExist() {
        var request = new NewTicketRequest(UUID.randomUUID(), "Cleanliness",
                "Title", "Desc", RemedyType.FULL_REFUND, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.fileTicket(request, guestId, Role.GUEST));
    }

    @Test
    void rejectsWhenGuestDoesNotOwnBooking() {
        UUID otherGuest = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.fileTicket(validRequest(), otherGuest, Role.GUEST));
    }

    @Test
    void rejectsWhenStayHasNotEndedYet() {
        // Booking ends Sep 15, clock is Sep 10 — stay not over
        Booking futureBooking = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 15),
                BookingStatus.CONFIRMED, new BigDecimal("100.00"),
                new BigDecimal("700.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"), null);
        service = rebuildService(futureBooking);
        var request = new NewTicketRequest(futureBooking.bookingId(), "Cleanliness",
                "Title", "Desc", RemedyType.FULL_REFUND, null);
        assertThrows(IllegalStateException.class,
                () -> service.fileTicket(request, guestId, Role.GUEST));
    }

    @Test
    void rejectsWhenOutsideDisputeWindow() {
        // Booking ended Aug 20, clock is Sep 10 — well beyond 7 days
        Booking oldBooking = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20),
                BookingStatus.COMPLETED, new BigDecimal("100.00"),
                new BigDecimal("500.00"), Instant.parse("2026-08-10T00:00:00Z"),
                Instant.parse("2026-08-11T00:00:00Z"),
                Instant.parse("2026-08-27T00:00:00Z"));
        service = rebuildService(oldBooking);
        var request = new NewTicketRequest(oldBooking.bookingId(), "Cleanliness",
                "Title", "Desc", RemedyType.FULL_REFUND, null);
        assertThrows(IllegalStateException.class,
                () -> service.fileTicket(request, guestId, Role.GUEST));
    }

    @Test
    void rejectsWhenBookingStatusIsPending() {
        Booking pending = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.PENDING, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                null, null);
        service = rebuildService(pending);
        var request = new NewTicketRequest(pending.bookingId(), "Cleanliness",
                "Title", "Desc", RemedyType.FULL_REFUND, null);
        assertThrows(IllegalStateException.class,
                () -> service.fileTicket(request, guestId, Role.GUEST));
    }

    @Test
    void rejectsWhenCategoryIsInvalid() {
        var request = new NewTicketRequest(validBooking.bookingId(), "NonExistent",
                "Title", "Desc", RemedyType.FULL_REFUND, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.fileTicket(request, guestId, Role.GUEST));
    }

    @Test
    void rejectsBlankTitle() {
        var request = new NewTicketRequest(validBooking.bookingId(), "Cleanliness",
                "  ", "Desc", RemedyType.FULL_REFUND, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.fileTicket(request, guestId, Role.GUEST));
    }

    @Test
    void rejectsBlankDescription() {
        var request = new NewTicketRequest(validBooking.bookingId(), "Cleanliness",
                "Title", "", RemedyType.FULL_REFUND, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.fileTicket(request, guestId, Role.GUEST));
    }

    @Test
    void rejectsDuplicateTicketOnSameBooking() {
        service.fileTicket(validRequest(), guestId, Role.GUEST);
        assertThrows(IllegalStateException.class,
                () -> service.fileTicket(validRequest(), guestId, Role.GUEST));
    }

    @Test
    void allowsTicketOnCompletedBooking() {
        Booking completed = new Booking(validBooking.bookingId(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.COMPLETED, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-09-05T15:00:00Z"));
        service = rebuildService(completed);
        Ticket filed = service.fileTicket(
                new NewTicketRequest(completed.bookingId(), "Cleanliness",
                        "Title", "Desc", RemedyType.FULL_REFUND, null),
                guestId, Role.GUEST);
        assertEquals(TicketStatus.OPEN, filed.status());
    }

    private TicketService rebuildService(Booking booking) {
        var users = new Fakes.StubUsers().with(guest(guestId));
        EventBus eventBus = new EventBus() {
            @Override
            public <T extends DomainEvent> Subscription subscribe(Class<T> type,
                    java.util.function.Consumer<T> subscriber) {
                return () -> {};
            }

            @Override
            public void publish(DomainEvent event) {
                publishedEvents.add(event);
            }
        };
        return new TicketServiceImpl(tickets, categories, stubBookings(booking), users,
                settlement, audit, clock, eventBus);
    }

    private static BookingRepository stubBookings(Booking booking) {
        return new BookingRepository() {
            @Override
            public Optional<Booking> findById(UUID id) {
                return id.equals(booking.bookingId()) ? Optional.of(booking) : Optional.empty();
            }

            @Override
            public List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end) {
                return List.of();
            }

            @Override
            public List<Booking> findByGuest(UUID guestId) {
                return List.of(booking);
            }

            @Override
            public List<Booking> findByHostPending(UUID hostId) {
                return List.of();
            }

            @Override
            public Booking save(Booking b) {
                return b;
            }
        };
    }
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: `./gradlew test --tests "com.snoozeshare.service.FileTicketTest" -i`

Expected: compilation errors — `TicketServiceImpl` constructor doesn't accept `EventBus` yet, `findByRaisedByUserId` not implemented in `InMemoryTickets`, and `fileTicket()` throws `UnsupportedOperationException`.

- [ ] **Step 6: Implement `fileTicket()` in `TicketServiceImpl`**

The constructor needs an `EventBus` parameter. Add the field and update the constructor in `src/main/java/com/snoozeshare/service/impl/TicketServiceImpl.java`:

Add field:
```java
private final EventBus eventBus;
```

Update constructor to accept `EventBus eventBus` as the last parameter and assign `this.eventBus = eventBus;`.

Replace the `fileTicket()` method body:

```java
@Override
public Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole) {
    Booking booking = bookings.findById(request.bookingId())
            .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));

    if (!booking.guestId().equals(raisedByUserId)) {
        throw new IllegalArgumentException("Only the booking guest may file a ticket");
    }

    LocalDate today = LocalDate.now(clock);
    boolean stayEnded = !booking.endDate().isAfter(today);
    boolean statusEligible = booking.status() == BookingStatus.CONFIRMED
            || booking.status() == BookingStatus.COMPLETED;
    if (!statusEligible || (booking.status() == BookingStatus.CONFIRMED && !stayEnded)) {
        throw new IllegalStateException("Booking is not eligible for a dispute");
    }

    if (today.isAfter(booking.endDate().plusDays(7))) {
        throw new IllegalStateException("Dispute window has closed (7 days after stay end)");
    }

    String category = DomainValidation.requireText(request.category(), "category");
    boolean validCategory = categories.findActive().stream()
            .anyMatch(c -> c.label().equalsIgnoreCase(category.trim()));
    if (!validCategory) {
        throw new IllegalArgumentException("Invalid ticket category");
    }

    String title = DomainValidation.requireText(request.title(), "title");
    String description = DomainValidation.requireText(request.description(), "description");

    boolean duplicate = tickets.findByRaisedByUserId(raisedByUserId).stream()
            .anyMatch(t -> t.bookingId().equals(request.bookingId()));
    if (duplicate) {
        throw new IllegalStateException("A ticket already exists for this booking");
    }

    Instant now = clock.instant();
    Ticket ticket = new Ticket(UUID.randomUUID(), request.bookingId(), raisedByUserId,
            raisedByRole, category.trim(), title.trim(), description.trim(),
            request.requestedRemedy(), request.supportingText(),
            TicketStatus.OPEN, null, null, null, now, null);

    Ticket saved = tickets.save(ticket);
    audit.record(raisedByUserId, "TICKET_FILED", "Ticket", saved.ticketId(), null, saved);
    eventBus.publish(new TicketOpenedEvent(saved.ticketId(), raisedByUserId, now));
    return saved;
}
```

Add imports for `LocalDate`, `BookingStatus`, `TicketOpenedEvent`, `EventBus`.

- [ ] **Step 7: Update `AppContext` to pass `EventBus` to `TicketServiceImpl`**

In `src/main/java/com/snoozeshare/app/AppContext.java`, change the `TicketServiceImpl` constructor call to include `eventBus` as the last argument:

```java
this.ticketService = new TicketServiceImpl(ticketRepo, categoryRepo, bookingRepo, users,
        settlementService, auditService, clock, eventBus);
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew test --tests "com.snoozeshare.service.FileTicketTest" -i`

Expected: all 10 tests PASS.

- [ ] **Step 9: Run full test suite to check for regressions**

Run: `./gradlew test`

Expected: all existing tests pass. The existing `TicketServiceTest` setUp needs updating to pass `EventBus` — if it fails, add a no-op `EventBus` implementation to its constructor call:

```java
EventBus noopBus = new EventBus() {
    @Override
    public <T extends DomainEvent> Subscription subscribe(Class<T> type,
            java.util.function.Consumer<T> subscriber) {
        return () -> {};
    }
    @Override
    public void publish(DomainEvent event) {}
};
```

Pass `noopBus` as the last argument to the `TicketServiceImpl` constructor in the `setUp()` method of `TicketServiceTest`, `TicketServiceCategoryTest`, and `TicketServiceIntegrationTest`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/snoozeshare/repository/TicketRepository.java \
       src/main/java/com/snoozeshare/repository/jdbc/JdbcTicketRepository.java \
       src/test/java/com/snoozeshare/testsupport/Fakes.java \
       src/main/java/com/snoozeshare/service/impl/TicketServiceImpl.java \
       src/main/java/com/snoozeshare/app/AppContext.java \
       src/test/java/com/snoozeshare/service/FileTicketTest.java \
       src/test/java/com/snoozeshare/service/TicketServiceTest.java \
       src/test/java/com/snoozeshare/service/TicketServiceCategoryTest.java \
       src/test/java/com/snoozeshare/service/TicketServiceIntegrationTest.java
git commit -m "Implement fileTicket() with validation and event publishing

Adds findByRaisedByUserId to TicketRepository, implements all 8
validation rules (ownership, status, 7-day window, category, duplicate),
publishes TicketOpenedEvent, and audits TICKET_FILED."
```

---

### Task 2: Implement `ReviewServiceImpl` + `JdbcReviewRepository`

**Files:**
- Create: `src/main/java/com/snoozeshare/service/impl/ReviewServiceImpl.java`
- Create: `src/main/java/com/snoozeshare/repository/jdbc/JdbcReviewRepository.java`
- Modify: `src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java` (add `review()` mapper)
- Create: `src/test/java/com/snoozeshare/service/ReviewServiceTest.java`

**Interfaces:**
- Consumes: `ReviewRepository.findByBookingId(UUID)`, `ReviewRepository.save(Review)`, `BookingRepository.findById(UUID)`, `AuditService.record()`, `Clock`
- Produces: `ReviewServiceImpl` — implements `ReviewService.submit(UUID bookingId, UUID guestId, int rating, String comment)`; `JdbcReviewRepository` — JDBC implementation of `ReviewRepository`; `RowMappers.review(ResultSet)` — row mapper for `Review` records

- [ ] **Step 1: Add `review()` mapper to `RowMappers`**

Add to `src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java`:

```java
public static Review review(ResultSet result) throws SQLException {
    return new Review(
            JdbcCodecs.uuid(result.getString("reviewId")),
            JdbcCodecs.uuid(result.getString("bookingId")),
            JdbcCodecs.uuid(result.getString("guestId")),
            result.getInt("rating"),
            result.getString("comment"),
            JdbcCodecs.instant(result.getString("createdAt")));
}
```

Add import for `com.snoozeshare.domain.model.Review`.

- [ ] **Step 2: Create `JdbcReviewRepository`**

Create `src/main/java/com/snoozeshare/repository/jdbc/JdbcReviewRepository.java`:

```java
package com.snoozeshare.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.Review;
import com.snoozeshare.repository.ReviewRepository;
import com.snoozeshare.repository.jdbc.support.JdbcCodecs;
import com.snoozeshare.repository.jdbc.support.RowMappers;

public final class JdbcReviewRepository implements ReviewRepository {

    private final Connection connection;

    public JdbcReviewRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<Review> findByBookingId(UUID bookingId) {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM reviews WHERE bookingId = ?")) {
            statement.setString(1, JdbcCodecs.uuid(bookingId));
            try (var result = statement.executeQuery()) {
                List<Review> reviews = new ArrayList<>();
                while (result.next()) {
                    reviews.add(RowMappers.review(result));
                }
                return reviews;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query reviews", exception);
        }
    }

    @Override
    public Review save(Review review) {
        try (var statement = connection.prepareStatement(
                "INSERT INTO reviews (reviewId, bookingId, guestId, rating, comment, createdAt) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, JdbcCodecs.uuid(review.reviewId()));
            statement.setString(2, JdbcCodecs.uuid(review.bookingId()));
            statement.setString(3, JdbcCodecs.uuid(review.guestId()));
            statement.setInt(4, review.rating());
            statement.setString(5, review.comment());
            statement.setString(6, JdbcCodecs.instant(review.createdAt()));
            statement.executeUpdate();
            return review;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save review", exception);
        }
    }
}
```

- [ ] **Step 3: Create `ReviewServiceImpl`**

Create `src/main/java/com/snoozeshare/service/impl/ReviewServiceImpl.java`:

```java
package com.snoozeshare.service.impl;

import java.time.Clock;
import java.util.UUID;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Review;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.ReviewRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.ReviewService;

public final class ReviewServiceImpl implements ReviewService {

    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final AuditService audit;
    private final Clock clock;

    public ReviewServiceImpl(BookingRepository bookings, ReviewRepository reviews,
                             AuditService audit, Clock clock) {
        this.bookings = bookings;
        this.reviews = reviews;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Review submit(UUID bookingId, UUID guestId, int rating, String comment) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));

        if (!booking.guestId().equals(guestId)) {
            throw new IllegalArgumentException("Only the booking guest may leave a review");
        }

        if (booking.status() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Reviews can only be submitted for completed stays");
        }

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        if (!reviews.findByBookingId(bookingId).isEmpty()) {
            throw new IllegalStateException("A review already exists for this booking");
        }

        Review review = new Review(UUID.randomUUID(), bookingId, guestId, rating, comment,
                clock.instant());
        Review saved = reviews.save(review);
        audit.record(guestId, "REVIEW_SUBMITTED", "Review", saved.reviewId(), null, saved);
        return saved;
    }
}
```

- [ ] **Step 4: Write the tests for `ReviewServiceImpl`**

Create `src/test/java/com/snoozeshare/service/ReviewServiceTest.java`:

```java
package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Review;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.ReviewRepository;
import com.snoozeshare.service.impl.ReviewServiceImpl;
import com.snoozeshare.testsupport.Fakes;

class ReviewServiceTest {

    private final Fakes.RecordingAudit audit = new Fakes.RecordingAudit();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC);

    private final UUID guestId = UUID.randomUUID();
    private final UUID listingId = UUID.randomUUID();
    private Booking completedBooking;
    private InMemoryReviews reviews;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        completedBooking = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.COMPLETED, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z"));

        reviews = new InMemoryReviews();
        BookingRepository bookings = stubBookings(completedBooking);
        service = new ReviewServiceImpl(bookings, reviews, audit, clock);
    }

    @Test
    void successfullySubmitsReview() {
        Review review = service.submit(completedBooking.bookingId(), guestId, 4, "Great stay!");

        assertNotNull(review.reviewId());
        assertEquals(completedBooking.bookingId(), review.bookingId());
        assertEquals(guestId, review.guestId());
        assertEquals(4, review.rating());
        assertEquals("Great stay!", review.comment());
        assertNotNull(review.createdAt());
        assertTrue(audit.actions().contains("REVIEW_SUBMITTED"));
    }

    @Test
    void allowsNullComment() {
        Review review = service.submit(completedBooking.bookingId(), guestId, 5, null);
        assertEquals(5, review.rating());
    }

    @Test
    void rejectsBookingNotFound() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(UUID.randomUUID(), guestId, 4, "Nice"));
    }

    @Test
    void rejectsWhenGuestDoesNotOwnBooking() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(completedBooking.bookingId(), UUID.randomUUID(), 4, "Nice"));
    }

    @Test
    void rejectsNonCompletedBooking() {
        Booking confirmed = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.CONFIRMED, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"), null);
        service = new ReviewServiceImpl(stubBookings(confirmed), reviews, audit, clock);
        assertThrows(IllegalStateException.class,
                () -> service.submit(confirmed.bookingId(), guestId, 4, "Nice"));
    }

    @Test
    void rejectsForceCompletedBooking() {
        Booking forceCompleted = new Booking(UUID.randomUUID(), listingId, guestId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                BookingStatus.FORCE_COMPLETED, new BigDecimal("100.00"),
                new BigDecimal("400.00"), Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z"));
        service = new ReviewServiceImpl(stubBookings(forceCompleted), reviews, audit, clock);
        assertThrows(IllegalStateException.class,
                () -> service.submit(forceCompleted.bookingId(), guestId, 4, "Nice"));
    }

    @Test
    void rejectsRatingBelow1() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(completedBooking.bookingId(), guestId, 0, "Bad"));
    }

    @Test
    void rejectsRatingAbove5() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(completedBooking.bookingId(), guestId, 6, "Good"));
    }

    @Test
    void rejectsDuplicateReview() {
        service.submit(completedBooking.bookingId(), guestId, 4, "First review");
        assertThrows(IllegalStateException.class,
                () -> service.submit(completedBooking.bookingId(), guestId, 5, "Second review"));
    }

    private static BookingRepository stubBookings(Booking booking) {
        return new BookingRepository() {
            @Override
            public Optional<Booking> findById(UUID id) {
                return id.equals(booking.bookingId()) ? Optional.of(booking) : Optional.empty();
            }

            @Override
            public List<Booking> findOverlapping(UUID propertyId, LocalDate start, LocalDate end) {
                return List.of();
            }

            @Override
            public List<Booking> findByGuest(UUID guestId) {
                return List.of(booking);
            }

            @Override
            public List<Booking> findByHostPending(UUID hostId) {
                return List.of();
            }

            @Override
            public Booking save(Booking b) {
                return b;
            }
        };
    }

    private static final class InMemoryReviews implements ReviewRepository {
        private final Map<UUID, Review> store = new HashMap<>();

        @Override
        public List<Review> findByBookingId(UUID bookingId) {
            return store.values().stream()
                    .filter(r -> r.bookingId().equals(bookingId)).toList();
        }

        @Override
        public Review save(Review review) {
            store.put(review.reviewId(), review);
            return review;
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.snoozeshare.service.ReviewServiceTest" -i`

Expected: all 9 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/snoozeshare/service/impl/ReviewServiceImpl.java \
       src/main/java/com/snoozeshare/repository/jdbc/JdbcReviewRepository.java \
       src/main/java/com/snoozeshare/repository/jdbc/support/RowMappers.java \
       src/test/java/com/snoozeshare/service/ReviewServiceTest.java
git commit -m "Add ReviewServiceImpl and JdbcReviewRepository

Validates booking ownership, COMPLETED status, rating range 1-5, and
one-review-per-booking. Audits REVIEW_SUBMITTED."
```

---

### Task 3: Wire `ReviewService` into `AppContext`

**Files:**
- Modify: `src/main/java/com/snoozeshare/app/AppContext.java`

**Interfaces:**
- Consumes: `JdbcReviewRepository(Connection)`, `ReviewServiceImpl(BookingRepository, ReviewRepository, AuditService, Clock)`
- Produces: `AppContext.reviewService()` — accessor for `ReviewService`

- [ ] **Step 1: Add `ReviewService` field and wiring to `AppContext`**

In `src/main/java/com/snoozeshare/app/AppContext.java`:

Add field:
```java
private final ReviewService reviewService;
```

Add imports:
```java
import com.snoozeshare.repository.jdbc.JdbcReviewRepository;
import com.snoozeshare.service.ReviewService;
import com.snoozeshare.service.impl.ReviewServiceImpl;
```

In the constructor, after the `ticketService` wiring block, add:
```java
JdbcReviewRepository reviewRepo = new JdbcReviewRepository(connection);
this.reviewService = new ReviewServiceImpl(bookingRepo, reviewRepo, auditService, clock);
```

Add accessor:
```java
public ReviewService reviewService() {
    return reviewService;
}
```

- [ ] **Step 2: Build to verify wiring compiles**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/snoozeshare/app/AppContext.java
git commit -m "Wire ReviewService into AppContext"
```

---

### Task 4: Guest UI — Ticket Filing Modal

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/guest/tickets/TicketFilingController.java`
- Create: `src/main/resources/com/snoozeshare/ui/guest/tickets/ticket-filing.fxml`

**Interfaces:**
- Consumes: `AppContext.ticketService().listCategories()`, `AppContext.ticketService().fileTicket()`, `NewTicketRequest`, `RemedyType`
- Produces: `TicketFilingController` — JavaFX controller with `configure(AppContext, UUID bookingId, Runnable onClose)` and a form that files a ticket

- [ ] **Step 1: Create the FXML layout**

Create `src/main/resources/com/snoozeshare/ui/guest/tickets/ticket-filing.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TextArea?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" maxWidth="480" styleClass="detail-modal"
      xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.guest.tickets.TicketFilingController">
    <padding><Insets top="24" right="24" bottom="24" left="24"/></padding>

    <Label text="File a Dispute" styleClass="page-title"/>
    <Label text="Tell us what went wrong with your stay." styleClass="small"/>

    <Label text="Category" styleClass="card-title"/>
    <ComboBox fx:id="categoryCombo" maxWidth="Infinity" promptText="Select a category"/>

    <Label text="Title" styleClass="card-title"/>
    <TextField fx:id="titleField" promptText="Brief summary of the issue"/>

    <Label text="Description" styleClass="card-title"/>
    <TextArea fx:id="descriptionArea" promptText="Describe the issue in detail"
              prefRowCount="3" wrapText="true"/>

    <Label text="Requested Remedy" styleClass="card-title"/>
    <ComboBox fx:id="remedyCombo" maxWidth="Infinity" promptText="What resolution do you seek?"/>

    <Label text="Supporting Information (optional)" styleClass="card-title"/>
    <TextArea fx:id="supportingArea" promptText="Any additional details or evidence"
              prefRowCount="2" wrapText="true"/>

    <Label fx:id="statusLabel" styleClass="error-message" visible="false" managed="false"/>

    <HBox spacing="12">
        <Button text="Submit" styleClass="button" onAction="#handleSubmit"/>
        <Button text="Cancel" styleClass="outline-button" onAction="#handleCancel"/>
    </HBox>
</VBox>
```

- [ ] **Step 2: Create the controller**

Create `src/main/java/com/snoozeshare/ui/guest/tickets/TicketFilingController.java`:

```java
package com.snoozeshare.ui.guest.tickets;

import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.service.requests.NewTicketRequest;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public final class TicketFilingController {

    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> remedyCombo;
    @FXML private TextArea supportingArea;
    @FXML private Label statusLabel;

    private AppContext context;
    private UUID bookingId;
    private Runnable onClose;

    public void configure(AppContext context, UUID bookingId, Runnable onClose) {
        this.context = context;
        this.bookingId = bookingId;
        this.onClose = onClose;

        categoryCombo.getItems().clear();
        for (TicketCategory cat : context.ticketService().listCategories()) {
            categoryCombo.getItems().add(cat.label());
        }

        remedyCombo.getItems().clear();
        remedyCombo.getItems().addAll("Full Refund", "Partial Refund", "Other");
    }

    @FXML
    private void handleSubmit() {
        String category = categoryCombo.getValue();
        if (category == null || category.isBlank()) {
            showError("Please select a category.");
            return;
        }

        String title = titleField.getText();
        if (title == null || title.isBlank()) {
            showError("Please enter a title.");
            return;
        }

        String description = descriptionArea.getText();
        if (description == null || description.isBlank()) {
            showError("Please enter a description.");
            return;
        }

        String remedyLabel = remedyCombo.getValue();
        if (remedyLabel == null) {
            showError("Please select a requested remedy.");
            return;
        }

        RemedyType remedy = switch (remedyLabel) {
            case "Full Refund" -> RemedyType.FULL_REFUND;
            case "Partial Refund" -> RemedyType.PARTIAL_REFUND;
            default -> RemedyType.OTHER;
        };

        String supporting = supportingArea.getText();
        if (supporting != null && supporting.isBlank()) {
            supporting = null;
        }

        try {
            UUID guestId = context.session().currentUser().orElseThrow().userId();
            NewTicketRequest request = new NewTicketRequest(bookingId, category,
                    title.trim(), description.trim(), remedy, supporting);
            context.ticketService().fileTicket(request, guestId, Role.GUEST);
            onClose.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        onClose.run();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/snoozeshare/ui/guest/tickets/TicketFilingController.java \
       src/main/resources/com/snoozeshare/ui/guest/tickets/ticket-filing.fxml
git commit -m "Add ticket filing modal UI for guests

Category dropdown, title, description, remedy selection, and
supporting text fields with client-side validation."
```

---

### Task 5: Guest UI — Review Modal

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/guest/trips/ReviewDialogController.java`
- Create: `src/main/resources/com/snoozeshare/ui/guest/trips/review-dialog.fxml`

**Interfaces:**
- Consumes: `AppContext.reviewService().submit(UUID, UUID, int, String)`
- Produces: `ReviewDialogController` — JavaFX controller with `configure(AppContext, UUID bookingId, Runnable onClose)` and a form that submits a review

- [ ] **Step 1: Create the FXML layout**

Create `src/main/resources/com/snoozeshare/ui/guest/trips/review-dialog.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TextArea?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="16" maxWidth="400" styleClass="detail-modal"
      xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.snoozeshare.ui.guest.trips.ReviewDialogController">
    <padding><Insets top="24" right="24" bottom="24" left="24"/></padding>

    <Label text="Leave a Review" styleClass="page-title"/>
    <Label text="Share your experience with this stay." styleClass="small"/>

    <Label text="Rating" styleClass="card-title"/>
    <HBox fx:id="starBar" spacing="8"/>

    <Label text="Comment (optional)" styleClass="card-title"/>
    <TextArea fx:id="commentArea" promptText="Tell us about your stay"
              prefRowCount="3" wrapText="true"/>

    <Label fx:id="statusLabel" styleClass="error-message" visible="false" managed="false"/>

    <HBox spacing="12">
        <Button text="Submit Review" styleClass="button" onAction="#handleSubmit"/>
        <Button text="Cancel" styleClass="outline-button" onAction="#handleCancel"/>
    </HBox>
</VBox>
```

- [ ] **Step 2: Create the controller**

Create `src/main/java/com/snoozeshare/ui/guest/trips/ReviewDialogController.java`:

```java
package com.snoozeshare.ui.guest.trips;

import java.util.UUID;

import com.snoozeshare.app.AppContext;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

public final class ReviewDialogController {

    @FXML private HBox starBar;
    @FXML private TextArea commentArea;
    @FXML private Label statusLabel;

    private AppContext context;
    private UUID bookingId;
    private Runnable onClose;
    private int selectedRating;

    public void configure(AppContext context, UUID bookingId, Runnable onClose) {
        this.context = context;
        this.bookingId = bookingId;
        this.onClose = onClose;
        this.selectedRating = 0;
        buildStarButtons();
    }

    private void buildStarButtons() {
        starBar.getChildren().clear();
        for (int i = 1; i <= 5; i++) {
            Button star = new Button("\u2605");
            star.getStyleClass().add("outline-button");
            star.setStyle("-fx-font-size: 20px; -fx-min-width: 40px;");
            final int rating = i;
            star.setOnAction(event -> selectRating(rating));
            starBar.getChildren().add(star);
        }
    }

    private void selectRating(int rating) {
        this.selectedRating = rating;
        for (int i = 0; i < starBar.getChildren().size(); i++) {
            Button star = (Button) starBar.getChildren().get(i);
            if (i < rating) {
                star.setStyle("-fx-font-size: 20px; -fx-min-width: 40px; -fx-text-fill: #d4a017;");
            } else {
                star.setStyle("-fx-font-size: 20px; -fx-min-width: 40px;");
            }
        }
    }

    @FXML
    private void handleSubmit() {
        if (selectedRating == 0) {
            showError("Please select a rating.");
            return;
        }

        String comment = commentArea.getText();
        if (comment != null && comment.isBlank()) {
            comment = null;
        }

        try {
            UUID guestId = context.session().currentUser().orElseThrow().userId();
            context.reviewService().submit(bookingId, guestId, selectedRating, comment);
            onClose.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        onClose.run();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/snoozeshare/ui/guest/trips/ReviewDialogController.java \
       src/main/resources/com/snoozeshare/ui/guest/trips/review-dialog.fxml
git commit -m "Add review modal UI for guests

Star rating buttons (1-5) with visual highlight, optional comment
textarea, submit/cancel actions."
```

---

### Task 6: Guest UI — Support Tab (Ticket History + Detail)

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/guest/tickets/TicketHistoryController.java`
- Create: `src/main/resources/com/snoozeshare/ui/guest/tickets/ticket-history.fxml`
- Modify: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`

**Interfaces:**
- Consumes: `AppContext.ticketService()` (needs `findByRaisedByUserId` — accessed via `TicketRepository` through a new convenience method or directly via the repository exposed through context), `TicketOpenedEvent`, `TicketResolvedEvent`
- Produces: `TicketHistoryController` — JavaFX controller with `setContext(AppContext)`, renders ticket list and inline detail; `GuestShellController.showSupport()` loads the ticket history screen

Note: `TicketService` doesn't expose `findByRaisedByUserId` directly. We need to add a method to the service. Add `List<Ticket> myTickets(UUID guestId)` to `TicketService` and implement it in `TicketServiceImpl`.

- [ ] **Step 1: Add `myTickets` to `TicketService` interface**

Add to `src/main/java/com/snoozeshare/service/TicketService.java`:

```java
/** Returns all tickets filed by the given user, newest first. */
List<Ticket> myTickets(UUID userId);
```

- [ ] **Step 2: Implement `myTickets` in `TicketServiceImpl`**

Add to `src/main/java/com/snoozeshare/service/impl/TicketServiceImpl.java`:

```java
@Override
public List<Ticket> myTickets(UUID userId) {
    return tickets.findByRaisedByUserId(userId);
}
```

- [ ] **Step 3: Create the FXML layout for ticket history**

Create `src/main/resources/com/snoozeshare/ui/guest/tickets/ticket-history.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>

<StackPane fx:id="historyRoot" xmlns:fx="http://javafx.com/fxml"
           fx:controller="com.snoozeshare.ui.guest.tickets.TicketHistoryController">
    <VBox spacing="16">
        <padding><Insets top="24" right="24" bottom="24" left="24"/></padding>

        <Label text="Support" styleClass="page-title"/>
        <Label text="View and track your dispute tickets." styleClass="small"/>

        <ScrollPane fitToWidth="true" VBox.vgrow="ALWAYS">
            <VBox fx:id="ticketContainer" spacing="12">
                <padding><Insets top="8" right="8" bottom="8" left="8"/></padding>
            </VBox>
        </ScrollPane>

        <Label fx:id="emptyLabel" text="No tickets filed yet." styleClass="small"
               visible="false" managed="false"/>
    </VBox>
</StackPane>
```

- [ ] **Step 4: Create the controller**

Create `src/main/java/com/snoozeshare/ui/guest/tickets/TicketHistoryController.java`:

```java
package com.snoozeshare.ui.guest.tickets;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.TicketOpenedEvent;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class TicketHistoryController {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    @FXML private StackPane historyRoot;
    @FXML private VBox ticketContainer;
    @FXML private Label emptyLabel;

    private AppContext context;
    private final List<Subscription> subscriptions = new ArrayList<>();

    public void setContext(AppContext context) {
        this.context = context;
        loadTickets();
        subscribeToEvents();
    }

    public void cleanup() {
        for (Subscription sub : subscriptions) {
            sub.unsubscribe();
        }
        subscriptions.clear();
    }

    private void loadTickets() {
        var userId = context.session().currentUser().orElseThrow().userId();
        List<Ticket> tickets = context.ticketService().myTickets(userId);

        ticketContainer.getChildren().clear();
        if (tickets.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
        } else {
            emptyLabel.setVisible(false);
            emptyLabel.setManaged(false);
            for (Ticket ticket : tickets) {
                ticketContainer.getChildren().add(buildTicketCard(ticket));
            }
        }
    }

    private Node buildTicketCard(Ticket ticket) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("trip-card");
        card.setStyle("-fx-cursor: hand;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(ticket.title());
        title.getStyleClass().add("card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label status = new Label(formatStatus(ticket.status()));
        status.getStyleClass().addAll("trip-status", statusStyleClass(ticket.status()));

        topRow.getChildren().addAll(title, spacer, status);

        Label category = new Label(ticket.category());
        category.getStyleClass().add("small");

        Label date = new Label("Filed: " + ticket.createdAt()
                .atZone(ZoneId.systemDefault()).format(DATE_FORMAT));
        date.getStyleClass().add("small");

        card.getChildren().addAll(topRow, category, date);
        card.setOnMouseClicked(event -> showDetail(ticket));

        return card;
    }

    private void showDetail(Ticket ticket) {
        VBox detail = new VBox(12);
        detail.setPadding(new Insets(24));
        detail.getStyleClass().add("detail-modal");
        detail.setMaxWidth(520);

        Label title = new Label(ticket.title());
        title.getStyleClass().add("page-title");

        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label categoryLabel = new Label(ticket.category());
        categoryLabel.getStyleClass().add("small");
        Label statusLabel = new Label(formatStatus(ticket.status()));
        statusLabel.getStyleClass().addAll("trip-status", statusStyleClass(ticket.status()));
        metaRow.getChildren().addAll(categoryLabel, statusLabel);

        Label descHeader = new Label("Description");
        descHeader.getStyleClass().add("card-title");
        Label description = new Label(ticket.description());
        description.setWrapText(true);

        detail.getChildren().addAll(title, metaRow, descHeader, description);

        Label remedyHeader = new Label("Requested Remedy");
        remedyHeader.getStyleClass().add("card-title");
        Label remedy = new Label(formatRemedy(ticket.requestedRemedy()));
        detail.getChildren().addAll(remedyHeader, remedy);

        if (ticket.supportingText() != null && !ticket.supportingText().isBlank()) {
            Label supportHeader = new Label("Supporting Information");
            supportHeader.getStyleClass().add("card-title");
            Label support = new Label(ticket.supportingText());
            support.setWrapText(true);
            detail.getChildren().addAll(supportHeader, support);
        }

        Label filedDate = new Label("Filed: " + ticket.createdAt()
                .atZone(ZoneId.systemDefault()).format(DATE_FORMAT));
        filedDate.getStyleClass().add("small");
        detail.getChildren().add(filedDate);

        if (ticket.resolutionReason() != null) {
            Label resHeader = new Label("Resolution");
            resHeader.getStyleClass().add("card-title");
            Label resolution = new Label(ticket.resolutionReason());
            resolution.setWrapText(true);
            Label resolvedDate = new Label("Resolved: " + ticket.resolvedAt()
                    .atZone(ZoneId.systemDefault()).format(DATE_FORMAT));
            resolvedDate.getStyleClass().add("small");
            detail.getChildren().addAll(resHeader, resolution, resolvedDate);
        }

        Button back = new Button("Back to Tickets");
        back.getStyleClass().add("outline-button");
        back.setOnAction(event -> {
            historyRoot.getChildren().remove(historyRoot.getChildren().size() - 1);
            loadTickets();
        });
        detail.getChildren().add(back);

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");
        overlay.getChildren().add(detail);
        StackPane.setAlignment(detail, Pos.CENTER);
        historyRoot.getChildren().add(overlay);
    }

    private static String formatStatus(TicketStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_REVIEW -> "In Review";
            case RESOLVED_APPROVED -> "Approved";
            case RESOLVED_REJECTED -> "Rejected";
        };
    }

    private static String statusStyleClass(TicketStatus status) {
        return switch (status) {
            case OPEN -> "status-pending";
            case IN_REVIEW -> "status-confirmed";
            case RESOLVED_APPROVED -> "status-completed";
            case RESOLVED_REJECTED -> "status-cancelled";
        };
    }

    private static String formatRemedy(com.snoozeshare.domain.enums.RemedyType type) {
        return switch (type) {
            case FULL_REFUND -> "Full Refund";
            case PARTIAL_REFUND -> "Partial Refund";
            case HOST_PAYOUT -> "Host Payout";
            case OTHER -> "Other";
        };
    }

    private void subscribeToEvents() {
        if (context.eventBus() == null) {
            return;
        }
        subscriptions.add(context.eventBus().subscribe(TicketOpenedEvent.class,
                event -> Platform.runLater(this::loadTickets)));
        subscriptions.add(context.eventBus().subscribe(TicketResolvedEvent.class,
                event -> Platform.runLater(this::loadTickets)));
    }
}
```

- [ ] **Step 5: Update `GuestShellController.showSupport()` to load ticket history**

In `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`:

Add field:
```java
private TicketHistoryController ticketController;
```

Add import:
```java
import com.snoozeshare.ui.guest.tickets.TicketHistoryController;
```

Add cleanup method:
```java
private void cleanupTicketController() {
    if (ticketController != null) {
        ticketController.cleanup();
        ticketController = null;
    }
}
```

Replace the `showSupport()` method body:
```java
@FXML
private void showSupport() {
    selectTab(supportTab);
    cleanupTripController();
    cleanupWalletController();
    cleanupTicketController();
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/snoozeshare/ui/guest/tickets/ticket-history.fxml"));
        Node supportView = loader.load();
        ticketController = loader.getController();
        ticketController.setContext(getContext());
        shellRoot.setCenter(supportView);
    } catch (IOException exception) {
        throw new IllegalStateException("Unable to load ticket history", exception);
    }
}
```

Also add `cleanupTicketController();` calls at the start of `showExplore()`, `showMyTrips()`, and `showWallet()`.

- [ ] **Step 6: Build to verify compilation**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/snoozeshare/service/TicketService.java \
       src/main/java/com/snoozeshare/service/impl/TicketServiceImpl.java \
       src/main/java/com/snoozeshare/ui/guest/tickets/TicketHistoryController.java \
       src/main/resources/com/snoozeshare/ui/guest/tickets/ticket-history.fxml \
       src/main/java/com/snoozeshare/ui/guest/GuestShellController.java
git commit -m "Add Support tab with ticket history and detail view

Lists guest's tickets newest-first with status badges, click-to-view
detail with resolution info. Subscribes to ticket events for live
refresh. Adds myTickets() to TicketService."
```

---

### Task 7: Trip Hub Integration — File Dispute and Leave Review Buttons

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/guest/trips/TripDashboardController.java`

**Interfaces:**
- Consumes: `AppContext.ticketService().myTickets(UUID)`, `AppContext.reviewService()`, `ReviewRepository.findByBookingId(UUID)` (via a `hasReview` check through the service or repository), `TicketFilingController.configure()`, `ReviewDialogController.configure()`, `TicketOpenedEvent`
- Produces: Trip cards with conditional "File Dispute" and "Leave Review" buttons

Note: We need a way to check if a review exists. We'll add `ReviewService.hasReview(UUID bookingId)` or check through `ReviewRepository`. Simpler: add `Optional<Review> findReview(UUID bookingId)` to `ReviewService`. But to keep it minimal, we can check via the existing `ReviewRepository.findByBookingId()`. Since `AppContext` doesn't expose the repository directly, we'll add a thin method to `ReviewService`:

Add to `ReviewService` interface: `boolean hasReview(UUID bookingId);`
Implement in `ReviewServiceImpl`: `return !reviews.findByBookingId(bookingId).isEmpty();`

- [ ] **Step 1: Add `hasReview` to `ReviewService` and implement it**

Add to `src/main/java/com/snoozeshare/service/ReviewService.java`:

```java
boolean hasReview(UUID bookingId);
```

Add to `src/main/java/com/snoozeshare/service/impl/ReviewServiceImpl.java`:

```java
@Override
public boolean hasReview(UUID bookingId) {
    return !reviews.findByBookingId(bookingId).isEmpty();
}
```

- [ ] **Step 2: Update `TripDashboardController` to add dispute and review buttons**

In `src/main/java/com/snoozeshare/ui/guest/trips/TripDashboardController.java`:

Add imports:
```java
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.infra.events.events.TicketOpenedEvent;
import com.snoozeshare.ui.guest.tickets.TicketFilingController;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
```

Add a field for caching filed tickets per load:
```java
private Set<UUID> bookedTicketIds = Set.of();
```

In `loadTrips()`, after fetching bookings but before rendering cards, cache which bookings already have tickets:

```java
var guestTickets = context.ticketService().myTickets(
        context.session().currentUser().orElseThrow().userId());
bookedTicketIds = guestTickets.stream()
        .map(Ticket::bookingId)
        .collect(java.util.stream.Collectors.toSet());
```

In `buildTripCard()`, after the cancel button block, add:

```java
if (canFileDispute(booking)) {
    Button disputeButton = new Button("File Dispute");
    disputeButton.getStyleClass().add("outline-button");
    disputeButton.setOnAction(event -> showDisputeModal(booking));
    card.getChildren().add(disputeButton);
}

if (canReview(booking)) {
    Button reviewButton = new Button("Leave Review");
    reviewButton.getStyleClass().add("outline-button");
    reviewButton.setOnAction(event -> showReviewModal(booking));
    card.getChildren().add(reviewButton);
}
```

Add helper methods:

```java
private boolean canFileDispute(Booking booking) {
    if (bookedTicketIds.contains(booking.bookingId())) {
        return false;
    }
    LocalDate today = LocalDate.now();
    boolean stayEnded = !booking.endDate().isAfter(today);
    boolean inWindow = !today.isAfter(booking.endDate().plusDays(7));
    boolean statusOk = (booking.status() == BookingStatus.CONFIRMED && stayEnded)
            || booking.status() == BookingStatus.COMPLETED;
    return statusOk && inWindow;
}

private boolean canReview(Booking booking) {
    return booking.status() == BookingStatus.COMPLETED
            && !context.reviewService().hasReview(booking.bookingId());
}

private void showDisputeModal(Booking booking) {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/snoozeshare/ui/guest/tickets/ticket-filing.fxml"));
        javafx.scene.Node dialogView = loader.load();
        TicketFilingController controller = loader.getController();

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");
        overlay.getChildren().add(dialogView);
        StackPane.setAlignment(dialogView, Pos.CENTER);

        StackPane root = findOrCreateStackRoot();
        root.getChildren().add(overlay);

        controller.configure(context, booking.bookingId(), () -> {
            root.getChildren().remove(overlay);
            loadTrips(currentTab);
        });
    } catch (IOException exception) {
        throw new IllegalStateException("Unable to load ticket filing dialog", exception);
    }
}

private void showReviewModal(Booking booking) {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/snoozeshare/ui/guest/trips/review-dialog.fxml"));
        javafx.scene.Node dialogView = loader.load();
        ReviewDialogController controller = loader.getController();

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");
        overlay.getChildren().add(dialogView);
        StackPane.setAlignment(dialogView, Pos.CENTER);

        StackPane root = findOrCreateStackRoot();
        root.getChildren().add(overlay);

        controller.configure(context, booking.bookingId(), () -> {
            root.getChildren().remove(overlay);
            loadTrips(currentTab);
        });
    } catch (IOException exception) {
        throw new IllegalStateException("Unable to load review dialog", exception);
    }
}

private StackPane findOrCreateStackRoot() {
    javafx.scene.Parent parent = tripsContainer.getParent();
    while (parent != null && !(parent instanceof StackPane)) {
        parent = parent.getParent();
    }
    if (parent instanceof StackPane stack) {
        return stack;
    }
    // Wrap in a StackPane if needed for modal overlay
    StackPane wrapper = new StackPane();
    javafx.scene.Node currentScene = tripsContainer.getScene().getRoot();
    if (currentScene instanceof StackPane s) {
        return s;
    }
    return wrapper;
}
```

Also subscribe to `TicketOpenedEvent` in `subscribeToEvents()`:

```java
subscriptions.add(context.eventBus().subscribe(TicketOpenedEvent.class,
        event -> Platform.runLater(() -> loadTrips(currentTab))));
```

- [ ] **Step 3: Update `trip-dashboard.fxml` to wrap content in StackPane for modals**

Replace the root element in `src/main/resources/com/snoozeshare/ui/guest/trips/trip-dashboard.fxml` — wrap the existing `VBox` in a `StackPane`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>

<StackPane fx:id="tripRoot" xmlns:fx="http://javafx.com/fxml"
           fx:controller="com.snoozeshare.ui.guest.trips.TripDashboardController">
    <VBox spacing="16">
        <padding><Insets top="24" right="24" bottom="24" left="24"/></padding>

        <Label text="My Trips" styleClass="page-title"/>

        <HBox fx:id="tabBar" spacing="16" styleClass="trip-tab-bar">
            <Label text="Pending" styleClass="nav-item,trip-tab,trip-tab-active"
                   onMouseClicked="#showPending"/>
            <Label text="Upcoming" styleClass="nav-item,trip-tab"
                   onMouseClicked="#showUpcoming"/>
            <Label text="Active" styleClass="nav-item,trip-tab"
                   onMouseClicked="#showActive"/>
            <Label text="Completed" styleClass="nav-item,trip-tab"
                   onMouseClicked="#showCompleted"/>
            <Label text="Cancelled" styleClass="nav-item,trip-tab"
                   onMouseClicked="#showCancelled"/>
        </HBox>

        <ScrollPane fitToWidth="true" VBox.vgrow="ALWAYS">
            <VBox fx:id="tripsContainer" spacing="12">
                <padding><Insets top="8" right="8" bottom="8" left="8"/></padding>
            </VBox>
        </ScrollPane>

        <Label fx:id="emptyLabel" text="No trips to show." styleClass="small" visible="false"
               managed="false"/>
    </VBox>
</StackPane>
```

Add the `tripRoot` field to `TripDashboardController`:
```java
@FXML private StackPane tripRoot;
```

And simplify `findOrCreateStackRoot()`:
```java
private StackPane findOrCreateStackRoot() {
    return tripRoot;
}
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run full test suite**

Run: `./gradlew test`

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/snoozeshare/service/ReviewService.java \
       src/main/java/com/snoozeshare/service/impl/ReviewServiceImpl.java \
       src/main/java/com/snoozeshare/ui/guest/trips/TripDashboardController.java \
       src/main/resources/com/snoozeshare/ui/guest/trips/trip-dashboard.fxml
git commit -m "Add File Dispute and Leave Review buttons to Trip Hub

Shows dispute button on eligible bookings (stay ended, within 7-day
window, no existing ticket). Shows review button on COMPLETED bookings
without an existing review. Both open modal overlays."
```

---

### Task 8: Update `PROJECT_STATE.md` and Done Ledger

**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`

**Interfaces:**
- Consumes: none (documentation only)
- Produces: updated project state reflecting W4 completion

- [ ] **Step 1: Update W4 row in `PROJECT_STATE.md`**

Change the W4 workstream row status from `Spec'd` to `Building` (or `Done` if all tasks pass), link the plan, and update progress:

```
| W4 | F3 — Guest Feedback, Disputes & Reviews | Done | [guest-feedback design](docs/superpowers/specs/2026-09-26-w4-guest-feedback-disputes-reviews-design.md) | [guest-feedback plan](docs/superpowers/plans/2026-09-26-w4-guest-feedback-disputes-reviews.md) | All 7 tasks complete: fileTicket, ReviewService, AppContext wiring, filing modal, review modal, Support tab, Trip Hub buttons | Awaiting confirmation |
```

- [ ] **Step 2: Add done ledger entries**

Prepend to `docs/project-state/done-ledger.md`:

```markdown
### 2026-09-26

- **W4 Task 1:** Implemented `fileTicket()` in `TicketServiceImpl` with 8 validation rules, `TicketOpenedEvent` publishing, audit logging; added `findByRaisedByUserId` to `TicketRepository`
- **W4 Task 2:** Created `ReviewServiceImpl` and `JdbcReviewRepository` with validation (ownership, COMPLETED status, rating range, duplicate check) and audit
- **W4 Task 3:** Wired `ReviewService` into `AppContext`
- **W4 Task 4:** Created ticket filing modal UI (category, title, description, remedy, supporting text)
- **W4 Task 5:** Created review modal UI (1-5 star rating, optional comment)
- **W4 Task 6:** Built Support tab with ticket history list and detail view; added `myTickets()` to `TicketService`
- **W4 Task 7:** Integrated "File Dispute" and "Leave Review" buttons into Trip Hub cards with eligibility checks
```

- [ ] **Step 3: Commit**

```bash
git add PROJECT_STATE.md docs/project-state/done-ledger.md
git commit -m "Update PROJECT_STATE.md and done ledger for W4 completion"
```
