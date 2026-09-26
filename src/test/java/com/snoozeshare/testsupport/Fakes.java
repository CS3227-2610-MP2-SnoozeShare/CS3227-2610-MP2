package com.snoozeshare.testsupport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.AssigneeFilter;
import com.snoozeshare.domain.enums.ResolutionMode;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.enums.TicketStatus;
import com.snoozeshare.domain.model.AuditLogEntry;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.repository.TicketCategoryRepository;
import com.snoozeshare.repository.TicketRepository;
import com.snoozeshare.repository.UserRepository;
import com.snoozeshare.service.AuditFilter;
import com.snoozeshare.service.AuditRecord;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.DisputeSettlementService;
import com.snoozeshare.service.Settlement;

/**
 * Small in-memory stand-ins so service unit tests need no database.
 */
public final class Fakes {

    private Fakes() {
    }

    public static final class InMemoryTickets implements TicketRepository {
        private final Map<UUID, Ticket> store = new HashMap<>();

        @Override
        public Optional<Ticket> findById(UUID ticketId) {
            return Optional.ofNullable(store.get(ticketId));
        }

        @Override
        public List<Ticket> findByStatus(TicketStatus status) {
            return findQueue(status, AssigneeFilter.ALL, null);
        }

        @Override
        public List<Ticket> findQueue(TicketStatus status, AssigneeFilter assignee, UUID agentId) {
            return store.values().stream()
                    .filter(t -> status == null || t.status() == status)
                    .filter(t -> assignee == null || assignee == AssigneeFilter.ALL
                            || assignee == AssigneeFilter.UNASSIGNED && t.assignedAgentId() == null
                            || assignee == AssigneeFilter.MINE && agentId.equals(t.assignedAgentId()))
                    .sorted(Comparator.comparing(Ticket::createdAt))
                    .toList();
        }

        @Override
        public boolean existsByCategory(String categoryLabel) {
            return store.values().stream().anyMatch(t -> t.category().equalsIgnoreCase(categoryLabel));
        }

        @Override
        public Ticket save(Ticket ticket) {
            store.put(ticket.ticketId(), ticket);
            return ticket;
        }
    }

    public static final class InMemoryCategories implements TicketCategoryRepository {
        private final Map<UUID, TicketCategory> store = new HashMap<>();

        @Override
        public List<TicketCategory> findActive() {
            return findAll().stream().filter(TicketCategory::active).toList();
        }

        @Override
        public List<TicketCategory> findAll() {
            return store.values().stream()
                    .sorted(Comparator.comparing(c -> c.label().toLowerCase())).toList();
        }

        @Override
        public Optional<TicketCategory> findById(UUID categoryId) {
            return Optional.ofNullable(store.get(categoryId));
        }

        @Override
        public boolean labelInUse(String label, UUID excludeCategoryId) {
            return store.values().stream().anyMatch(c -> c.label().equalsIgnoreCase(label)
                    && !c.categoryId().equals(excludeCategoryId));
        }

        @Override
        public TicketCategory save(TicketCategory category) {
            store.put(category.categoryId(), category);
            return category;
        }

        @Override
        public void deleteById(UUID categoryId) {
            store.remove(categoryId);
        }
    }

    public static final class StubUsers implements UserRepository {
        private final Map<UUID, User> store = new HashMap<>();

        public StubUsers with(User user) {
            store.put(user.userId(), user);
            return this;
        }

        @Override
        public Optional<User> findById(UUID userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return store.values().stream().filter(u -> u.email().equals(email)).findFirst();
        }

        @Override
        public List<User> findByRole(Role role) {
            return store.values().stream().filter(u -> u.role() == role).toList();
        }

        @Override
        public User save(User user) {
            store.put(user.userId(), user);
            return user;
        }
    }

    public static final class RecordingAudit implements AuditService {
        private final List<AuditRecord> records = new ArrayList<>();

        public List<String> actions() {
            return records.stream().map(record -> record.action().name()).toList();
        }

        public List<AuditRecord> records() {
            return records;
        }

        @Override
        public void record(AuditRecord record) {
            records.add(record);
        }

        @Override
        public List<AuditLogEntry> search(AuditFilter filter, int limit, int offset) {
            return List.of();
        }
    }

    public static final class RecordingSettlement implements DisputeSettlementService {
        private ResolutionMode mode;
        private BigDecimal refund;
        private String reason;
        private int calls;

        public ResolutionMode mode() {
            return mode;
        }

        public BigDecimal refund() {
            return refund;
        }

        public String reason() {
            return reason;
        }

        public int calls() {
            return calls;
        }

        @Override
        public Settlement settle(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId,
                                 String reason) {
            this.calls++;
            this.mode = mode;
            this.refund = guestRefund;
            this.reason = reason;
            return new Settlement(null, null, null, null, null);
        }
    }
}
