package com.snoozeshare.testsupport;

import java.util.List;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.service.AuditFilter;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;

/**
 * Audit test double that delegates to a real service but throws on the Nth {@code record} call, so a
 * test can prove that an audit failure rolls back the change it was auditing (spec section 9).
 */
public final class FailingAuditService implements AuditService {

    private final AuditService delegate;
    private final int failingCall;
    private int calls;

    /** @param failingCall 1-based index of the record call that throws */
    public FailingAuditService(AuditService delegate, int failingCall) {
        this.delegate = delegate;
        this.failingCall = failingCall;
    }

    @Override
    public void record(AuditRecord record) {
        calls++;
        if (calls == failingCall) {
            throw new RuntimeException("Injected audit failure on call " + calls);
        }
        delegate.record(record);
    }

    @Override
    public List<AuditLogEntry> search(AuditFilter filter, int limit, int offset) {
        return delegate.search(filter, limit, offset);
    }
}
