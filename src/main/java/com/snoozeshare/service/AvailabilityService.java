package com.snoozeshare.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AvailabilityBlock;

public interface AvailabilityService {
    boolean isRangeAvailable(UUID propertyId, LocalDate start, LocalDate end);

    AvailabilityBlock createHostBlock(UUID propertyId, LocalDate start, LocalDate end, UUID hostId);

    List<AvailabilityBlock> blocksFor(UUID propertyId);
}
