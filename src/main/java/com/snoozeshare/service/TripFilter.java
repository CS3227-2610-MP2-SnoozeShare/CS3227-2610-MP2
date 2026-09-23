package com.snoozeshare.service;

import com.snoozeshare.domain.enums.BookingStatus;

public record TripFilter(BookingStatus status) {
}
