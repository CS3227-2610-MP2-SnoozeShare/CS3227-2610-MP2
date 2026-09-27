# W13 — Host Messages page — Design

**Status:** Draft for operator review 2026-09-27 · **Branch:** `w9` · **Backlog:** F12.1.1,
F12.1.2 · **Depends on:** [W13 messaging service design](2026-09-27-w13-messaging-service-design.md)

## 1. Goal

Add the Host Messages page as the third Host navigation tab. The page gives a host one inbox
for the two conversations already supported by the service layer:

1. private booking chat with a guest; and
2. the host-side thread of a support ticket.

The page follows the supplied Messages mockup and reuses the existing Fall Light shell and
chat-bubble visual language. Hosts do not create tickets; they use the host-side ticket thread to
respond to tickets opened by guests and handled by Support Agents.

## 2. Scope and success criteria

In scope:

- Replace the current non-interactive Host Messages placeholder with a working tab between
  Requests and Wallet.
- Show booking conversations and host-visible ticket conversations in one newest-activity-first
  list, with unread state and the selected row highlighted.
- Render the selected thread, send text replies, mark it read, and refresh on the existing
  `BookingMessagePostedEvent` / `MessagePostedEvent` events.
- Show the correct read-only state after a ticket is resolved or a booking chat window closes.
- Keep ticket creation out of the Host UI and service boundary; hosts may only reply to existing
  agent-managed ticket threads.
- Add controller, navigation, persistence-boundary, and JavaFX regression coverage.

Out of scope:

- Guest Messages UI; it remains deferred even though the same services support it.
- Attachments, typing indicators, editing/deleting messages, ticket evidence upload, and a new
  ticket state machine.
- A schema change for host ticket ownership; `Ticket.raisedByRole` and the existing ticket
  request model are reused.

Success means a host can open the tab, see both seeded booking/ticket conversations, exchange a
message with the correct counterparty, see unread/read state update, and file a ticket only for a
booking and property they are authorized to act on. Wrong-party and closed-thread service calls
remain rejected even if the UI is bypassed.

## 3. Visual and interaction design

The layout follows the supplied screenshot, adapted to the existing 1280×800 minimum window and
Fall Light theme:

- **Navigation:** `Listings · Requests · Messages · Wallet`; Messages is the third tab and is
  selected with the existing active-tab treatment.
- **Split view:** a fixed/narrow left conversation pane (approximately 27% of the available
  width) and a flexible right thread pane, separated by the existing warm border token.
- **Left pane:** `Messages` heading, then rows with a bold title, muted subtitle, optional status
  pill (`OPEN`, `IN_REVIEW`, `RESOLVED`), and selected-row peach background. Booking rows use
  `<property> · <start>–<end>` and `Direct message · <guest> (Guest)`. Ticket rows use the ticket
  title and `Ticket #<short id> · <property> · Support Agent`.
- **Thread header:** selected booking/property and dates on the first line; counterpart and
  booking/ticket reference on the second line.
- **Messages:** scrollable content, incoming bubbles left in neutral beige, host bubbles right
  in warm peach, author name above each body. `ChatBubbles` remains the shared renderer where
  its `Message` shape fits; booking messages get the same style through a small shared overload
  or equivalent common renderer rather than duplicating CSS.
- **Composer:** bottom-pinned text field with `Write a reply...` prompt and `Send` button. It is
  disabled/hidden when the selected ticket or booking thread is read-only. Empty inbox and empty
  thread states are explicit and non-error states.

## 4. Unified inbox behavior

The controller builds a UI projection from the two service summary types; it does not reach into
repositories or the database. Each row retains its kind (`BOOKING` or `TICKET`), source id,
display metadata, latest message, unread count, and whether posting is currently allowed.

| Selection | Read | Write | Read state |
|---|---|---|---|
| Booking conversation | `BookingConversationService.thread(bookingId, hostId, HOST)` | `post(bookingId, hostId, HOST, body)` | `markRead(bookingId, hostId, HOST)` |
| Ticket conversation | `MessageService.thread(ticketId, HOST, hostId, HOST)` | `post(ticketId, HOST, hostId, HOST, body)` | `markRead(ticketId, HOST, hostId, HOST)` |

Inbox rows come from `BookingConversationService.conversationsFor(hostId, HOST)` and
`MessageService.conversationsFor(hostId, HOST)`, merged by newest message/activity. A selected
row is marked read after its thread loads. The tab badge, if the shell exposes one, is the sum of
the two service `unreadCount` values.

The page subscribes to both message-posted events, refreshes the inbox, and reloads the selected
thread when its source id matches. Subscription handles are disposed when the page is replaced.
Service exceptions become an inline error/status message and do not leave a stale enabled Send
button.

## 5. Host ticket responses

Hosts cannot create support tickets. Existing ticket rows are supplied by the persistent
`MessageService` and expose the host-side thread only when the host is the authorized booking
party. An open or in-review ticket remains writable through the composer so the host can respond
to the assigned Support Agent; resolved tickets are read-only. Guest ticket filing and Agent
ticket assignment/resolution remain unchanged.

## 6. Architecture and files

Expected implementation shape:

- Extend `HostShellController` and `host-shell.fxml` with the Messages tab and page replacement
  flow, preserving Wallet's existing full-center behavior.
- Add a `ui.host.messaging` controller/FXML pair plus a small UI projection/row model for the
  merged inbox. The controller depends only on service interfaces, `SessionContext`, and the
  event bus.
- Reuse the existing `ChatBubbles` styles and add the smallest common renderer extension needed
  for `BookingMessage`; keep host-specific layout rules in the existing Fall Light stylesheet.
- Keep `TicketService.fileTicket` guest-only; Host Messages must not expose a ticket-creation
  callback or dialog.
- Do not add repository access from UI or bypass service authorization.

## 7. Verification

Service tests cover guest ticket filing and reject Host ticket creation. Host UI tests cover
navigation order, merged row ordering, selected thread rendering, send/read-only states, event
refresh, empty states, and absence of ticket-creation controls. FXML/CSS
validation and the existing full Gradle test/build checks remain required.

Review focus before implementation: the host eligibility query and the exact read-only behavior
for completed bookings versus the booking service's existing `checkout + 7 days` rule.
