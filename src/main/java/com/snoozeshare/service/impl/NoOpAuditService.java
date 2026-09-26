package com.snoozeshare.service.impl;

import java.util.List;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.service.AuditFilter;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;

/** Discards every row; for tests that exercise a service without asserting on the audit log. */
public final class NoOpAuditService implements AuditService {

    @Override
    public void record(AuditRecord record) {
        // intentionally empty
    }

    @Override
    public List<AuditLogEntry> search(AuditFilter filter, int limit, int offset) {
        return List.of();
    }
}
