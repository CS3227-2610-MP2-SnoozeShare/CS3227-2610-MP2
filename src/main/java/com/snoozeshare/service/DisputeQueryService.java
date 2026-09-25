package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.TicketStatus;

/**
 * Read models for the agent dispute screens (controllers may not touch repositories).
 */
public interface DisputeQueryService {
    List<DisputeSummary> queue(TicketStatus status, AssigneeFilter assignee, UUID agentId);

    DisputeDetail detail(UUID ticketId);
}
