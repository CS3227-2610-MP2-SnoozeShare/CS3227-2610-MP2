package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;

class JdbcBookingRepositoryFindByListingTest {

    @Test
    void returnsEveryBookingOfOnePropertyRegardlessOfStatus() throws Exception {
        try (Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:")) {
            MigrationRunner.migrate(connection);
            var users = new JdbcUserRepository(connection);
            var properties = new JdbcPropertyRepository(connection);
            var bookings = new JdbcBookingRepository(connection);
            Instant now = Instant.parse("2026-09-01T00:00:00Z");
            User host = users.save(new User(UUID.randomUUID(), Role.HOST, "H", "h@test.com",
                    AccountStatus.ACTIVE, "HOST2026", now));
            User guest = users.save(new User(UUID.randomUUID(), Role.GUEST, "G", "g@test.com",
                    AccountStatus.ACTIVE, null, now));
            Property one = properties.save(property(host, now));
            Property other = properties.save(property(host, now));
            bookings.save(booking(one, guest, BookingStatus.PENDING, now));
            bookings.save(booking(one, guest, BookingStatus.COMPLETED, now));
            bookings.save(booking(other, guest, BookingStatus.PENDING, now));

            assertEquals(2, bookings.findByListing(one.propertyId()).size());
            assertEquals(1, bookings.findByListing(other.propertyId()).size());
        }
    }

    private static Property property(User host, Instant now) {
        return new Property(UUID.randomUUID(), host.userId(), ListingStatus.ACTIVE, "T", "D",
                PropertyType.APARTMENT, "1 St", "Singapore", "Central", 123456, 2, 1, 1,
                new BigDecimal("100.00"), LocalTime.of(14, 0), LocalTime.of(11, 0), Set.of(), now);
    }

    private static Booking booking(Property property, User guest, BookingStatus status, Instant now) {
        return new Booking(UUID.randomUUID(), property.propertyId(), guest.userId(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), status, new BigDecimal("100.00"),
                new BigDecimal("200.00"), now, null, null);
    }
}
