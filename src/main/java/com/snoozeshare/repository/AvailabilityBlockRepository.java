package com.snoozeshare.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.AvailabilityBlock;

public interface AvailabilityBlockRepository {
    Optional<AvailabilityBlock> findById(UUID blockId);

    List<AvailabilityBlock> findByPropertyId(UUID propertyId);

    List<AvailabilityBlock> findOverlapping(UUID propertyId, LocalDate start, LocalDate end);

    AvailabilityBlock save(AvailabilityBlock block);

    void deleteByBookingId(UUID bookingId);
}
