# **SnoozeShare Product Backlog & Engineering Specification**

This document serves as the formal Product Backlog and Software Engineering Specification for **SnoozeShare**, a production-level Java 25 desktop application designed for short-term property rentals. 

The project is structured across three distinct user roles: **Guest (Renter)**, **Host (Homeowner)**, and **Support Agent (Platform Admin)**. Each feature is prioritized, decomposed into hierarchical levels (Epic → Sub-Feature → PR-Level Executable Requirement), and mapped to planned sprint cycles to support parallel team development using Agentic Software Engineering practices.

## **1\. Executive Summary & Architectural Overview**

The SnoozeShare application follows strict Single Responsibility Principle (SRP) and Don't Repeat Yourself (DRY) software engineering standards. The system decouples the JavaFX user interface per role while leveraging a shared Java 25 domain model, persistence layer, and transaction state machine.

| Iteration | Primary Objective | Key Deliverables |
| :---- | :---- | :---- |
| **Iteration 1** | Core Domain Model, Shared Ledger & Base UI Infrastructure | Java 25 domain records, database schema migrations, basic Guest/Host listing CRUD, search filters, state machines. |
| **Iteration 2** | Booking Workflows, Availability Controls & Core Role Interfaces | Booking request lifecycle (Pending → Confirmed), calendar date blocking, Guest Trip Hub, basic Dispute queue. |
| **Iteration 3** | Dispute Settlement Engine, Governance, Audit Logs & Refinement | Support administrative overrides, account suspensions, financial refunds/payouts, audit logging, reviews. |

## **2\. Guest (Renter) Feature Backlog**

The Guest backlog focuses on listing discovery, booking execution, trip management, and post-stay dispute resolution.

| L1 | L2 | L3 ID | Requirement Specification (PR-Level Scope) | Priority | Sprint |
| ----- | ----- | ----- | :---- | ----- | ----- |
| **F1** | **Listing Search & Property Discovery Engine** |  |  | **High** | **1** |
|  | **F1.1** | **Multi-Criteria Search & Filter Implementation** |  |  |  |
|  |  | F1.1.1 | The system shall filter available properties using start and end date pickers against existing booking block intervals. | High | 1 |
|  |  | F1.1.2 | The system shall filter property listings by city/location substring and minimum guest capacity. | High | 1 |
|  |  | F1.1.3 | The system shall support max nightly budget filtering via a JavaFX range slider with real-time UI table updates. | Medium | 2 |
|  | **F1.2** | **Property Detail & Cost Estimation View** |  |  |  |
|  |  | F1.2.1 | The system shall render complete listing specs, house rules, pricing, and host profile summary in a dedicated modal view. | High | 1 |
|  |  | F1.2.2 | The system shall calculate and display total price breakdown (Nightly Rate × Stay Days \+ 10% Service Fee) dynamically. | High | 1 |
| **F2** | **Booking Execution & Trip Hub Workflow** |  |  | **High** | **1** |
|  | **F2.1** | **Reservation Submission & Ledger Reservation** |  |  |  |
|  |  | F2.1.1 | The system shall allow guests to submit booking requests and block requested dates from overlapping reservations. | High | 1 |
|  |  | F2.1.2 | The system shall place total booking funds into internal escrow ledger state upon request creation. | High | 2 |
|  | **F2.2** | **Trip Management Dashboard** |  |  |  |
|  |  | F2.2.1 | The system shall provide a guest dashboard categorizing bookings into Upcoming, Active, Completed, and Cancelled tabs. | High | 2 |
|  |  | F2.2.2 | The system shall dynamically reflect real-time host acceptance or rejection updates on the guest trip view. | Medium | 2 |
|  | **F2.3** | **Guest Cancellation & Refund Logic** |  |  |  |
|  |  | F2.3.1 | The system shall allow guests to cancel pending or confirmed reservations prior to check-in. | Medium | 2 |
|  |  | F2.3.2 | The system shall automatically compute policy refunds (\>48h lead time \= 100% refund, \<48h lead time \= 50% refund). | Medium | 3 |
| **F3** | **Guest Feedback, Disputes & Profile Controls** |  |  | **Medium** | **3** |
|  | **F3.1** | **Reviews & Incident Ticketing** |  |  |  |
|  |  | F3.1.1 | The system shall allow guests to log a formal dispute ticket with categorical selection (e.g., Cleanliness, No-Show). | Medium | 3 |
|  |  | F3.1.2 | The system shall enable guests to submit a 1-5 star rating and review comment for completed stays. | Low | 3 |

## **3\. Host (Homeowner) Feature Backlog**

The Host backlog covers property publishing, calendar date blocking, booking request management, earnings calculation, and dispute responses.

