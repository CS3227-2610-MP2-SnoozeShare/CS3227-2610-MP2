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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

        assertTrue(audit.actions().contains("TICKET_OPENED"));
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
