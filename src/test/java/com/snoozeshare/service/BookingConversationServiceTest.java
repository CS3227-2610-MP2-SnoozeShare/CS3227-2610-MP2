package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.BookingMessage;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.infra.events.events.BookingMessagePostedEvent;
import com.snoozeshare.repository.jdbc.JdbcBookingMessageRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.impl.BookingConversationServiceImpl;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

/**
 * Runs against a copy of the committed mock DB with the clock at 2026-09-25. Booking 3 (Aria / Marcus Lee) and
 * booking 14 (Maya Singh / Diego) are inside their chat window; booking 9 (Aria / Priya) ended 6 August, so
 * its chat is read-only; booking 1 (Wei / Olivia) is still pending.
 */
class BookingConversationServiceTest {

    private static final UUID BOOKING_3 = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID BOOKING_1 = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID BOOKING_14 = UUID.fromString("20000000-0000-0000-0000-000000000014");
    private static final UUID MARCUS = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID WEI = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID MAYA = UUID.fromString("c0000000-0000-0000-0000-000000000007");

    private final InProcessEventBus bus = new InProcessEventBus();

    private BookingConversationServiceImpl service(MockDbFixture db, Clock clock) {
        var c = db.connection();
        return new BookingConversationServiceImpl(new JdbcBookingMessageRepository(c), new JdbcBookingRepository(c),
                new JdbcPropertyRepository(c), new JdbcUserRepository(c), bus, clock);
    }

    private BookingConversationServiceImpl service(MockDbFixture db) {
        return service(db, SettlementFixtures.CLOCK);
    }

