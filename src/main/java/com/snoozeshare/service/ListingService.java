package com.snoozeshare.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.model.Property;

public interface ListingService {
    List<Property> search(SearchCriteria criteria);

    Property getDetail(UUID propertyId);

    PriceBreakdown estimateCost(UUID propertyId, LocalDate start, LocalDate end);

    Property create(Property draft, UUID hostId);

    Property updateStatus(UUID propertyId, ListingStatus status, UUID hostId);
}
