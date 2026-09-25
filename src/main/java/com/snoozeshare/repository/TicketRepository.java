package com.snoozeshare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;

public interface TicketRepository {
    Optional<Ticket> findById(UUID ticketId);

    List<Ticket> findByStatus(TicketStatus status);

    /**
     * Tickets oldest first. A null status means every status; MINE requires a non-null agentId.
     */
    List<Ticket> findQueue(TicketStatus status, AssigneeFilter assignee, UUID agentId);

    Ticket save(Ticket ticket);
}
