package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.model.Review;
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

class JdbcReviewRepositoryTest {

    @Test
    void findByGuestIdReturnsOnlyReviewsForThatGuest() throws Exception {
        try (Connection connection = migratedConnection()) {
            var repository = new JdbcReviewRepository(connection);
            UUID guestId = UUID.randomUUID();
            UUID otherGuestId = UUID.randomUUID();
            var users = new JdbcUserRepository(connection);
            users.save(new User(guestId, Role.GUEST, "Guest", guestId + "@test.com",
                    AccountStatus.ACTIVE, null, Instant.now()));
            users.save(new User(otherGuestId, Role.GUEST, "Other guest",
                    otherGuestId + "@test.com", AccountStatus.ACTIVE, null, Instant.now()));
            UUID hostId = UUID.randomUUID();
            users.save(new User(hostId, Role.HOST, "Host", hostId + "@test.com",
                    AccountStatus.ACTIVE, "HOST", Instant.now()));
            UUID propertyId = UUID.randomUUID();
            new JdbcPropertyRepository(connection).save(new Property(propertyId, hostId,
                    ListingStatus.ACTIVE, "Test", "Description", PropertyType.APARTMENT,
                    "Street", "City", "Region", "000000", 2, 1, 1.0,
                    new BigDecimal("100.00"), LocalTime.NOON, LocalTime.of(11, 0), Set.of(),
                    Instant.now()));
            UUID bookingId = UUID.randomUUID();
            new JdbcBookingRepository(connection).save(new Booking(bookingId, propertyId, guestId,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
                    BookingStatus.CONFIRMED, new BigDecimal("100.00"), new BigDecimal("200.00"),
                    Instant.now(), Instant.now(), null));
            repository.save(new Review(UUID.randomUUID(), bookingId, guestId,
                    5, "great", Instant.now()));
            repository.save(new Review(UUID.randomUUID(), bookingId, otherGuestId,
                    1, "poor", Instant.now()));

            assertEquals(1, repository.findByGuestId(guestId).size());
            assertEquals(0, repository.findByGuestId(UUID.randomUUID()).size());
        }
    }

    private static Connection migratedConnection() throws Exception {
        Connection connection = ConnectionFactory.open("jdbc:sqlite::memory:");
        MigrationRunner.migrate(connection);
        return connection;
    }
}
