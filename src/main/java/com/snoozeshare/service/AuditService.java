package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;

public interface AuditService {

    /** The seeded, non-loginable user that owns system-initiated rows. */
    UUID SYSTEM_ACTOR_ID = UUID.fromString("a0000000-0000-0000-0000-0000000000ff");

    /** Writes one row on the caller's connection, so it commits or rolls back with the caller's change. */
    void record(AuditRecord record);

    /** Newest first. Text is a name/email/id fragment; a name is resolved to user ids before querying. */
    List<AuditLogEntry> search(AuditFilter filter, int limit, int offset);
}
