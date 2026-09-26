package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.model.Message;
import com.snoozeshare.service.impl.InMemoryMessageService;

class InMemoryMessageServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC);
    private final MessageService service = new InMemoryMessageService(clock);
    private final UUID ticket = UUID.randomUUID();
    private final UUID agent = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();

    @Test
    void threadsAreSeparatePerChannelAndChronological() {
        service.post(ticket, ThreadChannel.GUEST, guest, Role.GUEST, "It was loud");
        service.post(ticket, ThreadChannel.GUEST, agent, Role.AGENT, "Looking into it");
        service.post(ticket, ThreadChannel.HOST, agent, Role.AGENT, "Please respond");

        assertEquals(2, service.thread(ticket, ThreadChannel.GUEST).size());
        assertEquals("It was loud", service.thread(ticket, ThreadChannel.GUEST).get(0).body());
        assertEquals(1, service.thread(ticket, ThreadChannel.HOST).size());
        assertTrue(service.thread(UUID.randomUUID(), ThreadChannel.GUEST).isEmpty());
    }

    @Test
    void postedMessageCarriesAuthorAndTimestamp() {
        Message message = service.post(ticket, ThreadChannel.GUEST, agent, Role.AGENT, "Hello");

        assertEquals(Role.AGENT, message.authorRole());
        assertEquals(agent, message.authorId());
        assertEquals(Instant.parse("2026-09-25T04:00:00Z"), message.sentAt());
    }

    @Test
    void rejectsBlankBodiesAndWrongChannelForTheRole() {
        assertThrows(IllegalArgumentException.class, () ->
                service.post(ticket, ThreadChannel.GUEST, agent, Role.AGENT, "  "));
        assertThrows(IllegalArgumentException.class, () ->
                service.post(ticket, ThreadChannel.HOST, guest, Role.GUEST, "wrong thread"));
    }

    @Test
    void returnedThreadCannotBeUsedToMutateTheStore() {
        service.post(ticket, ThreadChannel.GUEST, guest, Role.GUEST, "one");

        assertThrows(UnsupportedOperationException.class, () ->
                service.thread(ticket, ThreadChannel.GUEST).clear());
    }
}
