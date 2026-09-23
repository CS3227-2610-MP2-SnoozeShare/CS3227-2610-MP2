package com.snoozeshare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.model.Property;

public interface PropertyRepository {
    Optional<Property> findById(UUID propertyId);

    List<Property> findByHostId(UUID hostId);

    Property save(Property property);
}
