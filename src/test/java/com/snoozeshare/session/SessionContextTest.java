package com.snoozeshare.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

class SessionContextTest {

    @Test
    void loginSetsCurrentUserAndRoleAndLogoutClearsBoth() {
        MockSessionContext session = new MockSessionContext();
        User user = new User(UUID.randomUUID(), Role.HOST, "Host", "host@example.com",
                AccountStatus.ACTIVE, null, Instant.parse("2026-09-23T00:00:00Z"));

        session.loginAs(user);

        assertEquals(user, session.currentUser().orElseThrow());
        assertEquals(Role.HOST, session.currentRole());

        session.logout();

        assertTrue(session.currentUser().isEmpty());
        assertNull(session.currentRole());
    }
}
