package com.snoozeshare.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.validation.DomainValidation;
import com.snoozeshare.repository.PropertyRepository;
import com.snoozeshare.service.AvailabilityService;
import com.snoozeshare.service.ListingService;
import com.snoozeshare.service.PriceBreakdown;
import com.snoozeshare.service.SearchCriteria;
import com.snoozeshare.service.SearchResult;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.UserService;

public final class ListingServiceImpl implements ListingService {

    private final PropertyRepository properties;
    private final AvailabilityService availability;
    private final UserService users;
    private final AuditService audit;

    public ListingServiceImpl(PropertyRepository properties,
                               AvailabilityService availability,
                               UserService users,
                               AuditService audit) {
        this.properties = properties;
        this.availability = availability;
        this.users = users;
        this.audit = audit;
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
    public List<Property> findByHostId(UUID hostId) {
        return properties.findByHostId(hostId);
    }

    @Override
    public Property create(Property draft, UUID hostId) {
        User host = requireActiveHost(hostId);
        validateDraft(draft);
        if (draft.propertyId() != null && properties.findById(draft.propertyId()).isPresent()) {
            throw new IllegalArgumentException("Property ID already exists");
        }
        Property saved = new Property(
                draft.propertyId() == null ? UUID.randomUUID() : draft.propertyId(),
                host.userId(), ListingStatus.ACTIVE, draft.title(), draft.description(),
                draft.propertyType(), draft.streetAddress(), draft.city(), draft.region(),
                draft.postalCode(), draft.maxGuests(), draft.bedrooms(), draft.bathrooms(),
                draft.baseNightlyRate(), draft.checkInTime(), draft.checkOutTime(),
                draft.amenities(), draft.createdAt() == null ? Instant.now() : draft.createdAt());
        Property persisted = properties.save(saved);
        audit.record(host.userId(), "LISTING_CREATED", "PROPERTY", persisted.propertyId(),
                null, persisted);
        return persisted;
    }

    @Override
    public Property updateStatus(UUID propertyId, ListingStatus status, UUID hostId) {
        User host = requireActiveHost(hostId);
        if (status == null) {
            throw new IllegalArgumentException("Listing status must not be null");
        }
        Property existing = properties.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property does not exist"));
        if (!host.userId().equals(existing.hostId())) {
            throw new IllegalStateException("Host does not own this property");
        }
        if (existing.status() == status) {
            return existing;
        }
        Property updated = new Property(existing.propertyId(), existing.hostId(), status,
                existing.title(), existing.description(), existing.propertyType(),
                existing.streetAddress(), existing.city(), existing.region(),
                existing.postalCode(), existing.maxGuests(), existing.bedrooms(),
                existing.bathrooms(), existing.baseNightlyRate(), existing.checkInTime(),
                existing.checkOutTime(), existing.amenities(), existing.createdAt());
        Property persisted = properties.save(updated);
        audit.record(host.userId(), "LISTING_STATUS_CHANGED", "PROPERTY", propertyId,
                existing, persisted);
        return persisted;
    }

    private User requireActiveHost(UUID hostId) {
        if (hostId == null) {
            throw new IllegalStateException("Host ID must not be null");
        }
        User host = users.findById(hostId);
        AuthorizationService.requireRole(Role.HOST, host);
        if (host.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
        return host;
    }

    private static void validateDraft(Property draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Property must not be null");
        }
        DomainValidation.requireText(draft.title(), "title");
        DomainValidation.requireText(draft.description(), "description");
        DomainValidation.requireText(draft.streetAddress(), "streetAddress");
        DomainValidation.requireText(draft.city(), "city");
        DomainValidation.requireText(draft.region(), "region");
        DomainValidation.requireText(draft.postalCode(), "postalCode");
        if (draft.propertyType() == null || draft.checkInTime() == null
                || draft.checkOutTime() == null) {
            throw new IllegalArgumentException("Property type and check-in/out times are required");
        }
        if (draft.maxGuests() <= 0) {
            throw new IllegalArgumentException("Max guests must be positive");
        }
        if (draft.bedrooms() < 0) {
            throw new IllegalArgumentException("Bedrooms must be non-negative");
        }
        if (!Double.isFinite(draft.bathrooms()) || draft.bathrooms() < 0) {
            throw new IllegalArgumentException("Bathrooms must be non-negative");
        }
        DomainValidation.requireNonNegative(draft.baseNightlyRate(), "baseNightlyRate");
    }
}
