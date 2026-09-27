package com.snoozeshare.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.enums.ListingStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.infra.events.events.AccountStatusChangedEvent;
import com.snoozeshare.testsupport.FailingAuditService;

class AccountGovernanceAtomicityTest {

    @Test
    void anAuditFailureDuringTheCascadeRollsBackStatusBookingsWalletsAndListings() throws Exception {
        // Calls: 1 ACCOUNT_SUSPENDED, 2 LISTING_STATUS_CASCADE, 3 BOOKING_FORCE_CANCELLED, 4 ESCROW_REFUND
        // (the money row, written by the ledger on the same audit service).
        for (int failingCall = 3; failingCall <= 4; failingCall++) {
            assertCascadeRollsBack(failingCall);
        }
    }

    private static void assertCascadeRollsBack(int failingCall) throws Exception {
        String label = "failing audit call " + failingCall;
        try (AccountFixture fixture = new AccountFixture()) {
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);
            Booking pending = fixture.booking(guest, loft, BookingStatus.PENDING,
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7));
            BigDecimal balance = fixture.balance(guest);
            int auditBefore = fixture.audit.search(AuditFilter.none(), 50, 0).size();
            List<Object> events = new ArrayList<>();
            fixture.bus.subscribe(AccountStatusChangedEvent.class, events::add);
            var service = fixture.service(new FailingAuditService(fixture.audit, failingCall));

            assertThrows(RuntimeException.class, () -> service.suspend(host.userId(), fixture.agent.userId(), "fraud"),
                    label);

            User stored = fixture.users.findById(host.userId()).orElseThrow();
            assertEquals(AccountStatus.ACTIVE, stored.accountStatus(), label);
            assertNull(stored.suspensionReason(), label);
            assertEquals(ListingStatus.ACTIVE, fixture.properties.findById(loft.propertyId()).orElseThrow().status(),
                    label);
            assertEquals(BookingStatus.PENDING, fixture.bookings.findById(pending.bookingId()).orElseThrow()
                    .status(), label);
            assertEquals(0, balance.compareTo(fixture.balance(guest)), "no refund was kept: " + label);
            assertEquals(auditBefore, fixture.audit.search(AuditFilter.none(), 50, 0).size(),
                    "no audit row was kept: " + label);
            assertEquals(0, events.size(), "nothing is published for a rolled-back change: " + label);
        }
    }

    @Test
    void aMissingGuestWalletRollsBackTheSuspension() throws Exception {
        try (AccountFixture fixture = new AccountFixture()) {
            User host = fixture.user(Role.HOST, "Marcus", "marcus@test.com", "2026-02-02T10:00:00Z");
            User guest = fixture.user(Role.GUEST, "Priya", "priya@test.com", "2026-03-05T08:00:00Z");
            Property loft = fixture.property(host, ListingStatus.ACTIVE);
            fixture.booking(guest, loft, BookingStatus.PENDING, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7));
            fixture.connection.createStatement().executeUpdate("PRAGMA foreign_keys = OFF");
            String deleteWallet = "DELETE FROM wallets WHERE userId = '" + guest.userId() + "'";
            fixture.connection.createStatement().executeUpdate(deleteWallet);

            var service = fixture.service();

            assertThrows(IllegalArgumentException.class, () -> service.suspend(host.userId(),
                    fixture.agent.userId(), "fraud"));

            assertEquals(AccountStatus.ACTIVE, fixture.users.findById(host.userId()).orElseThrow().accountStatus());
        }
    }
}
