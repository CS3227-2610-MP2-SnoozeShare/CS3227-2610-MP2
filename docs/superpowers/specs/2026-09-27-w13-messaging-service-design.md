# W13 — Messaging Service (persistent ticket chat) — Design

**Status:** Spec'd 2026-09-27 · **Branch:** `messaging-service` · **Backlog:** F12.1.1, F12.1.2 ·
**Raised by:** W10 (C21, C29) · **Plan:** [W13 plan](../plans/2026-09-27-w13-messaging-service.md)

## 1. Goal

W10 shipped the agent dispute page against a `MessageService` interface backed by
`InMemoryMessageService`, a session-only **seam**: only the agent could ever write to it, and
nothing survives a restart. W13 replaces the seam with a persistent service that the **guest**,
**host** and **agent** screens all use, and fixes the interface those three screens code against.

Operator scope (2026-09-27): build the persistent service and move the agent page onto it; **define**
the guest and host interfaces but defer their UI (Messages tabs, chat panes) to a later slice.

## 2. Out of scope

- Guest Messages tab and host Messages tab UI. Only the contract they need is built (§ 5).
- Structured host dispute response notes/evidence, F7.2.2 (C29). It stays deferred; chat is not that flow.
- Chat outside tickets (guest↔host booking chat), attachments, edit/delete, typing indicators.
- Restyling chat bubbles for the guest/host themes (`agent-theme.css` classes are reused as-is).

## 3. Model

Each ticket has two threads (`ThreadChannel.GUEST`, `ThreadChannel.HOST`), unchanged from W10.

- **Parties.** Guest of a ticket = the booking's `guestId`; host = the property's `hostId`. A
  guest may read and post only in the `GUEST` thread, a host only in the `HOST` thread, any agent in
  both (F12.1.2). Suspended accounts are not blocked here; login already excludes them.
- **Open/closed.** Threads accept posts while the ticket is `OPEN` or `IN_REVIEW`. After resolution
  they are read-only: `post` throws `IllegalStateException("Ticket is resolved")`.
- **Order.** Insertion order (`rowid`), not the `sentAt` text, to avoid the variable-fraction
  `Instant.toString()` misordering already accepted for audit (§ Known Gaps).
- **Read state.** `message_reads` stores, per (ticket, channel, user), the last message id read.
  Unread = messages in that thread after it, written by someone else.

## 4. Persistence

Migration `V004__messaging.sql`, applied or adopted by `MigrationRunner`:

```sql
CREATE TABLE messages (
    messageId  TEXT PRIMARY KEY,
    ticketId   TEXT NOT NULL REFERENCES tickets(ticketId),
    channel    TEXT NOT NULL CHECK (channel IN ('GUEST','HOST')),
    authorId   TEXT NOT NULL REFERENCES users(userId),
    authorRole TEXT NOT NULL CHECK (authorRole IN ('GUEST','HOST','AGENT')),
    body       TEXT NOT NULL,
    sentAt     TEXT NOT NULL
);
CREATE INDEX idx_messages_thread ON messages(ticketId, channel);
CREATE TABLE message_reads (
    ticketId           TEXT NOT NULL REFERENCES tickets(ticketId),
    channel            TEXT NOT NULL CHECK (channel IN ('GUEST','HOST')),
    userId             TEXT NOT NULL REFERENCES users(userId),
    lastReadMessageId  TEXT NOT NULL REFERENCES messages(messageId),
    PRIMARY KEY (ticketId, channel, userId)
);
```

`db/schema.sql` gains both tables, `db/seed-mock-data.sql` gains a short thread on each open
ticket and records version 4, and `db/snoozeshare-mock.db` is rebuilt (it ships migrated).
Layering follows the repo: `repository.MessageRepository` + `repository.jdbc.JdbcMessageRepository`;
only the JDBC class touches `java.sql`.

## 5. Interface contract

```java
public interface MessageService {
    /** Oldest first. Viewer must be a party of the channel or an agent, else IllegalArgumentException. */
    List<Message> thread(UUID ticketId, ThreadChannel channel, UUID viewerId, Role viewerRole);

    /** Blank body, wrong channel for the role, non-party author, or resolved ticket are rejected. */
    Message post(UUID ticketId, ThreadChannel channel, UUID authorId, Role authorRole, String body);

    /** Guest/Host inbox: one entry per ticket the user is a party to, most recent activity first. */
    List<ConversationSummary> conversationsFor(UUID userId, Role role);

    /** Total unread across the user's conversations (tab badge). */
    int unreadCount(UUID userId, Role role);

    /** Marks the thread read up to its newest message. No-op on an empty thread. */
    void markRead(UUID ticketId, ThreadChannel channel, UUID userId, Role role);
}

public record ConversationSummary(UUID ticketId, ThreadChannel channel, String ticketLabel,
        String ticketTitle, String listingTitle, String counterpartName,
        Message lastMessage /* nullable */, int unreadCount, boolean open) { }
```

`post` publishes `MessagePostedEvent(Message message, Instant occurredAt, UUID eventId)` after the
insert commits (`infra.events.events`), so every open screen can refresh without polling.

### Who uses what

| Screen | Reads | Writes | Refresh |
|---|---|---|---|
| Agent dispute detail (built now) | `thread(..., agentId, AGENT)` both channels | `post(..., AGENT, body)`; input disabled when the ticket is resolved | subscribes to `MessagePostedEvent` for its ticket |
| Guest Messages tab (deferred) | `conversationsFor(guestId, GUEST)`, `thread(t, GUEST, guestId, GUEST)` | `post(t, GUEST, guestId, GUEST, body)`, `markRead` | `MessagePostedEvent`, `unreadCount` badge |
| Host Messages tab (deferred) | `conversationsFor(hostId, HOST)`, `thread(t, HOST, hostId, HOST)` | `post(t, HOST, hostId, HOST, body)`, `markRead` | same |

Shared UI piece (built now): `ui.common.messaging.ChatBubbles.render(VBox, List<Message>, UUID viewerId)`
draws bubbles, own messages on the right. The agent page uses it; guest and host reuse it.

## 6. Decisions

- **Replace, not wrap.** `InMemoryMessageService` and its test are deleted; the service signature
  changes (`thread` gains the viewer). The only caller is `DisputeDetailController`.
- **Party check in the service, not the UI** (F12.1.2), using ticket → booking → property.
- **Resolved tickets are read-only.** New rule; W10 allowed posting forever because no one else could.
- **Read tracking is included** so the guest/host tabs need no later schema change.

## 7. Tests

Service: thread isolation per channel and viewer, party checks (guest cannot read HOST thread, a
stranger cannot read or post), resolved-ticket rejection, blank body, ordering, unread counts and
`markRead`, event published once and only after a successful post, `conversationsFor` ordering.
Repository and migration: fresh, adopted and mock-DB paths; `SchemaParityTest`;
`CommittedMockDbTest`. UI: agent page renders seeded messages, sends, disables input on resolved
tickets, refreshes on `MessagePostedEvent`.

## 8. Acceptance

Agent posts on the dispute page, the message survives an app restart against the same DB file, a
message posted through the service by the guest or host appears on the open agent page without a
reload, and the full suite plus Checkstyle pass.
