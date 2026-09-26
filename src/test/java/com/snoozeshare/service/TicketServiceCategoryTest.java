package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.service.impl.TicketServiceImpl;
import com.snoozeshare.testsupport.Fakes;

class TicketServiceCategoryTest {

    private static final com.snoozeshare.infra.events.EventBus NOOP_BUS =
            new com.snoozeshare.infra.events.EventBus() {
                @Override
                public <T extends com.snoozeshare.infra.events.DomainEvent>
                        com.snoozeshare.infra.events.Subscription subscribe(Class<T> type,
                        java.util.function.Consumer<T> subscriber) {
                    return () -> {};
                }
                @Override
                public void publish(com.snoozeshare.infra.events.DomainEvent event) {}
            };
    private final UUID agent = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();
    private final Fakes.RecordingAudit audit = new Fakes.RecordingAudit();
    private final Fakes.InMemoryTickets tickets = new Fakes.InMemoryTickets();
    private final TicketService service = new TicketServiceImpl(tickets,
            new Fakes.InMemoryCategories(), null,
            new Fakes.StubUsers()
                    .with(new User(agent, Role.AGENT, "Amy", "amy@x.test", AccountStatus.ACTIVE, null,
                            Instant.parse("2026-01-01T00:00:00Z")))
                    .with(new User(guest, Role.GUEST, "Gus", "gus@x.test", AccountStatus.ACTIVE, null,
                            Instant.parse("2026-01-01T00:00:00Z"))),
            new Fakes.RecordingSettlement(), audit,
            Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC), NOOP_BUS);

    @Test
    void createTrimsTheLabelAndMakesTheCategoryActive() {
        TicketCategory created = service.createCategory("  Noise  ", agent);

        assertEquals("Noise", created.label());
        assertTrue(created.active());
        assertEquals(List.of("Noise"), service.listCategories().stream().map(TicketCategory::label).toList());
        assertEquals(List.of("TICKET_CATEGORY_CREATED"), audit.actions());
    }

    @Test
    void duplicateOrBlankLabelsAreRejectedCaseInsensitively() {
        service.createCategory("Noise", agent);

        assertThrows(IllegalArgumentException.class, () ->
                service.createCategory("noise", agent));
        assertThrows(IllegalArgumentException.class, () ->
                service.createCategory("   ", agent));
    }

    @Test
    void renameAllowsTheSameLabelButNotAnotherCategorysLabel() {
        TicketCategory noise = service.createCategory("Noise", agent);
        service.createCategory("Smell", agent);

        assertEquals("NOISE", service.renameCategory(noise.categoryId(), "NOISE", agent).label());
        assertThrows(IllegalArgumentException.class, () ->
                service.renameCategory(noise.categoryId(), "smell", agent));
        assertThrows(IllegalArgumentException.class, () ->
                service.renameCategory(UUID.randomUUID(), "Whatever", agent));
    }

    @Test
    void deactivatedCategoriesLeaveTheGuestListButStayInTheAdminList() {
        TicketCategory noise = service.createCategory("Noise", agent);

        service.setCategoryActive(noise.categoryId(), false, agent);

        assertTrue(service.listCategories().isEmpty());
        assertEquals(1, service.listAllCategories().size());
        assertFalse(service.listAllCategories().get(0).active());

        service.setCategoryActive(noise.categoryId(), true, agent);
        assertEquals(1, service.listCategories().size());
    }

    @Test
    void deleteRemovesAnUnusedCategoryAndAuditsIt() {
        TicketCategory noise = service.createCategory("Noise", agent);

        service.deleteCategory(noise.categoryId(), agent);

        assertTrue(service.listAllCategories().isEmpty());
        assertEquals(List.of("TICKET_CATEGORY_CREATED", "CATEGORY_DELETED"), audit.actions());
    }

    @Test
    void deleteIsRefusedWhenAnyTicketUsesTheCategory() {
        TicketCategory noise = service.createCategory("Noise", agent);
        tickets.save(new Ticket(UUID.randomUUID(), UUID.randomUUID(), guest, Role.GUEST, "noise", "t", "d",
                RemedyType.OTHER, null, TicketStatus.RESOLVED_REJECTED, null, null, null,
                Instant.parse("2026-09-01T00:00:00Z"), null));

        IllegalStateException refused = assertThrows(IllegalStateException.class, () ->
                service.deleteCategory(noise.categoryId(), agent));

        assertEquals("Category is in use by tickets; deactivate it instead", refused.getMessage());
        assertEquals(1, service.listAllCategories().size());
    }

    @Test
    void deleteNeedsAnExistingCategoryAndAnAgent() {
        TicketCategory noise = service.createCategory("Noise", agent);

        assertThrows(IllegalArgumentException.class, () ->
                service.deleteCategory(UUID.randomUUID(), agent));
        assertThrows(IllegalStateException.class, () ->
                service.deleteCategory(noise.categoryId(), guest));
        assertEquals(1, service.listAllCategories().size());
    }

    @Test
    void onlyAgentsMayAdministerCategories() {
        assertThrows(IllegalStateException.class, () ->
                service.createCategory("Noise", guest));
        assertThrows(IllegalStateException.class, () ->
                service.createCategory("Noise", UUID.randomUUID()));
    }
}
