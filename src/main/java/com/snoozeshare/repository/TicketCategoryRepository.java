package com.snoozeshare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.TicketCategory;

public interface TicketCategoryRepository {
    List<TicketCategory> findActive();

    List<TicketCategory> findAll();

    Optional<TicketCategory> findById(UUID categoryId);

    /**
     * Case-insensitive label check; excludeCategoryId (nullable) is ignored so a rename to itself is allowed.
     */
    boolean labelInUse(String label, UUID excludeCategoryId);

    TicketCategory save(TicketCategory category);

    void deleteById(UUID categoryId);
}
