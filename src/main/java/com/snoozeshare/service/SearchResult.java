package com.snoozeshare.service;

import com.snoozeshare.domain.model.Property;

public record SearchResult(
        Property property,
        boolean available
) {
}
