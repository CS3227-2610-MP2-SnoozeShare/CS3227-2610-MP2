package com.snoozeshare.repository;

import java.util.List;

import com.snoozeshare.domain.model.TicketCategory;

public interface TicketCategoryRepository {
    List<TicketCategory> findActive();

    TicketCategory save(TicketCategory category);
}
