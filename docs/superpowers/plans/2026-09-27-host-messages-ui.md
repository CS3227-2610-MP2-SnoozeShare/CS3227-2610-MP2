# Host Messages UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Host Messages page as the third Host navigation tab, combining booking chat and host-side ticket chat, with functional host ticket filing.

**Architecture:** Keep the two existing messaging services as the authorization and persistence boundary. Add a host-only UI projection that merges their summaries, a reusable booking-message bubble renderer, and a role-aware ticket filing flow. The Host shell loads the page into its existing center region and owns cleanup when navigation changes.

**Tech Stack:** Java 25, JavaFX 25/FXML, Gradle, JUnit 5/TestFX, existing in-process event bus, existing Fall Light `agent-theme.css`.

**Spec:** [Host Messages UI design](../specs/2026-09-27-host-messages-ui-design.md)

## Global Constraints

- Host navigation order is exactly `Listings · Requests · Messages · Wallet`.
- UI code depends on service interfaces, `SessionContext`, and the event bus; it must not access repositories or JDBC.
- Booking chat remains private to the booking guest and host; agents cannot read it.
- Ticket chat is the host channel and becomes read-only after ticket resolution.
- Booking chat is writable only while `CONFIRMED` and through checkout + 7 days; history remains readable after that.
- Existing guest ticket filing behavior and validation must remain unchanged.
- No attachments, typing indicators, edit/delete, or schema changes for host ticket ownership.

## Review Focus

- A host must not file against another host's booking: `FileTicketTest.hostCannotFileForAnotherHost`.
- A host's eligible-booking selector must not offer pending, cancelled, expired, or already-used bookings: `TicketServiceTest.hostTicketCandidatesFilterEligibility`.
- A closed booking thread and resolved ticket thread must render history while disabling the composer: `HostMessagesControllerTest.closedThreadIsReadOnly`.
- The merged inbox must preserve newest-activity-first ordering across booking and ticket summaries: `HostMessagesControllerTest.mergesAndOrdersBookingAndTicketRows`.
- Event subscriptions must refresh only the affected inbox/thread and be disposed on navigation: `HostMessagesControllerTest.messageEventsRefreshSelectedThread` and shell cleanup assertions.

## File Map

- Modify `src/main/java/com/snoozeshare/service/TicketService.java` and `TicketServiceImpl.java` for host authorization and ticket-filing candidates.
- Create a small service projection record for host ticket choices, colocated with service records.
- Modify `src/main/java/com/snoozeshare/ui/common/messaging/ChatBubbles.java` and its tests for booking-message rendering.
- Create `src/main/java/com/snoozeshare/ui/host/messaging/HostMessagesController.java`, its inbox-row model, and `host-messages.fxml`.
- Create a host ticket dialog controller/FXML, or generalize the existing form behind a role-aware configuration while preserving Guest call sites.
- Modify `HostShellController.java`, `host-shell.fxml`, and `ShellNavigationTest.java`.
- Modify/add host messaging, service, and ticket filing tests; update `PROJECT_STATE.md` and the Done ledger only after verification.

### Task 1: Extend ticket filing for hosts

**Files:**
- Modify: `src/main/java/com/snoozeshare/service/TicketService.java`
- Modify: `src/main/java/com/snoozeshare/service/impl/TicketServiceImpl.java`
- Create/modify: `src/main/java/com/snoozeshare/service/HostTicketBookingOption.java`
- Test: `src/test/java/com/snoozeshare/service/FileTicketTest.java`
- Test: `src/test/java/com/snoozeshare/service/TicketServiceTest.java` if the existing fixture uses that split

**Interfaces:**
- Produces `List<HostTicketBookingOption> hostTicketBookingOptions(UUID hostId)`.
- Keeps `Ticket fileTicket(NewTicketRequest request, UUID raisedByUserId, Role raisedByRole)` unchanged.