| L1 | L2 | L3 ID | Requirement Specification (PR-Level Scope) | Priority | Sprint |
| ----- | ----- | ----- | :---- | ----- | ----- |
| **F4** | **Listing Management & Publishing Engine** |  |  | **High** | **1** |
|  | **F4.1** | **Property Onboarding & Validation** |  |  |  |
|  |  | F4.1.1 | The system shall allow hosts to create properties with title, description, address, guest capacity, and base rate. | High | 1 |
|  |  | F4.1.2 | The system must validate listing inputs (non-negative pricing, positive capacity \> 0\) prior to storage. | High | 1 |
|  | **F4.2** | **Listing Status & Pricing Control** |  |  |  |
|  |  | F4.2.1 | The system shall allow hosts to toggle listing availability status between Active and Inactive. | Medium | 2 |
| **F5** | **Calendar Management & Date Overrides** |  |  | **High** | **1** |
|  | **F5.1** | **Manual Availability Blackouts** |  |  |  |
|  |  | F5.1.1 | The system shall allow hosts to select date ranges and set them as blocked for private maintenance or use. | High | 1 |
|  |  | F5.1.2 | The system shall reject host manual date block requests if a confirmed guest booking already exists for those dates. | Medium | 2 |
| **F6** | **Host Reservation Queue, Earnings & Disputes** |  |  | **High** | **1** |
|  | **F6.1** | **Request Decision Queue** |  |  |  |
|  |  | F6.1.1 | The system shall display pending guest booking requests with guest profile ratings, requested dates, and net earnings. | High | 1 |
|  |  | F6.1.2 | The system shall allow hosts to approve or decline requests, transitioning state to Confirmed or Rejected. | High | 1 |
|  | **F6.2** | **Host Financial Ledger & Dispute Response** |  |  |  |
|  |  | F6.2.1 | The system shall compute net host earnings (Gross minus 3% host platform fee) upon trip completion. | Medium | 2 |
|  |  | F6.2.2 | The system shall allow hosts to submit formal response notes and evidence text against open guest dispute tickets. | Low | 3 |

## **4\. Support Agent (Admin) Feature Backlog**

The Support Agent backlog manages dispute triage, manual state overrides, account suspensions, financial refunds/payouts, and system audit logs.

| L1 | L2 | L3 ID | Requirement Specification (PR-Level Scope) | Priority | Sprint |
| ----- | ----- | ----- | :---- | ----- | ----- |
| **F7** | **Dispute Resolution & State Override Engine** |  |  |  |  |
|  | **F7.1** | **Centralized Dispute Triage Queue** |  |  |  |
|  |  | F7.1.1 | The system shall show an active dispute ticket queue sorted chronologically with guest and host evidence presented. | High | 1 |
|  |  | F7.1.2 | The system shall allow support agents to accept ticket requests and record internal administrative notes. | Medium | 1 |
|  |  | F7.1.2 | The system shall allow support agents to record internal administrative notes within tickets | Medium | 2 |
|  | **F7.2** | **Manual Ledger Settlement & State Overrides** |  |  |  |
|  |  | F7.2.1 | The system shall permit agents to force-change booking state (e.g., Force Cancel, Force Complete) to resolve deadlocks. | Medium | 2 |
|  |  | F7.2.2 | The system shall permit agents to override ledger payouts manually for:\- Full Refund to Guest \- Full Payout to Host, or  \- Customised Partial Refund/Payout | High | 2 |
| **F8** | **Account Governance** |  |  |  |  |
|  | **F8.1** | **User Suspension Controls** |  |  |  |
|  |  | F8.1.1 | The system shall enable agents to suspend user accounts (Guest or Host), revoking active login session permissions. | Medium | 2 |
|  |  | F8.1.2 | The system shall automatically cancel all pending booking requests/listings belonging to a newly suspended user. | Medium | 3 |
| **F9** | **Platform Audit Trail & Analytics** |  |  |  |  |
|  | **F9.1** | **System Audit Trail Inspector** |  |  |  |
|  |  | F9.1.1 | The system shall log every state transition and financial ledger change by support agents into an audit database table. | High | 1 |
|  |  | F9.1.2 | The system shall allow support agents to filter audit logs by User ID, Booking ID, and Action Type. | Medium | 3 |

## **5\. Agentic Software Engineering (SE) Workflow Guidelines**

To meet the course requirements for Agentic SE integration in Java 25, team members will structure AI sub-agent prompts using the following standardized pipeline:

> 1. **Domain Entity Generation (Java 25 Records):** Provide AI agents with strict record definitions (e.g., record Booking(UUID id, UUID guestId, UUID listingId, LocalDate start, LocalDate end, BookingStatus status)) to ensure immutable domain data.  
> 2. **Automated Unit Test Generation (JUnit 5):** Task the AI agent with generating boundary tests for state transitions before writing service implementation code (TDD approach).  
> 3. **UI Component Generation (JavaFX):** Direct the AI agent to build role-isolated FXML controllers adhering strictly to Single Responsibility Principle.  
> 4. **CI/CD Integration:** Use automated agent scripts to construct GitHub Actions pipelines for running headless TestFX UI tests and Maven unit builds on every PR merge.