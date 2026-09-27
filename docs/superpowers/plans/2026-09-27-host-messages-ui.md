# Host Messages UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Host Messages page as the third Host navigation tab, combining booking chat and host-side ticket chat. Hosts respond to existing tickets; they do not create tickets.

**Architecture:** Keep the two existing messaging services as the authorization and persistence boundary. Add a host-only UI projection that merges their summaries and a reusable booking-message bubble renderer. The Host shell loads the page into its existing center region and owns cleanup when navigation changes.

**Tech Stack:** Java 25, JavaFX 25/FXML, Gradle, JUnit 5/TestFX, existing in-process event bus, existing Fall Light `agent-theme.css`.

**Spec:** [Host Messages UI design](../specs/2026-09-27-host-messages-ui-design.md)

## Global Constraints

- Host navigation order is exactly `Listings · Requests · Messages · Wallet`.
- UI code depends on service interfaces, `SessionContext`, and the event bus; it must not access repositories or JDBC.
- Booking chat remains private to the booking guest and host; agents cannot read it.
- Ticket chat is the host channel and becomes read-only after ticket resolution.
- Booking chat is writable only while `CONFIRMED` and through checkout + 7 days; history remains readable after that.
- Existing guest ticket filing behavior and validation must remain unchanged.
- No ticket creation, attachments, typing indicators, edit/delete, or schema changes for host ticket ownership.

## Review Focus

- Host ticket creation must be rejected by the service: `FileTicketTest.hostCannotFileTicket`.
- A closed booking thread and resolved ticket thread must render history while disabling the composer: `HostMessagesControllerTest.closedThreadIsReadOnly`.
- The merged inbox must preserve newest-activity-first ordering across booking and ticket summaries: `HostMessagesControllerTest.mergesAndOrdersBookingAndTicketRows`.
- Event subscriptions must refresh only the affected inbox/thread and be disposed on navigation: `HostMessagesControllerTest.messageEventsRefreshSelectedThread` and shell cleanup assertions.

## File Map

- Modify `src/main/java/com/snoozeshare/service/TicketService.java` and `TicketServiceImpl.java` to keep ticket filing guest-only.
- Modify `src/main/java/com/snoozeshare/ui/common/messaging/ChatBubbles.java` and its tests for booking-message rendering.
- Create `src/main/java/com/snoozeshare/ui/host/messaging/HostMessagesController.java`, its inbox-row model, and `host-messages.fxml`.
- Do not add a Host ticket dialog; the existing Guest ticket form remains the only filing UI.
- Modify `HostShellController.java`, `host-shell.fxml`, and `ShellNavigationTest.java`.
- Modify/add host messaging, service, and ticket filing tests; update `PROJECT_STATE.md` and the Done ledger only after verification.

### Task 1: Keep ticket filing guest-only

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/TicketService.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/TicketServiceImpl.java`
- Test: `src/test/java/com/snoozeshare/service/FileTicketTest.java`
- Test: `src/test/java/com/snoozeshare/service/TicketServiceTest.java` if the existing fixture uses that split

**Interfaces:**
- Keeps `Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole)` for guests and rejects `Role.HOST`.

- [x] **Step 1:** Add a regression test proving Host ticket filing is rejected while Guest filing remains valid.
- [x] **Step 2:** Remove the host candidate query and enforce guest-only filing in `TicketServiceImpl`.
- [x] **Step 3:** Run focused service tests.

### Task 2: Add booking-message rendering and host inbox projection

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/common/messaging/ChatBubbles.java`
- Modify/create: `src/test/java/com/snoozeshare/ui/common/messaging/ChatBubblesTest.java`
- Create: `src/main/java/com/snoozeshare/ui/host/messaging/HostConversationRow.java`
- Test: `src/test/java/com/snoozeshare/ui/host/HostMessagesControllerTest.java`

**Interfaces:**
- `HostConversationRow` carries kind, source id, title, subtitle, counterpart, status/open state, unread count, last-activity timestamp, and last-message preview.
- The common renderer accepts `List<BookingMessage>` with a host-owned outgoing predicate while preserving the existing `List<Message>` API.

- [ ] **Step 1: Write failing renderer/projection tests** for incoming/outgoing booking bubbles, ticket/booking row labels, status pills, and deterministic newest-first merge ordering.
- [ ] **Step 2: Run the focused JavaFX tests** and confirm failures identify the missing renderer/projection behavior.
- [ ] **Step 3: Implement the minimal booking-message renderer** using the existing chat CSS classes and the same author/body structure as ticket bubbles.
- [ ] **Step 4: Implement the UI projection/merge utility** without repository access; map `BookingConversationSummary` and `ConversationSummary` to `HostConversationRow` and sort by last message/activity newest first with a stable id tie-breaker.
- [ ] **Step 5: Run the focused tests** and confirm rendering and ordering pass.
- [ ] **Step 6: Commit** `feat: add host conversation projection and booking bubbles`.

