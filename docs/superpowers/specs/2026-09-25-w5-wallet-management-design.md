# W5 — Guest Wallet Management (F4) Design Spec

**Status:** Approved via planning session, 2026-09-25
**Author:** Claude Opus 4.6, with Nathan, 2026-09-25
**Workstream:** W5 (F4 — Guest Wallet Management: Top-Up/Withdraw)
**Branch:** `w5`
**Backlog items:** F4.1.1, F4.1.2 (Sprint 1, High), F4.2.1 (Sprint 2, Medium)
**Depends on:** W1 (shared foundation — wallet provisioning, WalletService, ledger writer)
**Visual reference:** [UI design system spec](2026-09-23-ui-design-system-design.md) screens 8 (Wallet), 9 (Top Up Modal), 10 (Withdraw Modal)

---

## 1. Goal

Enable guests to top up their wallet, view their full transaction history, and withdraw
available funds. This is the Guest's financial management screen — the "Wallet" tab in the
Guest shell sidebar.

**What is already built (W1/W3):**
- `WalletService` interface and `WalletServiceImpl` — `topUp()`, `withdraw()`, `balanceOf()`, `statementFor()`, `getWallet()`
- `WalletLedgerWriter` — atomic ledger writes with event publishing
- `WalletRepository` / `WalletTransactionRepository` and their JDBC implementations
- Domain models: `Wallet`, `WalletTransaction`, `WalletTransactionType`
- `WalletTransactionRecordedEvent` and `InProcessEventBus`
- `WalletProvisioningService` — creates wallets at registration
- Sidebar balance display in `NavShellController`

**What W5 builds:** the UI layer only — a wallet dashboard screen with balance display,
transaction statement, and top-up/withdraw modals wired to the existing service.

---

## 2. Constraints & Decisions

| ID | Decision | Why |
|---|---|---|
| Inherits Known Gap | Top-up and withdraw are fully mocked — no real payment gateway or payout rail | Stated project constraint |
| Inherits C7 | No guest-side service fee | Operator-confirmed |
| Inherits C10 | Currency fixed to SGD | Operator-confirmed |
| W5-D1 | No separate "available balance" calculation needed — wallet balance already reflects escrowed amounts (deducted at booking time by `BookingServiceImpl.submitRequest`) | Escrow hold deducts immediately; `WalletLedgerWriter` rejects if `balanceAfter < 0` |
| W5-D2 | Single shared modal FXML for both top-up and withdraw (parameterized by mode) | The two dialogs differ only in title, button text, and service call — DRY |

---

## 3. Scope

### 3.1 Wallet Dashboard Screen (new)

A new screen at `ui.guest.wallet.WalletDashboardController` + `wallet-dashboard.fxml`:

- **Balance display:** Prominent label showing current wallet balance (SGD formatted to 2dp)
- **Action buttons:** "Top Up" and "Withdraw" buttons
- **Transaction history:** Scrollable list of transaction cards showing type, amount (green for
  credit, red for debit), date, and related booking ID if applicable
- **Event subscription:** Subscribes to `WalletTransactionRecordedEvent` for real-time refresh
- **Cleanup:** Unsubscribes on navigation away (same pattern as `TripDashboardController`)

### 3.2 Top-Up / Withdraw Modal (new)

A shared modal at `ui.guest.wallet.WalletActionDialogController` + `wallet-action-dialog.fxml`:

- **Parameterized:** `Mode.TOP_UP` sets title "Top Up Wallet", button "Top Up"; `Mode.WITHDRAW`
  sets title "Withdraw Funds", button "Withdraw"
- **Input:** Amount text field with validation (positive number only)
- **Current balance:** Displayed for reference
- **Action:** Calls `walletService.topUp()` or `walletService.withdraw()`
- **Error handling:** Catches `IllegalArgumentException`/`IllegalStateException` from service,
  displays message in status label (e.g., "Insufficient wallet funds")
- **Close:** Dismisses modal overlay on success or cancel

### 3.3 Navigation Wiring

- Add "Wallet" nav item to `guest-shell.fxml` sidebar
- Add `showWallet()` handler to `GuestShellController` (same pattern as `showMyTrips()`)
- Cleanup wallet controller when navigating away

### 3.4 Sidebar Balance Refresh

- `NavShellController` subscribes to `WalletTransactionRecordedEvent` so the right-panel
  balance updates in real-time after any wallet mutation

---

## 4. Out of Scope

- Host wallet management (W9 — identical UI, different shell)
- Real payment/payout integration (Known Gap)
- Transaction filtering or search within statement
- Wallet-to-wallet transfers
