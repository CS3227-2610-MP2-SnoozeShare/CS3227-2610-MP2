package com.snoozeshare.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;

public record Property(
        UUID propertyId,
        UUID hostId,
        ListingStatus status,
        String title,
        String description,
        PropertyType propertyType,
        String streetAddress,
        String city,
        String region,
        String postalCode,
        int maxGuests,
        int bedrooms,
        double bathrooms,
        BigDecimal baseNightlyRate,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        Set<AmenityType> amenities,
        Instant createdAt
) {
    public Property {
        amenities = amenities == null ? Set.of() : Set.copyOf(amenities);
    }
}