    @Test
    void partiesReadTheSeededThreadInOrder(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            BookingConversationService service = service(db);

            List<BookingMessage> guest = service.thread(BOOKING_3, MockIds.GUEST_ARIA, Role.GUEST);
            List<BookingMessage> host = service.thread(BOOKING_3, MARCUS, Role.HOST);

            assertEquals(4, guest.size());
            assertEquals(guest, host);
            assertEquals(Role.GUEST, guest.get(0).authorRole());
            assertEquals(Role.HOST, guest.get(3).authorRole());
        }
    }

    @Test
    void agentsStrangersAndMismatchedRolesAreRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            BookingConversationService service = service(db);

            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(BOOKING_3, MockIds.AGENT_AMY, Role.AGENT));
            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(BOOKING_3, MockIds.GUEST_SOPHIA, Role.GUEST));
            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(BOOKING_3, MockIds.HOST_DIEGO, Role.HOST));
            assertThrows(IllegalArgumentException.class, () ->
                    service.post(BOOKING_3, MARCUS, Role.GUEST, "wrong role"));
            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(UUID.randomUUID(), MockIds.GUEST_ARIA, Role.GUEST));
        }
    }

    @Test
    void postStoresTrimmedBodyInOrderAndPublishesOnce(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            BookingConversationService service = service(db);
            List<BookingMessagePostedEvent> events = new ArrayList<>();
            bus.subscribe(BookingMessagePostedEvent.class, events::add);

            BookingMessage posted = service.post(BOOKING_3, MockIds.GUEST_ARIA, Role.GUEST, "  See you on the 10th  ");

            assertEquals("See you on the 10th", posted.body());
            assertEquals(SettlementFixtures.CLOCK.instant(), posted.sentAt());
            List<BookingMessage> thread = service.thread(BOOKING_3, MARCUS, Role.HOST);
            assertEquals(5, thread.size());
            assertEquals(posted, thread.get(4));
            assertEquals(1, events.size());
            assertEquals(posted, events.get(0).message());
        }
    }

    @Test
    void rejectedPostsStoreNothingAndPublishNothing(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            BookingConversationService service = service(db);
            List<BookingMessagePostedEvent> events = new ArrayList<>();
            bus.subscribe(BookingMessagePostedEvent.class, events::add);

            assertThrows(IllegalArgumentException.class, () ->
                    service.post(BOOKING_3, MockIds.GUEST_ARIA, Role.GUEST, "   "));
            assertThrows(IllegalStateException.class, () ->
                    service.post(BOOKING_1, WEI, Role.GUEST, "hi"));

            assertTrue(events.isEmpty());
            assertEquals(4, service.thread(BOOKING_3, MockIds.GUEST_ARIA, Role.GUEST).size());
        }
    }

    @Test
    void chatIsWritableFromConfirmationUntilSevenDaysAfterCheckout(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            // Booking 14 ends 25 September, so the last writable day is 2 October.
            Clock lastDay = Clock.fixed(Instant.parse("2026-10-02T23:00:00Z"), ZoneOffset.UTC);
            Clock nextDay = Clock.fixed(Instant.parse("2026-10-03T00:30:00Z"), ZoneOffset.UTC);

            assertEquals("Last day", service(db, lastDay).post(BOOKING_14, MAYA, Role.GUEST, "Last day").body());
            IllegalStateException closed = assertThrows(IllegalStateException.class, () ->
                    service(db, nextDay).post(BOOKING_14, MAYA, Role.GUEST, "Too late"));
            assertEquals("Chat is closed for this booking", closed.getMessage());
            assertEquals(7, service(db, nextDay).thread(BOOKING_14, MAYA, Role.GUEST).size(), "still readable");
        }
    }

    @Test
    void closedHistoryStaysReadableAfterTheWindow(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            BookingConversationService service = service(db);
            UUID booking9 = UUID.fromString("20000000-0000-0000-0000-000000000009");

            assertEquals(5, service.thread(booking9, MockIds.GUEST_ARIA, Role.GUEST).size());
            assertThrows(IllegalStateException.class, () ->
                    service.post(booking9, MockIds.HOST_PRIYA, Role.HOST, "Any update?"));
        }
    }

    @Test
    void unreadCountsIgnoreOwnMessagesAndClearOnMarkRead(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            BookingConversationService service = service(db);

            assertEquals(1, service.unreadCount(MockIds.GUEST_ARIA, Role.GUEST), "Marcus's last reply on booking 3");
            assertEquals(1, service.unreadCount(MARCUS, Role.HOST), "Aria's follow-up on booking 3");

            service.markRead(BOOKING_3, MockIds.GUEST_ARIA, Role.GUEST);

            assertEquals(0, service.unreadCount(MockIds.GUEST_ARIA, Role.GUEST));
            assertEquals(1, service.unreadCount(MARCUS, Role.HOST), "read state is per user");
            assertThrows(IllegalArgumentException.class, () ->
                    service.markRead(BOOKING_3, MockIds.GUEST_SOPHIA, Role.GUEST));
        }
    }

    @Test
    void guestInboxListsOpenAndHistoricChatsNewestFirstAndSkipsPending(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            List<BookingConversationSummary> inbox = service(db).conversationsFor(MockIds.GUEST_ARIA, Role.GUEST);

            assertEquals(2, inbox.size(), "booking 3 (open) and booking 9 (closed history)");
            BookingConversationSummary open = inbox.get(0);
            assertEquals(BOOKING_3, open.bookingId());
            assertEquals("Marcus Lee", open.counterpartName());
            assertEquals("Downtown Skyline Condo", open.listingTitle());
            assertEquals(Role.HOST, open.lastMessage().authorRole());
            assertEquals(1, open.unreadCount());
            assertTrue(open.open());
            assertFalse(inbox.get(1).open());
            assertEquals("Priya Nair", inbox.get(1).counterpartName());
        }
    }

    @Test
    void hostInboxShowsGuestNamesAndAConfirmedBookingWithNoMessagesYet(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            List<BookingConversationSummary> inbox = service(db).conversationsFor(MockIds.HOST_DIEGO, Role.HOST);

            assertTrue(inbox.stream().anyMatch(c -> c.counterpartName().equals("Maya Singh") && c.open()));
            assertTrue(inbox.stream().anyMatch(c -> c.counterpartName().equals("Liam O'Sullivan")));
            assertTrue(inbox.stream().anyMatch(c -> c.counterpartName().equals("Sophia Rossi") && !c.open()));
            assertThrows(IllegalArgumentException.class, () ->
                    service(db).conversationsFor(MockIds.AGENT_AMY, Role.AGENT));
        }
    }

    @Test
    void aConfirmedBookingWithNoMessagesIsListedAsOpenWithNoLastMessage(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            db.execute("DELETE FROM booking_message_reads WHERE bookingId = ?", BOOKING_3.toString());
            db.execute("DELETE FROM booking_messages WHERE bookingId = ?", BOOKING_3.toString());

            BookingConversationSummary row = service(db).conversationsFor(MARCUS, Role.HOST).stream()
                    .filter(c -> c.bookingId().equals(BOOKING_3)).findFirst().orElseThrow();

            assertNull(row.lastMessage());
            assertTrue(row.open());
            assertEquals(0, row.unreadCount());
        }
    }
}
