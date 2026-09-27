package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.service.requests.NewTicketRequest;
import com.snoozeshare.service.requests.ResolutionRequest;

public interface TicketService {
    /** Owned by W4 (guest filing). */
    Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole);

    /** Returns all tickets filed by the given user, newest first. */
    List<Ticket> myTickets(UUID userId);

    /** Active categories only (what guests see when filing). */
    List<TicketCategory> listCategories();

    List<TicketCategory> listAllCategories();

    TicketCategory createCategory(String label, UUID agentId);

    TicketCategory renameCategory(UUID categoryId, String label, UUID agentId);

    TicketCategory setCategoryActive(UUID categoryId, boolean active, UUID agentId);

    /** Permanently removes a category no ticket was filed under; a used category must be deactivated instead. */
    void deleteCategory(UUID categoryId, UUID agentId);

    /** Oldest first. */
    List<Ticket> queueForAgent(TicketStatus statusFilter, AssigneeFilter assignee, UUID agentId);

    Ticket assignToMe(UUID ticketId, UUID agentId);

    /** Returns an IN_REVIEW ticket to OPEN with no assignee; only the assigned agent may do this. */
    Ticket unassign(UUID ticketId, UUID agentId);

    /** Replaces the single internal-notes text (may be empty); only while IN_REVIEW and assigned to the agent. */
    Ticket saveNotes(UUID ticketId, String notes, UUID agentId);

    /** Superseded by MessageService (C21); not implemented. */
    @Deprecated
    Ticket addHostResponse(UUID ticketId, String responseText, UUID hostId);

    Ticket resolve(UUID ticketId, ResolutionRequest request, UUID agentId);
}
