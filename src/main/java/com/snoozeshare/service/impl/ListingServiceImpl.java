package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.service.AvailabilityService;
import com.snoozeshare.service.ListingService;
import com.snoozeshare.service.PriceBreakdown;
import com.snoozeshare.service.SearchCriteria;
import com.snoozeshare.service.SearchResult;

public final class ListingServiceImpl implements ListingService {

    private final PropertyRepository properties;
    private final AvailabilityService availability;

    public ListingServiceImpl(PropertyRepository properties,
                               AvailabilityService availability) {
        this.properties = properties;
        this.availability = availability;
    }

    @Override
    public List<SearchResult> search(SearchCriteria criteria) {
        List<Property> candidates = properties.findBySearchCriteria(criteria);
        boolean hasDateRange = criteria.startDate() != null && criteria.endDate() != null;
        if (!hasDateRange) {
            return candidates.stream()
                    .map(p -> new SearchResult(p, true))
                    .toList();
        }
        List<SearchResult> available = new ArrayList<>();
        List<SearchResult> unavailable = new ArrayList<>();
        for (Property property : candidates) {
            if (availability.isRangeAvailable(property.propertyId(),
                    criteria.startDate(), criteria.endDate())) {
                available.add(new SearchResult(property, true));
            } else {
                unavailable.add(new SearchResult(property, false));
            }
        }
        List<SearchResult> results = new ArrayList<>(available);
        results.addAll(unavailable);
        return results;
    }

    @Override
    public Property getDetail(UUID propertyId) {
        return properties.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
    }

    @Override
    public PriceBreakdown estimateCost(UUID propertyId, LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates must not be null");
        }
        long nights = ChronoUnit.DAYS.between(start, end);
        if (nights <= 0) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        Property property = getDetail(propertyId);
        BigDecimal totalAmount = property.baseNightlyRate()
                .multiply(BigDecimal.valueOf(nights));
        return new PriceBreakdown(property.baseNightlyRate(), (int) nights, totalAmount);
    }

    @Override
    public Property create(Property draft, UUID hostId) {
        throw new UnsupportedOperationException("Owned by W6 (F5)");
    }

    @Override
    public Property updateStatus(UUID propertyId, ListingStatus status, UUID hostId) {
        throw new UnsupportedOperationException("Owned by W6 (F5)");
    }
}
