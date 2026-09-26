package com.snoozeshare.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.snoozeshare.domain.model.AuditLogEntry;

public interface AuditLogRepository {
    AuditLogEntry save(AuditLogEntry entry);

    /** Newest first; rows written at the same instant keep insertion order. */
    List<AuditLogEntry> search(AuditCriteria criteria, int limit, int offset);

    /** Ids of users whose current name/email, or a name recorded in the log, contains the fragment. */
    Set<UUID> findUserIdsByName(String fragment);
}
