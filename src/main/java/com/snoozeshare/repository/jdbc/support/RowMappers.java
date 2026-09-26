package com.snoozeshare.repository.jdbc.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.AmenityType;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.PropertyType;
import com.snoozeshare.domain.enums.RemedyType;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.enums.WalletTransactionType;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.Review;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.domain.model.Wallet;
import com.snoozeshare.domain.model.WalletTransaction;

public final class RowMappers {

    private RowMappers() {
    }

    public static User user(ResultSet result) throws SQLException {
        return new User(
                JdbcCodecs.uuid(result.getString("userId")),
                Role.valueOf(result.getString("role")),
                result.getString("displayName"),
                result.getString("email"),
                AccountStatus.valueOf(result.getString("accountStatus")),
                result.getString("registrationCode"),
                JdbcCodecs.instant(result.getString("createdAt")));
    }

    public static Wallet wallet(ResultSet result) throws SQLException {
        return new Wallet(
                JdbcCodecs.uuid(result.getString("walletId")),
                JdbcCodecs.uuid(result.getString("userId")),
                JdbcCodecs.decimal(result.getString("balance")),
                result.getString("currency"),
                JdbcCodecs.instant(result.getString("updatedAt")));
    }

    public static WalletTransaction walletTransaction(ResultSet result) throws SQLException {
        return new WalletTransaction(
                JdbcCodecs.uuid(result.getString("transactionId")),
                JdbcCodecs.uuid(result.getString("walletId")),
                WalletTransactionType.valueOf(result.getString("type")),
                JdbcCodecs.decimal(result.getString("amount")),
                JdbcCodecs.decimal(result.getString("feeAmount")),
                JdbcCodecs.decimal(result.getString("balanceAfter")),
                JdbcCodecs.uuid(result.getString("relatedBookingId")),
                JdbcCodecs.uuid(result.getString("relatedTicketId")),
                JdbcCodecs.uuid(result.getString("initiatedBy")),
                JdbcCodecs.instant(result.getString("createdAt")));
    }

    public static Property property(ResultSet result) throws SQLException {
        return new Property(
                JdbcCodecs.uuid(result.getString("propertyId")),
                JdbcCodecs.uuid(result.getString("hostId")),
                ListingStatus.valueOf(result.getString("status")),
                result.getString("title"),
                result.getString("description"),
                PropertyType.valueOf(result.getString("propertyType")),
                result.getString("streetAddress"),
                result.getString("city"),
                result.getString("region"),
                result.getString("postalCode"),
                result.getInt("maxGuests"),
                result.getInt("bedrooms"),
                result.getDouble("bathrooms"),
                JdbcCodecs.decimal(result.getString("baseNightlyRate")),
                JdbcCodecs.localTime(result.getString("checkInTime")),
                JdbcCodecs.localTime(result.getString("checkOutTime")),
                parseAmenities(result.getString("amenities")),
                JdbcCodecs.instant(result.getString("createdAt")));
    }

    public static AvailabilityBlock availabilityBlock(ResultSet result) throws SQLException {
        return new AvailabilityBlock(
                JdbcCodecs.uuid(result.getString("blockId")),
                JdbcCodecs.uuid(result.getString("propertyId")),
                JdbcCodecs.localDate(result.getString("startDate")),
                JdbcCodecs.localDate(result.getString("endDate")),
                result.getString("source"),
                JdbcCodecs.uuid(result.getString("bookingId")),
                result.getString("reason"));
    }

    public static Booking booking(ResultSet result) throws SQLException {
        return new Booking(
                JdbcCodecs.uuid(result.getString("bookingId")),
                JdbcCodecs.uuid(result.getString("listingId")),
                JdbcCodecs.uuid(result.getString("guestId")),
                JdbcCodecs.localDate(result.getString("startDate")),
                JdbcCodecs.localDate(result.getString("endDate")),
                BookingStatus.valueOf(result.getString("status")),
                JdbcCodecs.decimal(result.getString("nightlyRateSnapshot")),
                JdbcCodecs.decimal(result.getString("totalAmount")),
                JdbcCodecs.instant(result.getString("createdAt")),
                JdbcCodecs.instant(result.getString("decidedAt")),
                JdbcCodecs.instant(result.getString("completedAt")),
                optionalString(result, "hostDecisionMessage"));
    }

    private static Set<AmenityType> parseAmenities(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(s -> {
                    try {
                        return java.util.stream.Stream.of(AmenityType.valueOf(s));
                    } catch (IllegalArgumentException ignored) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String optionalString(ResultSet result, String column) throws SQLException {
        try {
            return result.getString(column);
        } catch (SQLException missingColumn) {
            return null;
        }
    }

    public static AuditLogEntry auditLogEntry(ResultSet result) throws SQLException {
        return new AuditLogEntry(
                JdbcCodecs.uuid(result.getString("logId")),
                JdbcCodecs.uuid(result.getString("actorUserId")),
                result.getString("actionType"),
                result.getString("entityType"),
                JdbcCodecs.uuid(result.getString("entityId")),
                result.getString("beforeState"),
                result.getString("afterState"),
                JdbcCodecs.instant(result.getString("timestamp")));
    }

    public static Ticket ticket(ResultSet result) throws SQLException {
        return new Ticket(
                JdbcCodecs.uuid(result.getString("ticketId")),
                JdbcCodecs.uuid(result.getString("bookingId")),
                JdbcCodecs.uuid(result.getString("raisedByUserId")),
                Role.valueOf(result.getString("raisedByRole")),
                result.getString("category"),
                result.getString("title"),
                result.getString("description"),
                RemedyType.valueOf(result.getString("requestedRemedy")),
                result.getString("supportingText"),
                TicketStatus.valueOf(result.getString("status")),
                JdbcCodecs.uuid(result.getString("assignedAgentId")),
                result.getString("agentNotes"),
                result.getString("resolutionReason"),
                JdbcCodecs.instant(result.getString("createdAt")),
                JdbcCodecs.instant(result.getString("resolvedAt")));
    }

    public static TicketCategory ticketCategory(ResultSet result) throws SQLException {
        return new TicketCategory(
                JdbcCodecs.uuid(result.getString("categoryId")),
                result.getString("label"),
                result.getInt("active") != 0);
    }

    public static Review review(ResultSet result) throws SQLException {
        return new Review(
                JdbcCodecs.uuid(result.getString("reviewId")),
                JdbcCodecs.uuid(result.getString("bookingId")),
                JdbcCodecs.uuid(result.getString("guestId")),
                result.getInt("rating"),
                result.getString("comment"),
                JdbcCodecs.instant(result.getString("createdAt")));
    }
}
