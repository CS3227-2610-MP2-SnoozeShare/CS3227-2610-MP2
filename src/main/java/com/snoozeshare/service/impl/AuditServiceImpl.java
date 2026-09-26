package com.snoozeshare.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.repository.AuditCriteria;
import com.snoozeshare.repository.AuditLogRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.AuditFilter;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;

public final class AuditServiceImpl implements AuditService {

    private static final Pattern ID_FRAGMENT = Pattern.compile("[0-9a-fA-F-]{4,36}");

    private final AuditLogRepository entries;
    private final UserRepository users;
    private final Clock clock;

    public AuditServiceImpl(AuditLogRepository entries, UserRepository users, Clock clock) {
        this.entries = entries;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public void record(AuditRecord record) {
        Instant at = record.at() == null ? clock.instant() : record.at();
        entries.save(new AuditLogEntry(UUID.randomUUID(), record.actorId(), nameOf(record.actorId()),
                record.action().name(), record.entityType(), record.entityId(), record.beforeState(),
                record.afterState(), record.walletAdjustment(), record.reason(), record.subjectUserId(),
                record.subjectUserId() == null ? null : nameOf(record.subjectUserId()), record.bookingId(),
                record.ticketId(), at));
    }

    @Override
    public List<AuditLogEntry> search(AuditFilter filter, int limit, int offset) {
        String text = filter.text() == null ? "" : filter.text().trim();
        boolean textGiven = !text.isEmpty();
        Set<UUID> userIds = textGiven ? entries.findUserIdsByName(text) : Set.of();
        String fragment = textGiven && ID_FRAGMENT.matcher(text).matches() ? text.toLowerCase(Locale.ROOT) : null;
        Instant from = filter.from() == null ? null
                : filter.from().atStartOfDay(clock.getZone()).toInstant();
        Instant toExclusive = filter.to() == null ? null
                : filter.to().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        AuditCriteria criteria = new AuditCriteria(textGiven, userIds, fragment,
                filter.actions().stream().map(Enum::name).collect(Collectors.toSet()), from, toExclusive);
        return entries.search(criteria, limit, offset);
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(User::displayName).orElse("Unknown user");
    }
}