- [ ] **Step 1: Write failing service tests** for host filing on an eligible property booking, wrong-host rejection, candidate filtering, per-actor duplicate scope, and unchanged guest filing.
- [ ] **Step 2: Run the focused tests** with `./gradlew test --tests '*FileTicketTest' --tests '*TicketServiceTest'`; expect the new host cases to fail.
- [ ] **Step 3: Implement host authorization** by resolving the booking's property and requiring `property.hostId()` for `Role.HOST`; retain the guest check for `Role.GUEST` and reject unsupported roles clearly.
- [ ] **Step 4: Implement `hostTicketBookingOptions`** behind the service boundary. Return only the host's bookings that satisfy the existing dispute status/window rules and have no ticket already filed by that host; include booking id, property title, dates, and guest display name.
- [ ] **Step 5: Run focused tests** and confirm all old guest cases plus new host cases pass.
- [ ] **Step 6: Commit** `feat: allow hosts to file authorized tickets`.

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
- Controller setup accepts `AppContext`, current Host session, and a callback for opening the ticket dialog.
- Controller cleanup unsubscribes both message event subscriptions.

- [ ] **Step 1: Write failing controller tests** for loading the split layout, empty inbox, selecting a booking row, selecting a ticket row, rendering the correct header/bubbles, sending a trimmed reply, marking read, and disabling the composer for closed threads.
- [ ] **Step 2: Run the focused controller tests** and confirm the page/controller is not yet available.
- [ ] **Step 3: Implement inbox loading** from both `BookingConversationService.conversationsFor` and `MessageService.conversationsFor`, using the projection from Task 2.
- [ ] **Step 4: Implement selection and thread loading** with the exact service calls from the approved spec; call the matching `markRead` after load and show a non-error empty state when there are no messages.
- [ ] **Step 5: Implement send behavior** with blank-body rejection, service call, input clearing, and immediate thread/inbox refresh. Respect each service's read-only state.
- [ ] **Step 6: Subscribe to `BookingMessagePostedEvent` and `MessagePostedEvent`**, refresh the affected row/thread, and expose `cleanup()` for shell navigation.
- [ ] **Step 7: Implement the FXML/CSS layout**: left conversation list, selected peach row, right header, status pills, scrollable bubble area, bottom composer, and top-right `+ New Ticket` button.
- [ ] **Step 8: Run the focused UI tests** and inspect a rendered snapshot at the 1280×800 minimum size.
- [ ] **Step 9: Commit** `feat: implement host messages page`.

### Task 4: Add functional Host ticket filing dialog

**Files:**
- Create/modify: `src/main/java/com/snoozeshare/ui/host/messaging/HostTicketFilingController.java`
- Create/modify: `src/main/resources/com/snoozeshare/ui/host/messaging/host-ticket-filing.fxml`
- Test: `src/test/java/com/snoozeshare/ui/host/HostTicketFilingControllerTest.java`
- Regression test: `src/test/java/com/snoozeshare/ui/guest/GuestTicketFilingTest.java` or the existing guest ticket test

**Interfaces:**
- Configure with `AppContext`, optional preselected booking id, and `Runnable onClose`/`Runnable onFiled` callbacks.
- Calls `TicketService.hostTicketBookingOptions(hostId)` and `fileTicket(request, hostId, Role.HOST)`.

- [ ] **Step 1: Write failing dialog tests** for candidate population, preselection from a booking row, required field validation, successful host filing, error display, and cancel behavior.
- [ ] **Step 2: Run focused dialog and guest regression tests** and confirm the new host flow fails while Guest remains green.
- [ ] **Step 3: Implement the dialog** by reusing the Guest category/remedy semantics and adding the host booking selector; preselect the current booking when available.
- [ ] **Step 4: Wire successful filing** to close, refresh the Host inbox, and select the newly created ticket when its event/refresh makes it available.
- [ ] **Step 5: Run focused tests** and confirm both roles' filing behavior passes.
- [ ] **Step 6: Commit** `feat: add host ticket filing flow`.

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
- [ ] **Step 3: Implement navigation and lifecycle cleanup** for the Messages controller alongside the existing Wallet cleanup; make the New Ticket callback open the host dialog.
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
- [ ] **Step 6: Add one newest-first Done ledger entry** naming the Host Messages page and host ticket filing behavior; keep ledger numbering/order consistent with the existing newest-first format.
- [ ] **Step 7: Commit** `docs: record host messages delivery and verification`.

