# W9 — Host Wallet Management Design

**Date:** 2026-09-27  
**Status:** Draft for operator review  
**Workstream:** W9 / F8 — Host Wallet Management

## Goal

Give hosts a complete wallet page for viewing their available balance, topping up,
withdrawing available funds, and inspecting every wallet transaction in chronological
order. The page will use the supplied wallet mockups and the same reusable implementation
for Guest and Host.

W8 and W10 remain responsible for creating booking payouts, ticket remedies, and agent
overrides. W9 displays those persisted transactions; it does not change their settlement
rules.

## Confirmed scope

W9 covers:

- F8.1.1: display the current host wallet balance.
- F8.1.2: mocked host top-up.
- F8.1.3: display all wallet transaction types, including payouts, platform-fee metadata,
  ticket remedies, agent overrides, escrow rows, top-ups, and withdrawals.
- F8.2.1: mocked withdrawal up to the available wallet balance.
- The same wallet UI and interactions for Guest and Host, implemented in a common package.
- The supplied visual layout: balance card, side-by-side actions, transaction statement,
  modal scrim, modal cards, and role-appropriate green/red action styling.
- Fixed top-up amount controls that populate the amount field when clicked.
- A withdrawal link that populates the amount field with the full current available balance.

Out of scope:

- Payment or payout-rail integrations.
- New wallet or transaction tables.
- New settlement behavior for bookings or disputes.
- Filtering or pagination of the statement.
- A separate host-only wallet business service.

## User experience

### Wallet page

The shared page presents:

1. An `AVAILABLE BALANCE` card with the current SGD balance and short explanatory copy.
2. A right-side action stack with `Top up` and `Withdraw` buttons.
3. A `Transaction statement` table ordered newest first.

Each statement row contains:

- formatted date and time;
- the exact transaction type label (`TOP_UP`, `WITHDRAWAL`, `ESCROW_HOLD`,
  `ESCROW_REFUND`, `BOOKING_PAYOUT`, `TICKET_REMEDY`, or `AGENT_OVERRIDE`);
- a related booking or ticket reference when present, otherwise an em dash;
- signed amount in SGD, with credits styled green and debits styled red;
- `balanceAfter` in SGD;
- fee metadata when a payout carries a non-zero `feeAmount`, without treating the fee as a
  second wallet debit.

The statement is sourced from `WalletService.statementFor(userId)`, which already returns
the wallet's complete append-only transaction history. The UI must not filter by role or
transaction type.

### Top-up modal

The modal follows the supplied design: current balance, amount field, preset amount controls,
an explanatory mocked-payment notice, and Cancel/Confirm actions.

The preset controls are clickable controls with fixed values `$50`, `$100`, `$500`, and
`$1000`. Clicking one replaces the amount field contents with that value. The user may still
edit the field before confirming. Confirmation calls `WalletService.topUp` after the existing
positive-amount validation.

### Withdrawal modal

The modal shows the current available balance and the notice that escrow-held or otherwise
unavailable funds cannot be withdrawn. The `Withdraw full available balance` text is a
clickable control; clicking it replaces the amount field contents with the current balance.
The user may edit the amount before confirming. Confirmation calls `WalletService.withdraw`,
whose atomic ledger write rejects an amount greater than the available balance.

After either operation succeeds, the modal closes and the page refreshes. The existing
`WalletTransactionRecordedEvent` subscription remains the cross-flow refresh mechanism so
booking and dispute transactions also update the visible balance and statement.

## Architecture and component boundaries

The wallet UI currently lives under `ui.guest.wallet` even though the Host page already
loads the same controller. W9 moves the reusable dashboard and action-dialog controllers and
their FXML into `ui.common.wallet`. Guest and Host navigation continue to choose their own
page resources, but both resources use the common controller and common action-dialog flow.

The common controller depends on `AppContext`, `SessionContext`, `WalletService`, and the
wallet transaction event. It does not depend on repositories, JDBC, booking services, or
role-specific controllers. `WalletService` and `WalletLedgerWriter` remain unchanged unless
implementation tests expose a defect in their existing validation or event behavior.

The common formatter maps enum values and related IDs to display text without changing the
underlying records. Related booking and ticket IDs are shown in a compact stable reference;
the raw UUID remains available to the controller for deterministic formatting.

## Validation and failure behavior

- Empty, non-numeric, zero, and negative amounts show an inline modal error.
- Insufficient withdrawal funds show the service error and leave the modal open.
- Failed writes do not close the modal or alter the displayed statement.
- Successful writes refresh balance and statement from the service rather than incrementing
  values locally.
- An empty statement displays the existing empty-state message.
- Events received off the JavaFX thread refresh through `Platform.runLater`.

## Verification

Service tests will cover host users for positive top-up and withdrawal, overdraft rejection,
and complete statement ordering. UI tests will cover:

- common wallet page loading for Guest and Host;
- rendering every transaction type and signed amount;
- booking/ticket/agent related references and payout fee display;
- top-up preset controls populating the input;
- withdrawal full-balance control populating the input;
- modal validation and successful refresh;
- event-driven refresh and cleanup on navigation.

FXML parsing, Checkstyle, focused wallet tests, and the full Gradle test/build suite are
required before W9 is marked complete.

## Decisions captured

- The wallet page is a common Guest/Host UI, not two divergent role implementations.
- The host statement includes all wallet transaction types.
- Existing wallet and settlement service boundaries are preserved.
