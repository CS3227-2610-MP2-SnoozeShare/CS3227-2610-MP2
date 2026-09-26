package com.snoozeshare.ui.admin.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;

class AccountTextTest {

    @Test
    void joinedUsesDdMmmYyyyInTheGivenZone() {
        Instant instant = Instant.parse("2026-03-05T20:00:00Z");
        assertEquals("05 Mar 2026", AccountText.joined(instant, ZoneId.of("UTC")));
        assertEquals("06 Mar 2026", AccountText.joined(instant, ZoneId.of("Asia/Singapore")));
    }

    @Test
    void rolesAndStatusesReadLikeTheCanvas() {
        assertEquals("Guest", AccountText.role(Role.GUEST));
        assertEquals("Host", AccountText.role(Role.HOST));
        assertEquals("Support Agent", AccountText.role(Role.AGENT));
        assertEquals("Active", AccountText.status(AccountStatus.ACTIVE));
        assertEquals("Suspended", AccountText.status(AccountStatus.SUSPENDED));
    }
}
