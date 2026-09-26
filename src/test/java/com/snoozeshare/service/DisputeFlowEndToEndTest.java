package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.ThreadChannel;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.events.TicketResolvedEvent;
import com.snoozeshare.service.requests.ResolutionRequest;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class DisputeFlowEndToEndTest {

    @Test
    void anAgentWorksAnOpenDisputeThroughTheWholeAppContext(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            User amy = context.userService().authenticate("amy.tanaka@snoozeshare.test");
            context.session().loginAs(amy);
            List<TicketResolvedEvent> resolved = new ArrayList<>();
            context.eventBus().subscribe(TicketResolvedEvent.class, resolved::add);

            var unassigned = context.disputeQueryService().queue(
                    null, AssigneeFilter.UNASSIGNED, amy.userId());
            assertEquals(1, unassigned.size());
            assertEquals(MockIds.TICKET_2, unassigned.get(0).ticketId());

            context.messageService().post(MockIds.TICKET_2, ThreadChannel.GUEST, amy.userId(),
                    Role.AGENT, "Could you send a photo of the gate?");
            assertEquals(1, context.messageService().thread(MockIds.TICKET_2, ThreadChannel.GUEST).size());

            context.ticketService().assignToMe(MockIds.TICKET_2, amy.userId());
            context.ticketService().resolve(MockIds.TICKET_2,
                    new ResolutionRequest(ResolutionMode.ACCEPT, new BigDecimal("175.00"),
                            "Gate was locked"), amy.userId());

            var detail = context.disputeQueryService().detail(MockIds.TICKET_2);
            assertEquals(TicketStatus.RESOLVED_APPROVED, detail.ticket().status());
            assertFalse(detail.escrowHeld());
            assertEquals(1, resolved.size());
            assertEquals(0, new BigDecimal("700").compareTo(
                    context.walletService().balanceOf(MockIds.GUEST_ARIA)));
            assertTrue(context.walletService().statementFor(MockIds.GUEST_ARIA).stream()
                    .anyMatch(t -> t.relatedTicketId() != null
                            && t.relatedTicketId().equals(MockIds.TICKET_2)));
            db.assertLedgerInvariant();
        }
    }
}
