# W13 Messaging Service Implementation Plan

Spec: [W13 design](../specs/2026-09-27-w13-messaging-service-design.md). TDD per task; commit per task.
Run tests with `.\gradlew test --tests <Class>`; finish with `.\gradlew build`.

### Task 1: Contract and event
- `service/ConversationSummary` record; `infra/events/events/MessagePostedEvent`.
- Change `MessageService` to the spec § 5 interface. Temporarily keep the build green by
  updating `InMemoryMessageService` and `DisputeDetailController` call sites (removed in Tasks 4, 6).

### Task 2: Schema
- Failing tests first: extend `SchemaParityTest`, add `MessagingMigrationTest` (fresh, adopted mock DB),
  update `CommittedMockDbTest` for version 4.
- `V004__messaging.sql`; `MigrationRunner` (version 4, skip if `messages` exists); `db/schema.sql`;
  seed messages on open tickets + version 4 in `db/seed-mock-data.sql`; rebuild
  `db/snoozeshare-mock.db` (`sqlite3 x.db < schema.sql; sqlite3 x.db < seed-mock-data.sql`).

### Task 3: Repository
- `MessageRepository` (`findThread`, `save`, `markRead`, `lastMessage`, `countUnread`);
  `JdbcMessageRepository` ordering by `rowid`; `RowMappers.message`.
- `TicketRepository.findByParty(userId, role)` (join tickets → bookings → properties) plus JDBC impl.
- Tests: `JdbcMessageRepositoryTest`, extend `JdbcTicketRepositoryTest`.

### Task 4: Service
- `MessageServiceImpl(messages, tickets, bookings, properties, users, eventBus, clock)`.
- Port `InMemoryMessageServiceTest` into `MessageServiceTest` and add the spec § 7 cases; delete
  `InMemoryMessageService` and its test.

### Task 5: Wiring
- `AppContext` builds `JdbcMessageRepository` and `MessageServiceImpl`; fix `Fakes`/tests that referenced the old service.

### Task 6: Agent page
- `ui/common/messaging/ChatBubbles` (extracted from `DisputeDetailController.bubble`), with a test.
- `DisputeDetailController`: use `ChatBubbles`, pass viewer to `thread`, disable inputs when the ticket
  is resolved, subscribe to `MessagePostedEvent` for its ticket and dispose on leaving the page
  (same pattern as `AccountGovernanceController`).
- UI tests: seeded thread renders, send persists, resolved disables, event refreshes.

### Task 7: Records and verification
- `PROJECT_STATE.md` (W13 row, § Architecture messaging area, session row, decisions), Done ledger,
  `docs/ProductBacklog.md` unchanged. Developer guide untouched (checkpoint-only, § 5 of AGENTS.md).
- `.\gradlew build`; note any pre-existing red tests (D21) rather than masking them.
