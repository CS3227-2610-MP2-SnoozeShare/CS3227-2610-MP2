package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.User;

/** Agent account governance (F10): list accounts, suspend with a cascade, reactivate. */
public interface AccountGovernanceService {

    /** Every listed account, oldest first. The internal System user is not listed. */
    List<AccountSummary> listAccounts();

    /**
     * Suspends a Guest or Host and, in the same transaction, force-cancels their PENDING and not-yet-started
     * CONFIRMED bookings with a full refund and deactivates a host's ACTIVE listings (F10.1.2, C36).
     */
    User suspend(UUID userId, UUID agentId, String reason);

    /** Reactivates a suspended Guest or Host. Cancelled bookings and deactivated listings stay as they are. */
    User reactivate(UUID userId, UUID agentId, String reason);
}
