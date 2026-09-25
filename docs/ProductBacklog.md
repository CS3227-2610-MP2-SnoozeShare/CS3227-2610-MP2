# SnoozeShare Product Backlog & Engineering Specification

This document serves as the formal Product Backlog and Software Engineering Specification for **SnoozeShare**, a production-level Java 25 desktop application designed for short-term property rentals. 

The project is structured across three distinct user roles: **Guest (Renter)**, **Host (Homeowner)**, and **Support Agent (Platform Admin)**. Each feature is prioritized, decomposed into hierarchical levels (Epic → Sub-Feature → PR-Level Executable Requirement), and mapped to planned sprint cycles to support parallel team development using Agentic Software Engineering practices.

> **Revision note:** This version restructures the original backlog against `SnoozeShare-Architecture-Proposal.md` — see the [Changelog](#changelog--revision-notes) at the bottom for a full list of what changed and why.

## 1. Executive Summary & Architectural Overview

The SnoozeShare application follows strict Single Responsibility Principle (SRP) and Don't Repeat Yourself (DRY) software engineering standards. The system decouples the JavaFX user interface per role while leveraging a shared Java 25 domain model, a wallet/transaction-based financial ledger, mocked authentication, and a shared booking/ticket state machine.

| Iteration | Primary Objective | Key Deliverables |
| :---- | :---- | :---- |
| **Iteration 1** | Platform Foundations, Core Domain Model & Base UI Infrastructure | Mocked auth & role-based routing, wallet provisioning, Java 25 domain records, database schema migrations, in-process event bus, audit log infra, Guest/Host listing CRUD, search filters, booking request creation + escrow hold, state machines. |
| **Iteration 2** | Booking Lifecycle, Dispute Workflow & Financial Settlement | Host approve/reject, trip completion & payout settlement, calendar date blocking, Guest Trip Hub, guest/host ticket filing, agent triage queue, wallet withdrawal, manual agent overrides, audit hooks wired into every mutating service. |
| **Iteration 3** | Governance, Reviews & Reporting Refinement | Account suspension cascade, guest reviews, audit log filtering/reporting, refinement. |

## 2. Platform Foundations (Cross-Role) Backlog

Everything in this section is a prerequisite for every role-specific feature below: nothing else can be meaningfully built or demoed without mocked auth and a wallet to hold money in.

| L1 | L2 | L3 ID | Requirement Specification (PR-Level Scope) | Priority | Sprint |
| ----- | ----- | ----- | :---- | ----- | ----- |
| **F0** | **Authentication, Registration & Wallet Provisioning** |  |  | **High** | **1** |
|  | **F0.1** | **Mocked Registration & Role Assignment** |  |  |  |
|  |  | F0.1.1 | The system shall allow a new user to register as Guest, Host, or Agent; Host and Agent registration shall require a valid registration code before the account is created. | High | 1 |
|  |  | F0.1.2 | The system shall provide a mocked login that sets the active session's user and role (`SessionContext`) and routes to the corresponding role shell (Guest/Host/Admin), with no real credential verification. | High | 1 |
|  | **F0.2** | **Wallet Provisioning** |  |  |  |
|  |  | F0.2.1 | The system shall automatically create a zero-balance Wallet for a user upon successful Guest or Host registration. | High | 1 |
| **F12** | **Messaging** |  |  | **Medium** | **2** |
|  | **F12.1** | **Ticket Conversations** |  |  |  |
|  |  | F12.1.1 | The system shall provide a `MessageService` that lets a dispute ticket's guest or host and the assigned Support Agent exchange chat messages in a per-party thread (guest↔agent, host↔agent) attached to the ticket; the agent chat runs through the same service as any other participant. | Medium | 2 |
|  |  | F12.1.2 | The system shall persist messages and let each party read only their own thread, with the agent able to read both. | Medium | 2 |

## 3. Guest (Renter) Feature Backlog

The Guest backlog focuses on listing discovery, wallet funding, booking execution, trip management, and post-stay dispute resolution.

| L1 | L2 | L3 ID | Requirement Specification (PR-Level Scope) | Priority | Sprint |
| ----- | ----- | ----- | :---- | ----- | ----- |
| **F1** | **Listing Search & Property Discovery Engine** |  |  | **High** | **1** |
|  | **F1.1** | **Multi-Criteria Search & Filter Implementation** |  |  |  |
|  |  | F1.1.1 | The system shall filter available properties using start and end date pickers against existing availability blocks (host blackouts and confirmed bookings). | High | 1 |
|  |  | F1.1.2 | The system shall filter property listings by city/location substring and minimum guest capacity. | High | 1 |
|  |  | F1.1.3 | The system shall support max nightly budget filtering with real-time UI table updates. | Medium | 2 |
|  | **F1.2** | **Property Detail & Cost Estimation View** |  |  |  |
|  |  | F1.2.1 | The system shall render complete listing specs, house rules, pricing, and host profile summary in a dedicated view. | High | 1 |
|  |  | F1.2.2 | The system shall calculate and display total price breakdown (Nightly Rate × Stay Days) dynamically. | High | 1 |
| **F2** | **Booking Execution & Trip Hub Workflow** |  |  | **High** | **1** |
|  | **F2.1** | **Reservation Submission & Escrow Transaction** |  |  |  |
|  |  | F2.1.1 | The system shall allow guests to submit a booking request for a date range, creating a Booking in PENDING status. | High | 1 |
|  |  | F2.1.2 | The system shall block the requested dates from overlapping reservations by creating a BOOKING-sourced availability block for the requested range. | High | 1 |
|  |  | F2.1.3 | The system shall place the total booking amount on hold against the guest's wallet balance via an ESCROW_HOLD wallet transaction, atomically with booking creation. | High | 1 |
|  | **F2.2** | **Trip Management Dashboard** |  |  |  |
|  |  | F2.2.1 | The system shall provide a guest dashboard categorizing bookings into Upcoming, Active, Completed, and Cancelled tabs. | High | 2 |
|  |  | F2.2.2 | The system shall subscribe to booking-decision events on the event bus and dynamically reflect host acceptance or rejection on the guest trip view without a manual refresh. | Medium | 2 |
|  | **F2.3** | **Guest Cancellation & Refund Logic** |  |  |  |
|  |  | F2.3.1 | The system shall allow guests to cancel pending or confirmed reservations prior to check-in. | Medium | 2 |
|  |  | F2.3.2 | The system shall automatically compute the policy refund (>48h lead time = 100%, <48h lead time = 50%) and credit the guest's wallet via an ESCROW_REFUND wallet transaction as part of the same cancellation action. | Medium | 2 |
| **F3** | **Guest Feedback, Disputes & Profile Controls** |  |  | **Medium** | **2** |
|  | **F3.1** | **Incident Ticketing & Reviews** |  |  |  |
|  |  | F3.1.1 | The system shall allow a guest to file a dispute ticket against a booking (up to 7 days after stay end) with category selection (from an agent-managed category list), a title, and a description. | Medium | 2 |
|  |  | F3.1.2 | The system shall allow the guest to specify a requested remedy (Full Refund, Partial Refund, Other) and attach supporting text evidence to the ticket. | Medium | 2 |
|  |  | F3.1.3 | The system shall enable guests to submit a 1-5 star rating and review comment for stays with status COMPLETED. | Low | 3 |
| **F4** | **Guest Wallet Management** |  |  | **High** | **1** |
|  | **F4.1** | **Wallet Top-Up & Balance View** |  |  |  |
|  |  | F4.1.1 | The system shall allow a guest to top up their wallet balance by entering an amount (mocked — no real payment gateway). | High | 1 |
|  |  | F4.1.2 | The system shall allow a guest to view their current wallet balance and a chronological transaction statement (top-ups, escrow holds, refunds). | High | 1 |
|  | **F4.2** | **Wallet Withdrawal** |  |  |  |
|  |  | F4.2.1 | The system shall allow a guest to withdraw funds up to their available (non-escrowed) wallet balance (mocked — no real payout rail). | Medium | 2 |

## 4. Host (Homeowner) Feature Backlog

The Host backlog covers property publishing, calendar date blocking, booking request management, wallet earnings, and dispute responses.

| L1 | L2 | L3 ID | Requirement Specification (PR-Level Scope) | Priority | Sprint |
| ----- | ----- | ----- | :---- | ----- | ----- |
| **F5** | **Listing Management & Publishing Engine** |  |  | **High** | **1** |
|  | **F5.1** | **Property Onboarding & Validation** |  |  |  |
|  |  | F5.1.1 | The system shall allow hosts to create properties with title, description, address, guest capacity, and base rate. | High | 1 |
|  |  | F5.1.2 | The system must validate listing inputs (non-negative pricing, positive capacity > 0) prior to storage. | High | 1 |
|  | **F5.2** | **Listing Status & Pricing Control** |  |  |  |
|  |  | F5.2.1 | The system shall allow hosts to toggle listing availability status between Active and Inactive. | Medium | 2 |
| **F6** | **Calendar Management & Date Overrides** |  |  | **High** | **1** |
|  | **F6.1** | **Manual Availability Blackouts** |  |  |  |
|  |  | F6.1.1 | The system shall allow hosts to select date ranges and set them as blocked for private maintenance or use. | High | 1 |
|  |  | F6.1.2 | The system shall reject a host manual date-block request if a confirmed guest booking already exists for any date in that range. | Medium | 2 |
| **F7** | **Host Reservation Queue, Earnings & Disputes** |  |  | **High** | **1** |
|  | **F77.1** | **Request Decision Queue** |  |  |  |
|  |  | F7.1.1 | The system shall display pending guest booking requests with guest identity, requested dates, and stay length. | High | 1 |
|  |  | F7.1.2 | The system shall allow hosts to approve or decline requests, transitioning state to Confirmed or Rejected. | High | 1 |
|  |  | F7.1.3 | The system shall display a projected net earnings figure (gross minus 3% platform fee) alongside each pending request. | Medium | 2 |
|  |  | F7.1.4 | The system shall display the requesting guest's review rating alongside each pending request, or "No ratings yet" if none exist. | Low | 3 |
|  | **F7.2** | **Host Wallet Earnings & Dispute Response** |  |  |  |
|  |  | F7.2.1 | The system shall compute net host earnings (gross minus 3% host platform fee) and credit the host's wallet via a BOOKING_PAYOUT wallet transaction when a booking reaches COMPLETED. | Medium | 2 |
|  |  | F7.2.2 | The system shall allow hosts to submit formal response notes and evidence text against open guest dispute tickets. | Medium | 2 |
|  | **F7.3** | **Trip Completion & Settlement Trigger** |  |  |  |
|  |  | F7.3.1 | The system shall automatically transition a CONFIRMED booking to COMPLETED once its dispute window has passed (checkout date + 7 days), triggering wallet settlement per F6.2.1. A booking with an open (unresolved) dispute ticket shall not be auto-completed; its escrow stays held until an agent resolves the ticket (F9.2.2). | High | 2 |
| **F8** | **Host Wallet Management** |  |  | **High** | **1** |
|  | **F8.1** | **Wallet Balance & Payout History** |  |  |  |
|  |  | F8.1.1 | The system shall allow a host to view their current wallet balance. | High | 1 |
|  |  | F8.1.2 | The system shall allow a host to top up their wallet balance by entering an amount (mocked — no real payment gateway). | High | 1 |
|  |  | F8.1.3 | The system shall show a chronological transaction statement (payouts, platform fees, ticket remedies) | Low | 3
|  | **F8.2** | **Wallet Withdrawal** |  |  |  |
|  |  | F8.2.1 | The system shall allow a host to withdraw funds up to their available wallet balance to cash out earnings (mocked — no real payout rail). | High | 1 |

## 5. Support Agent (Admin) Feature Backlog

The Support Agent backlog manages dispute triage, manual state overrides, account suspensions, wallet transaction overrides, and system audit logs.

| L1 | L2 | L3 ID | Requirement Specification (PR-Level Scope) | Priority | Sprint |
| ----- | ----- | ----- | :---- | ----- | ----- |
| **F9** | **Dispute Resolution & State Override Engine** |  |  | **High** | **2** |
|  | **F9.1** | **Centralized Dispute Triage Queue** |  |  |  |
|  |  | F9.1.1 | The system shall show an active dispute ticket queue sorted chronologically (oldest first) with guest and host evidence and their chat threads (F12.1.1) presented. | High | 2 |
|  |  | F9.1.2 | The system shall allow support agents to accept ticket requests and record internal administrative notes within tickets. | Medium | 2 |
|  | **F9.2** | **Manual Wallet Settlement & State Overrides** |  |  |  |
|  |  | F9.2.1 | ~~The system shall permit agents to force-change booking state (e.g., Force Cancel, Force Complete) to resolve deadlocks.~~ **Dropped 2026-09-25 (PROJECT_STATE C22):** redundant — accepting or rejecting the dispute ticket (F9.2.2) already closes it and settles the booking. | ~~Medium~~ | Dropped |
|  |  | F9.2.2 | The system shall permit agents to resolve a dispute by settling the booking's full held escrow: accept the ticket's requested remedy, reject the ticket (host paid in full), or apply a manual adjustment — Full Refund to Guest, Full Payout to Host, or a Customised Partial Refund/Payout. Any amount reaching the host is net of the 3% platform fee; guest refunds carry no fee. Resolution completes the booking (CONFIRMED → COMPLETED). | High | 2 |
|  | **F9.3** | **Ticket Category Administration** |  |  |  |
|  |  | F9.3.1 | The system shall allow agents to create, edit, and deactivate the ticket categories offered to guests when filing a dispute (F3.1.1). | Low | 2 |
| **F10** | **Account Governance** |  |  | **Medium** | **2** |
|  | **F10.1** | **User Suspension Controls** |  |  |  |
|  |  | F10.1.1 | The system shall enable agents to suspend user accounts (Guest or Host), revoking app-use permissions. | Medium | 2 |
|  |  | F10.1.2 | The system shall automatically cancel all pending booking requests/listings belonging to a newly suspended user. | Medium | 3 |
| **F11** | **Platform Audit Trail & Analytics** |  |  | **High** | **1** |
|  | **F11.1** | **System Audit Trail Inspector** |  |  |  |
|  |  | F11.1.1 | The system shall provide an audit logging service and database table, and log every booking state transition into it. | High | 1 |
|  |  | F11.1.2 | The system shall extend audit logging to wallet transactions (escrow, payout, refund, override) and ticket resolutions as those features land. | Medium | 2 |
|  |  | F11.1.3 | The system shall allow support agents to filter audit logs by User ID, Booking ID, and Action Type. | Medium | 3 |

## 6. Agentic Software Engineering (SE) Workflow Guidelines

To meet the course requirements for Agentic SE integration in Java 25, team members will structure AI sub-agent prompts using the following standardized pipeline:

> 1. **Domain Entity Generation (Java 25 Records):** Provide AI agents with strict record definitions (e.g., `record Booking(UUID id, UUID guestId, UUID listingId, LocalDate start, LocalDate end, BookingStatus status)`) to ensure immutable domain data.
> 2. **Automated Unit Test Generation (JUnit 5):** Task the AI agent with generating boundary tests for state transitions before writing service implementation code (TDD approach).
> 3. **UI Component Generation (JavaFX):** Direct the AI agent to build role-isolated FXML controllers adhering strictly to Single Responsibility Principle.
> 4. **CI/CD Integration:** Use automated agent scripts to construct GitHub Actions pipelines for running headless TestFX UI tests and Maven unit builds on every PR merge.

## Changelog & Revision Notes

**2026-09-25 (W10 design, see PROJECT_STATE C16–C23)**
- **F9.2.1** (agent Force Cancel / Force Complete) dropped as redundant with ticket accept/reject.
- **F9.2.2** reworded: every resolution settles the full held escrow; host share net of 3%, guest refunds fee-free.
- **F9.1.1** now presents chat threads; queue is oldest-first.
- **F7.3.1** gains the guard that bookings with an open ticket are not auto-completed.
- **F12 Messaging** added (F12.1.1, F12.1.2) — the design canvas shows guest/host chat but no epic owned it (workstream W13).

This restructure was driven by a gap review against `SnoozeShare-Architecture-Proposal.md`. Summary of changes from the original backlog:

**New epics added**
- **F0** Authentication, Registration & Wallet Provisioning — previously unrepresented despite every other epic depending on it.
- **F10** Guest Wallet Management and **F11** Host Wallet Management — top-up/withdraw/balance/statement were architecturally required but had no backlog items.
- **F6.3** Trip Completion & Settlement Trigger — nothing previously defined the normal CONFIRMED→COMPLETED path; only the agent's force-complete override existed.
- **F7.3** Ticket Category Administration — the "predetermined but user-customizable" category requirement had no agent-facing CRUD item.

**Sequencing fixes**
- Escrow hold (F2.1.3, formerly F2.1.2) moved from Sprint 2 to Sprint 1 to match the architecture, where it fires atomically inside booking submission — a booking can no longer exist for a full sprint with no funds held.
- Cancellation refund (F2.3.2) moved from Sprint 3 to Sprint 2 to match its cancellation action (F2.3.1, Sprint 2) — guests could previously cancel with no refund logic for a sprint.
- Ticket filing (F3.1.1/F3.1.2) and host ticket response (F6.2.2) moved from Sprint 3 to Sprint 2 so the Agent's dispute triage queue (F7.1.1, also moved to Sprint 2) has real data to operate on — previously the queue was scheduled a full sprint before any ticket could exist.
- Host request-queue earnings preview and guest rating display were split out of F6.1.1 into their own items (F6.1.3 net earnings, F6.1.4 rating) and pushed to the sprint where their data dependency (fee calc / reviews) actually exists, instead of being silently bundled into a Sprint 1 item that couldn't render them yet.
- F9.1.1 (audit trail) split into infra (Sprint 1: table + service, hook into booking transitions) and extension (F9.1.2, Sprint 2: hook into wallet transactions and ticket resolutions as those land), instead of claiming full coverage in Sprint 1.

**Data/formatting fixes**
- Removed the duplicated F7.1.2 row (previously listed twice, once per Sprint 1 and Sprint 2, with near-identical text).
- Filled in previously blank L1-level Priority/Sprint cells for F7, F8, F9.

**Terminology alignment**
- Renamed "Ledger" language throughout to Wallet/Transaction terms consistent with the architecture doc: F2.1 ("...& Ledger Reservation" → "...& Escrow Transaction"), F6.2 ("Host Financial Ledger..." → "Host Wallet Earnings..."), F7.2 ("Manual Ledger Settlement..." → "Manual Wallet Settlement..."), and their requirement text (`ESCROW_HOLD`/`ESCROW_REFUND`/`BOOKING_PAYOUT` wallet transactions instead of "ledger state").

**Granularity fixes**
- F2.1.1 split into three PR-level items (submit request / block overlapping dates / escrow hold) since they map to two different services (`BookingService`, `AvailabilityService`) collaborating with `TransactionService`.
- F3.1.1 split into two items (category+title+description / remedy+supporting text) so each maps to an independently testable acceptance criterion.
