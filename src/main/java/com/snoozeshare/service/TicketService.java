package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.service.requests.NewTicketRequest;

public interface TicketService {
    Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole);

    List<TicketCategory> listCategories();

    List<Ticket> queueForAgent(TicketStatus statusFilter);

    Ticket addAgentNote(UUID ticketId, String note, UUID agentId);

    Ticket addHostResponse(UUID ticketId, String responseText, UUID hostId);

    Ticket resolve(UUID ticketId, boolean approve, RemedyType remedy, BigDecimal amount,
                   String reason, UUID agentId);
}
