package com.snoozeshare.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.repository.AvailabilityBlockRepository;
import com.snoozeshare.repository.BookingRepository;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.service.AvailabilityService;

public final class AvailabilityServiceImpl implements AvailabilityService {

    private final PropertyRepository properties;
    private final AvailabilityBlockRepository blocks;
    private final BookingRepository bookings;

    public AvailabilityServiceImpl(PropertyRepository properties,
                                   AvailabilityBlockRepository blocks,
                                   BookingRepository bookings) {
        this.properties = properties;
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
                                              UUID hostId, String reason) {
        if (propertyId == null || start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("Property and a valid date range are required");
        }
        LocalDate exclusiveEnd = end.plusDays(1);
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
        if (!property.hostId().equals(hostId)) {
            throw new IllegalStateException("Host does not own this property");
        }
        if (!bookings.findOverlapping(propertyId, start, exclusiveEnd).isEmpty()
                || !blocks.findOverlapping(propertyId, start, exclusiveEnd).isEmpty()) {
            throw new IllegalStateException("Date range is not available");
        }
        String normalizedReason = reason == null ? null : reason.trim();
        if (normalizedReason != null && normalizedReason.isEmpty()) {
            normalizedReason = null;
        }
        return blocks.save(new AvailabilityBlock(UUID.randomUUID(), propertyId, start, exclusiveEnd,
                "HOST_BLOCK", null, normalizedReason));
    }

    @Override
    public void removeHostBlock(UUID blockId, UUID hostId) {
        AvailabilityBlock block = blocks.findById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Availability block does not exist"));
        if (!"HOST_BLOCK".equals(block.source())) {
            throw new IllegalStateException("Booking blocks cannot be removed");
        }
        Property property = properties.findById(block.propertyId())
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
        if (!property.hostId().equals(hostId)) {
            throw new IllegalStateException("Host does not own this property");
        }
        blocks.deleteById(blockId);
    }

    @Override
    public List<AvailabilityBlock> blocksFor(UUID propertyId) {
        return blocks.findByPropertyId(propertyId);
    }
}
