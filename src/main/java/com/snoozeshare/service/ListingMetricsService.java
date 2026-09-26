package com.snoozeshare.service;

import java.util.UUID;

public interface ListingMetricsService {
    ListingMetrics metricsFor(UUID propertyId);
}
