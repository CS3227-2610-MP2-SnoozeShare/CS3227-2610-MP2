package com.snoozeshare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;

public interface TicketRepository {
    Optional<Ticket> findById(UUID ticketId);

    List<Ticket> findByStatus(TicketStatus status);

    Ticket save(Ticket ticket);
}
