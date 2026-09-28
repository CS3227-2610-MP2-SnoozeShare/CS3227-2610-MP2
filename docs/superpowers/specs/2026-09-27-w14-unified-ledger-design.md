# W14 — Unified Ledger (design)

**Status:** Draft for operator review · **Branch:** `unified-ledger` (from the `messaging-service` tip, which carries W11, W12 and W13) · **Date:** 2026-09-27
**Origin:** C30 / C31 (operator, 2026-09-26): fold `wallet_transactions` into `audit_log` and give the platform a System account that really holds money. **Revised 2026-09-27 (C40):** the `wallets` table stays (no `users.balance`), and the System user gets a real `SYSTEM` role.

## 1. Goal and honest size

Today every money movement is written twice: once to `wallet_transactions` (the ledger) and once to `audit_log` (W12's money row). Five code paths hand-copy the same "read wallet, add, save, insert ledger row" block. W14 leaves **one** record of money: the `audit_log` money row. `wallets` stays as the balance holder. The 3% platform fee gets a real home, the System user's wallet.

**Medium change, smaller than first drafted.** The UI barely moves (§ 6). Keeping `wallets` removes the wallet-identity churn: `Wallet`, `WalletRepository`, `JdbcWalletRepository`, `WalletProvisioningService`, `getWallet` and the event payload all stay. What remains: two migrations, a seed rewrite, about 15 main files and 13 test files. The risk is in the money paths from W1, W3, W5, W8, W10 and W11, so the plan must be TDD slices with the conservation invariant (§ 9) green after each.

## 2. Target design

**One rule: a money row is an `audit_log` row whose `walletAdjustment` is not null.** W12 already writes exactly these; a row is never both a status change and a money change.

| Concept | Before | After |
|---|---|---|
| Balance | `wallets.balance` | **unchanged**: `wallets.balance`, kept as its own table, so a balance never travels on the `User` record the UI and session pass around (operator, C40) |
| Ledger | `wallet_transactions` table | money rows in `audit_log`, with a new `balanceAfter REAL` (money rows only) |
| Platform fee | informational `feeAmount` on the payout row | a `PLATFORM_FEE` money row credited to the System user's wallet |
| System user | role `AGENT`, status `SUSPENDED`, no wallet | role **`SYSTEM`**, status `ACTIVE`, one real wallet |
| Payout row `amount` | net (W10) or gross minus fee (`WalletLedgerWriter`): inconsistent | always the amount that actually moved; the fee is its own row |
| Writers | 5 inline copies + `WalletLedgerWriter` (opens its own transaction) | one `LedgerWriter` that **never opens a transaction**; callers own it |

`wallets.balance` stays a denormalized cache: it changes only in the same DB transaction that inserts the money row, and always equals the newest money row's `balanceAfter` for that wallet's owner. The Conventions rule is reworded from "sum of wallet_transactions" to "sum of the owner's money rows in `audit_log`".

**Fee flow.** When a host is paid (`settleBookingCompletion`, and the host side of a dispute settlement), `LedgerWriter` writes two rows in the caller's transaction: `BOOKING_PAYOUT` to the host (net) and `PLATFORM_FEE` to the System wallet (the fee; same actor, booking and ticket ids; reason `3% platform fee on payout`). A zero fee writes no row. A full refund to the guest carries no fee (C18/C20 unchanged). Escrow stays implicit (guest debited at `ESCROW_HOLD`, nothing credited until release); no escrow account (§ 10, item 3).

**System user (role `SYSTEM`).** The seeded user `AuditService.SYSTEM_ACTOR_ID` keeps its id and becomes role `SYSTEM`. Consequences, all small:

- `Role` gains `SYSTEM`. The two exhaustive switches (`SceneRouter`, `AccountText`) get a case; `AuthController` already lists roles explicitly, so it is never selectable. `UserServiceImpl` registration and login must reject `SYSTEM` (add a test).
- The Accounts list hides `role == SYSTEM` (replaces the id check), and `AccountGovernanceServiceImpl` refuses to suspend it (replaces the id check at line 232).
- `WalletProvisioningService` skips `AGENT` today; `SYSTEM` gets its wallet from the migration and the seed, not from provisioning.
- Status `ACTIVE`: "suspended" means nothing for a non-loginable account, and the wallet code does not check status.
- `messages.authorRole` and `booking_messages` keep `('GUEST','HOST','AGENT')`; the System user never chats.

## 3. Schema and migrations

Two migrations, so the risky table rebuild is isolated.

**`V006__system_role.sql`: rebuild `users` with the new role.** SQLite cannot alter a CHECK, so use the documented rebuild: create `users_new` with `role IN ('GUEST','HOST','AGENT','SYSTEM')` and every current column (including V003's `suspensionReason`), copy all rows, set the System row to `SYSTEM` / `ACTIVE`, drop `users`, rename `users_new` to `users`, recreate any index on `users`. Thirteen tables reference `users`, and `ConnectionFactory` turns `PRAGMA foreign_keys = ON`; that pragma cannot change inside a transaction, so `MigrationRunner` must run this migration with foreign keys off (outside its transaction) and finish with `PRAGMA foreign_key_check`, failing the migration on any row. Plan Task 1 confirms how `MigrationRunner` executes scripts and adds that hook (with a test that a child row still resolves after the rebuild). `db/schema.sql` mirrors the new CHECK.

**`V007__unified_ledger.sql`**, in this order:

1. `ALTER TABLE audit_log ADD COLUMN balanceAfter REAL`.
2. For every `wallet_transactions` row that already has a money row in `audit_log` (`entityId = transactionId`), copy `balanceAfter` across. For every row with none (pre-W12 data, adopted databases), insert the missing audit row (actor and subject name snapshots from `users`; subject = the wallet's owner).
3. Insert the System wallet (zero balance, SGD) if missing. For every legacy `BOOKING_PAYOUT` with `feeAmount > 0`, insert a `PLATFORM_FEE` row for the System user (timestamp = the payout's) and add the fee to the System wallet's balance (§ 10, item 4).
4. `DROP TABLE wallet_transactions`. `wallets` is untouched.
5. Index `audit_log(subjectUserId, walletAdjustment)` for the statement query.

`db/schema.sql` mirrors the result; `SchemaParityTest` stays green; `MigrationRunner` applies both to fresh, migrated and adopted databases (as for V003 to V005).

**Deviation from this draft (executed, Tasks 4 and 10):** step 4 above (`DROP TABLE wallet_transactions`) was pulled out into its own `V008__drop_wallet_transactions.sql` instead of running inside V007. This let every caller (`WalletService`, `TransactionService`, `BookingService`, dispute settlement, account governance) move onto `LedgerWriter`/`LedgerRepository` across Tasks 6-9 while the old table still existed to be read as a safety net, then drop it once nothing referenced it (Task 10). V007 now ends after step 3 (System wallet + legacy fee backfill) plus the index in step 5; V008 is exactly the old step 4. Recorded as C41 in `PROJECT_STATE.md` and in the Done ledger.

**Mock DB.** `db/seed-mock-data.sql` today derives its audit money rows *from* `wallet_transactions`. That inverts: the 35 money rows, plus a `PLATFORM_FEE` row for each of transactions 29 and 35 (16.35 in total for the System wallet), are written straight into `audit_log` with `balanceAfter`; `wallets` keeps its rows and gains the System wallet; the System user row becomes `SYSTEM` / `ACTIVE`. Rebuild `db/snoozeshare-mock.db` from schema + seed with `schema_history` v6 and v7, keeping the ID and timestamp rules in `PROJECT_STATE.md` § Orientation. The two seed rows whose `balanceAfter` chain looks wrong (Sophia Rossi, transactions 19 to 21) must be re-checked, not copied.

## 4. Code changes

Removed: `WalletTransactionRepository`, `JdbcWalletTransactionRepository`, `WalletLedgerWriter`, and the `feeAmount` field.

| Area | Change |
|---|---|
| `domain.enums.Role` | add `SYSTEM` (see § 2 for the call sites) |
| `domain.model.WalletTransaction` | kept as the **read model** of a money row: same fields, minus `feeAmount`; `walletId` is resolved from the row's subject user through `wallets`, `initiatedBy` is the row's actor. Type set gains `PLATFORM_FEE`. |
| `AuditLogEntry`, `AuditRecord.Builder`, `JdbcAuditLogRepository` | `balanceAfter`; `.wallet(amount, balanceAfter)`. |
| New `repository.LedgerRepository` (+ `JdbcLedgerRepository`) | `entriesForWallet(walletId)` and `entriesForBooking(bookingId)`; both filter `walletAdjustment IS NOT NULL` and order by `rowid` (insertion order, which also removes W12's same-second tie caveat for money rows). |
| New `service.impl.LedgerWriter` | `post(walletId, type, amount, actorId, bookingId, ticketId, reason)`: load the wallet through the existing `WalletRepository`, reject a negative result, save the new balance, write the audit row with `balanceAfter`. `postPayout(...)` writes payout + fee. Runs on the caller's connection and never opens a transaction. |
| `WalletServiceImpl` | `topUp` / `withdraw` wrap `LedgerWriter.post` in one `TransactionManager` transaction and publish the event after commit; `statementFor` reads `entriesForWallet`. `getWallet`, `balanceOf` unchanged. |
| `TransactionServiceImpl` | `holdEscrow`, `refundEscrow`, `settleBookingCompletion` use `LedgerWriter`; payout + fee rows and the booking `COMPLETED` change commit together. `historyFor` reads `entriesForBooking`. |
| `BookingServiceImpl` (3 inline blocks), `AccountGovernanceServiceImpl` (1), `DisputeSettlementServiceImpl` (`credit` + fee) | replace inline wallet code with `LedgerWriter` calls inside their existing transactions. This also removes the W3 handoff hazard (a fee passed through `WalletLedgerWriter` was subtracted twice). |
| `DisputeQueryServiceImpl`, `EscrowPolicy` | read `entriesForBooking`; `PLATFORM_FEE` is not a releasing type and always accompanies a payout, so `isHeld` is unchanged. |
| `WalletTransactionRecordedEvent`, `NavShellController` | unchanged. |
| `AppContext` | wire `JdbcLedgerRepository` and `LedgerWriter`; the booking and transaction services swap `WalletTransactionRepository` for the ledger repository. |

## 5. Contracts that stay

`WalletService` (`getWallet`, `balanceOf`, `topUp`, `withdraw`, `statementFor`) and `TransactionService` keep their method names and callers. C3's rule (UI touches `WalletService` only for top-up and withdrawal; only booking and ticket code touches `TransactionService`) stays, because a single choke point is the point of W14. The only part of C3 that changes is storage: `TransactionService` reaches money through `LedgerWriter` rather than writing the ledger table itself. See § 10, item 1.

## 6. How the guest and host wallets change

Guest and host share `WalletDashboardController`; it calls `balanceOf` and `statementFor` and reads `amount`, `type`, `createdAt`, `relatedBookingId`. All four survive, so **no controller or FXML change is needed**. The change is under `WalletService`: the statement is now "money rows whose subject is this wallet's owner, in insertion order" instead of "rows of this wallet". Two visible effects: a host's payout row shows the net amount the host received (as agent settlements already did, unlike the old `WalletLedgerWriter` path), and newest-first ordering is exact rather than by timestamp text. The fee row belongs to the System wallet and never appears on a guest or host statement.

## 7. Audit Log screen

No change to the screen. Money rows already render; `PLATFORM_FEE` appears as one more action type in the multi-select (add the enum value and its label), with the System user as subject.

## 8. Decisions reversed when this spec is approved (AGENTS.md § 4)

| Entry | What changes |
|---|---|
| § Known Gaps: "Platform fees are informational only … no system-owned platform Wallet" | removed; the System user's wallet holds the fees |
| C9 (single-sided wallet rows) | a payout is now two-sided in effect (host row + System row); refunds and overrides stay single-sided |
| C3 | narrowed to storage, see § 5 |
| C31: "add a balance to users" | **not done**: `wallets` stays (C40) |
| C32: System user is `AGENT` + `SUSPENDED` | replaced by role `SYSTEM`, status `ACTIVE` (C40) |
| Conventions: "`wallets.balance` is a cache of wallet_transactions" | reworded to the owner's money rows in `audit_log` |
| W12 D13.5 (dual-write) | resolved |

C31 pre-announced most of these; the two operator refinements are recorded as C40 in `PROJECT_STATE.md`. Nothing is reversed in code until the plan runs.

## 9. Tests

Conservation invariants, run on the rebuilt mock DB and after a real settlement: every wallet balance equals the sum of its money rows (and its newest `balanceAfter`); no booking releases more than it held; a booking that paid a host nets to exactly zero (refund + host net + fee = hold, C20). A global "money in = money out" identity is deliberately not asserted: a late guest cancellation keeps part of the hold with nobody, which is existing refund policy and out of scope. Also: each money-path test moves onto the new fixtures with the same assertions plus fee rows; atomicity tests (a failure after the balance write leaves neither balance nor row) for hold, refund, settlement, dispute settlement and account suspension; migration tests from a V005 database with legacy wallet transactions (with and without existing audit money rows) and from the mock DB; a V006 test that child tables still reference users and `foreign_key_check` is clean; `LedgerWriter` rejects a negative balance and a zero amount; a `SYSTEM` user cannot register, log in, be suspended or appear in the Accounts list. `LayerDependencyTest` and `UiDependencyTest` stay green. Files touching the old ledger table: `AccountFixture`, `SettlementFixtures`, `MockDbFixture`, `SchemaParityTest`, `DatabaseBootstrapTest`, `CommittedMockDbTest`, `W1FoundationIntegrationTest` and the wallet, booking, dispute, governance and audit tests.

## 10. Decisions and open items

**Decided by the operator (C40, 2026-09-27):**

- `wallets` stays a dedicated table, "for security and fewer changes" (reverses the `users.balance` part of C31).
- The System user gets a real `SYSTEM` role via a `users` table rebuild (reverses my earlier default and C32's `AGENT` + `SUSPENDED`).

**Open, defaults proposed:**

1. **Keep `WalletService` and `TransactionService`** (default) versus merging them. Kept: no UI or caller churn, and C3's caller rule still has value.
2. **Balance is a stored cache** (default; unchanged) versus computing `SUM(walletAdjustment)` on every read. The invariant test guards drift.
3. **No escrow account.** Held money is implied by unreleased `ESCROW_HOLD` rows, as today. Making the platform hold escrow in a System sub-account would be more "double-entry" but changes every booking path and the refund rules. Say so if you want it.
4. **Legacy fee backfill:** historical payouts get `PLATFORM_FEE` rows (default) so the System balance reflects fees already taken, versus starting it at 0 from V007 on.
5. **Rejected:** keeping `wallet_transactions` as a derived view; a `users.balance` column (operator).

## 11. Out of scope

A System-wallet screen or fee reporting UI (the balance is readable through `WalletService.balanceOf(SYSTEM_ACTOR_ID)` only); real payment integration (top-up and withdraw stay mocked); multi-currency; any change to the refund policy, the 3% rate or C17/C20 settlement rules; new Guest/Host UI.
