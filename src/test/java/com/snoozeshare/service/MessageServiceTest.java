package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.infra.events.events.MessagePostedEvent;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcMessageRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.service.impl.MessageServiceImpl;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

/**
 * Runs against a copy of the committed mock DB. Ticket 2 (open, Aria/Priya) has one seeded GUEST message;
 * ticket 3 (in review, Sophia/Diego, agent Ben) has two GUEST and two HOST messages; ticket 1 is resolved.
 */
class MessageServiceTest {

    private final InProcessEventBus bus = new InProcessEventBus();

    private MessageServiceImpl service(MockDbFixture db) {
        var c = db.connection();
        return new MessageServiceImpl(new JdbcMessageRepository(c), new JdbcTicketRepository(c),
                new JdbcBookingRepository(c), new JdbcPropertyRepository(c), new JdbcUserRepository(c), bus,
                SettlementFixtures.CLOCK);
    }

    @Test
    void agentReadsBothThreadsInOrder(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);

            List<Message> guest = service.thread(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.AGENT_BEN, Role.AGENT);
            List<Message> host = service.thread(MockIds.TICKET_3, ThreadChannel.HOST, MockIds.AGENT_BEN, Role.AGENT);

            assertEquals(2, guest.size());
            assertEquals(Role.GUEST, guest.get(0).authorRole());
            assertEquals(Role.AGENT, guest.get(1).authorRole());
            assertEquals(2, host.size());
            assertEquals(Role.HOST, host.get(1).authorRole());
        }
    }

    @Test
    void partiesReadOnlyTheirOwnThread(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);

            assertEquals(2, service.thread(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.GUEST_SOPHIA,
                    Role.GUEST).size());
            assertEquals(2, service.thread(MockIds.TICKET_3, ThreadChannel.HOST, MockIds.HOST_DIEGO, Role.HOST).size());
            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(MockIds.TICKET_3, ThreadChannel.HOST, MockIds.GUEST_SOPHIA, Role.GUEST));
            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.HOST_DIEGO, Role.HOST));
        }
    }

    @Test
    void strangersAndMismatchedRolesAreRejected(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);

            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.GUEST_ARIA, Role.GUEST));
            assertThrows(IllegalArgumentException.class, () ->
                    service.post(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.HOST_PRIYA, Role.HOST, "hi"));
            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.GUEST_SOPHIA, Role.AGENT));
            assertThrows(IllegalArgumentException.class, () ->
                    service.thread(UUID.randomUUID(), ThreadChannel.GUEST, MockIds.AGENT_BEN, Role.AGENT));
        }
    }

    @Test
    void postStoresTrimmedBodyAppendsInOrderAndPublishesOnce(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);
            List<MessagePostedEvent> events = new ArrayList<>();
            bus.subscribe(MessagePostedEvent.class, events::add);

            Message posted = service.post(MockIds.TICKET_2, ThreadChannel.GUEST, MockIds.GUEST_ARIA, Role.GUEST,
                    "  Here is a photo  ");
            service.post(MockIds.TICKET_2, ThreadChannel.GUEST, MockIds.AGENT_AMY, Role.AGENT, "Thanks");

            assertEquals("Here is a photo", posted.body());
            assertEquals(SettlementFixtures.CLOCK.instant(), posted.sentAt());
            List<Message> thread = service.thread(MockIds.TICKET_2, ThreadChannel.GUEST, MockIds.AGENT_AMY, Role.AGENT);
            assertEquals(3, thread.size());
            assertEquals("Here is a photo", thread.get(1).body());
            assertEquals("Thanks", thread.get(2).body());
            assertEquals(2, events.size());
            assertEquals(posted, events.get(0).message());
        }
    }

    @Test
    void rejectedPostsStoreNothingAndPublishNothing(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);
            List<MessagePostedEvent> events = new ArrayList<>();
            bus.subscribe(MessagePostedEvent.class, events::add);

            assertThrows(IllegalArgumentException.class, () ->
                    service.post(MockIds.TICKET_2, ThreadChannel.GUEST, MockIds.GUEST_ARIA, Role.GUEST, "  "));
            assertThrows(IllegalArgumentException.class, () ->
                    service.post(MockIds.TICKET_2, ThreadChannel.HOST, MockIds.GUEST_ARIA, Role.GUEST, "wrong thread"));

            assertTrue(events.isEmpty());
            assertEquals(1, service.thread(MockIds.TICKET_2, ThreadChannel.GUEST, MockIds.AGENT_AMY,
                    Role.AGENT).size());
        }
    }

    @Test
    void resolvedTicketThreadsAreReadOnly(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);

            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                    service.post(MockIds.TICKET_1, ThreadChannel.GUEST, MockIds.AGENT_AMY, Role.AGENT, "Any update?"));

            assertEquals("Ticket is resolved", failure.getMessage());
            assertEquals(3, service.thread(MockIds.TICKET_1, ThreadChannel.GUEST, MockIds.AGENT_AMY,
                    Role.AGENT).size(), "the history stays readable");
        }
    }

    @Test
    void unreadCountsIgnoreOwnMessagesAndClearOnMarkRead(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);

            assertEquals(1, service.unreadCount(MockIds.GUEST_SOPHIA, Role.GUEST), "the agent reply");
            assertEquals(1, service.unreadCount(MockIds.HOST_DIEGO, Role.HOST), "the agent request");

            service.markRead(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.GUEST_SOPHIA, Role.GUEST);
            assertEquals(0, service.unreadCount(MockIds.GUEST_SOPHIA, Role.GUEST));
            assertEquals(1, service.unreadCount(MockIds.HOST_DIEGO, Role.HOST), "read state is per user");

            service.post(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.AGENT_BEN, Role.AGENT, "Any news?");
            service.post(MockIds.TICKET_3, ThreadChannel.GUEST, MockIds.GUEST_SOPHIA, Role.GUEST, "Not yet");
            assertEquals(1, service.unreadCount(MockIds.GUEST_SOPHIA, Role.GUEST), "own message does not count");
        }
    }

    @Test
    void markReadOnAnEmptyThreadDoesNothing(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);

            service.markRead(MockIds.TICKET_2, ThreadChannel.HOST, MockIds.HOST_PRIYA, Role.HOST);

            assertEquals(0, service.unreadCount(MockIds.HOST_PRIYA, Role.HOST));
            assertThrows(IllegalArgumentException.class, () ->
                    service.markRead(MockIds.TICKET_2, ThreadChannel.HOST, MockIds.GUEST_ARIA, Role.GUEST));
        }
    }

    @Test
    void conversationsListEachPartyTicketWithLastMessageAndAgentName(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);

            List<ConversationSummary> sophia = service.conversationsFor(MockIds.GUEST_SOPHIA, Role.GUEST);
            assertEquals(1, sophia.size());
            ConversationSummary row = sophia.get(0);
            assertEquals(MockIds.TICKET_3, row.ticketId());
            assertEquals(ThreadChannel.GUEST, row.channel());
            assertEquals("#0003", row.ticketLabel());
            assertEquals("Modern Studio Near Metro", row.listingTitle());
            assertEquals("Ben Alvarez", row.counterpartName());
            assertEquals(Role.AGENT, row.lastMessage().authorRole());
            assertEquals(1, row.unreadCount());
            assertTrue(row.open());

            ConversationSummary priya = service.conversationsFor(MockIds.HOST_PRIYA, Role.HOST).get(0);
            assertEquals(MockIds.TICKET_2, priya.ticketId());
            assertEquals("Support team", priya.counterpartName(), "unassigned ticket");
            assertEquals(Role.HOST, priya.lastMessage().authorRole(), "the host has replied on the open ticket");
        }
    }

    @Test
    void conversationsAreRejectedForAgentsAndClosedOnResolvedTickets(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MessageService service = service(db);

            assertThrows(IllegalArgumentException.class, () -> service.conversationsFor(MockIds.AGENT_AMY, Role.AGENT));
            UUID ticket1Guest = UUID.fromString(db.scalarString(
                    "SELECT b.guestId FROM tickets t JOIN bookings b ON b.bookingId = t.bookingId WHERE t.ticketId = ?",
                    MockIds.TICKET_1.toString()));
            ConversationSummary resolved = service.conversationsFor(ticket1Guest, Role.GUEST).stream()
                    .filter(c -> c.ticketId().equals(MockIds.TICKET_1)).findFirst().orElseThrow();
            assertFalse(resolved.open());
        }
    }
}