### Task 3: Build the Host Messages page

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/host/messaging/HostMessagesController.java`
- Create: `src/main/resources/com/snoozeshare/ui/host/messaging/host-messages.fxml`
- Modify: `src/main/resources/com/snoozeshare/ui/admin/agent-theme.css`
- Test: `src/test/java/com/snoozeshare/ui/host/HostMessagesControllerTest.java`

**Interfaces:**
- Controller setup accepts `AppContext` and the current Host session.
- Controller cleanup unsubscribes both message event subscriptions.

- [ ] **Step 1: Write failing controller tests** for loading the split layout, empty inbox, selecting a booking row, selecting a ticket row, rendering the correct header/bubbles, sending a trimmed reply, marking read, and disabling the composer for closed threads.
- [ ] **Step 2: Run the focused controller tests** and confirm the page/controller is not yet available.
- [ ] **Step 3: Implement inbox loading** from both `BookingConversationService.conversationsFor` and `MessageService.conversationsFor`, using the projection from Task 2.
- [ ] **Step 4: Implement selection and thread loading** with the exact service calls from the approved spec; call the matching `markRead` after load and show a non-error empty state when there are no messages.
- [ ] **Step 5: Implement send behavior** with blank-body rejection, service call, input clearing, and immediate thread/inbox refresh. Respect each service's read-only state.
- [ ] **Step 6: Subscribe to `BookingMessagePostedEvent` and `MessagePostedEvent`**, refresh the affected row/thread, and expose `cleanup()` for shell navigation.
- [ ] **Step 7: Implement the FXML/CSS layout**: left conversation list, selected peach row, right header, status pills, scrollable bubble area, and bottom composer.
- [ ] **Step 8: Run the focused UI tests** and inspect a rendered snapshot at the 1280×800 minimum size.
- [ ] **Step 9: Commit** `feat: implement host messages page`.

### Task 4: Host ticket creation is intentionally absent

No Host ticket dialog, callback, candidate query, or Host filing test is maintained. Hosts respond
through the existing ticket composer; Guest filing remains covered by `FileTicketTest` and Guest UI
tests.

### Task 5: Add Messages to Host navigation

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/host/HostShellController.java`
- Modify: `src/main/resources/com/snoozeshare/ui/host/host-shell.fxml`
- Modify: `src/test/java/com/snoozeshare/ui/ShellNavigationTest.java`

**Interfaces:**
- `HostShellController.showMessages()` loads `host-messages.fxml`, configures `HostMessagesController`, and keeps the controller for cleanup.
- Existing Listings, Requests, and Wallet behavior remains unchanged.

- [ ] **Step 1: Write failing shell tests** asserting exactly four tabs in order and that selecting Messages loads the Messages root and active style.
- [ ] **Step 2: Run the focused shell tests** and confirm the current three-tab shell fails the new assertions.
- [ ] **Step 3: Implement navigation and lifecycle cleanup** for the Messages controller alongside the existing Wallet cleanup.
- [ ] **Step 4: Run shell tests and existing navigation tests** and confirm no regression in wallet/listings/request routing.
- [ ] **Step 5: Commit** `feat: add host messages navigation`.

### Task 6: Full verification and project-state handoff

**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`
- Modify: `docs/ProductBacklog.md` only if the implementation changes the F12 wording

- [ ] **Step 1: Run focused service and UI tests** for all new/changed classes.
- [ ] **Step 2: Run `./gradlew test`** and record any failures, distinguishing pre-existing D21 failures from regressions.
- [ ] **Step 3: Run `./gradlew build`** for Checkstyle and packaged-resource verification.
- [ ] **Step 4: Run the app against a copy of `db/snoozeshare-mock.db`** and visually inspect Host Messages at the screenshot's layout: merged rows, selected thread, bubbles, composer, status pills, and ticket dialog.
- [ ] **Step 5: Update `PROJECT_STATE.md` immediately** with the final W13 progress, architecture changes, session handoff, spec/plan links, verification evidence, and any deviations.
- [ ] **Step 6: Add one newest-first Done ledger entry** naming the Host Messages page and response-only Host ticket behavior; keep ledger numbering/order consistent with the existing newest-first format.
- [ ] **Step 7: Commit** `docs: record host messages delivery and verification`.
