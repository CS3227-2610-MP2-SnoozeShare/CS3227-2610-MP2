package com.snoozeshare.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.service.AvailabilityService;

public final class AvailabilityServiceImpl implements AvailabilityService {

    private final AvailabilityBlockRepository blocks;
    private final BookingRepository bookings;

    public AvailabilityServiceImpl(AvailabilityBlockRepository blocks,
                                    BookingRepository bookings) {
        this.blocks = blocks;
        this.bookings = bookings;
    }

    @Override
    public boolean isRangeAvailable(UUID propertyId, LocalDate start, LocalDate end) {
        return blocks.findOverlapping(propertyId, start, end).isEmpty()
                && bookings.findOverlapping(propertyId, start, end).isEmpty();
    }

    @Override
    public AvailabilityBlock createHostBlock(UUID propertyId, LocalDate start, LocalDate end,
                                              UUID hostId) {
        throw new UnsupportedOperationException("Owned by W7 (F6)");
    }

    @Override
    public List<AvailabilityBlock> blocksFor(UUID propertyId) {
        return blocks.findByPropertyId(propertyId);
    }
}
