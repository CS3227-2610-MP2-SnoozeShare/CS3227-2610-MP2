# W5 — Guest Wallet Management Implementation Plan

**Goal:** Enable guests to top up, view transaction history, and withdraw funds via a Wallet
dashboard screen in the Guest shell.

**Spec:** `docs/superpowers/specs/2026-09-25-w5-wallet-management-design.md`

## Global Constraints

- Java 25 language level, SQLite via plain JDBC
- UI controllers depend only on `service.*` interfaces
- Backend is fully built — W5 is UI-only
- Top-up/withdraw are mocked stubs (no real payment gateway)
- Use `compareTo` for monetary BigDecimal assertions (deviation D4)
- All existing tests must pass after every task
- Incremental commits at task boundaries

---

### Task 1: Wallet Dashboard Screen — balance + transaction history

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/guest/wallet/WalletDashboardController.java`
- Create: `src/main/resources/com/snoozeshare/ui/guest/wallet/wallet-dashboard.fxml`

**Controller:** `setContext(AppContext)` loads balance and statement, subscribes to
`WalletTransactionRecordedEvent`. `cleanup()` unsubscribes. Transaction cards built
programmatically in a ScrollPane VBox.

**FXML:** Root `StackPane` (for modal overlay support) wrapping content VBox with balance label,
action buttons, and scrollable transaction list.

**Reuses:** `TripDashboardController` pattern (event subscription, cleanup, card rendering)

---

### Task 2: Top-Up / Withdraw Modal Dialog

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/guest/wallet/WalletActionDialogController.java`
- Create: `src/main/resources/com/snoozeshare/ui/guest/wallet/wallet-action-dialog.fxml`

**Controller:** `enum Mode { TOP_UP, WITHDRAW }`. `configure(Mode, AppContext, balance, onClose)`
sets title/button text. `handleAction()` parses amount, calls service, shows error or closes on
success. `handleCancel()` calls onClose.

**Reuses:** Modal overlay pattern from `GuestShellController.showDetailModal()`

---

### Task 3: Wire Modals into Dashboard

**Files:**
- Modify: `WalletDashboardController.java`

Add `showModal(Mode)` method that loads `wallet-action-dialog.fxml`, creates overlay StackPane,
adds to dashboard root, configures controller. onClose removes overlay and refreshes data.

---

### Task 4: Wire Navigation in Guest Shell

**Files:**
- Modify: `src/main/resources/com/snoozeshare/ui/guest/guest-shell.fxml`
- Modify: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`
- Modify: `src/test/java/com/snoozeshare/ui/ShellNavigationTest.java`

Add "Wallet" nav item to sidebar. Add `showWallet()` handler and `cleanupWalletController()`.
Update test to expect `showWallet` method.

---

### Task 5: CSS Styles

**Files:**
- Modify: `src/main/resources/com/snoozeshare/ui/common/theme.css`

Add `.transaction-card`, `.transaction-amount-positive` (green), `.transaction-amount-negative`
(red), `.transaction-type`, `.wallet-balance-section`.

---

### Task 6: Sidebar Wallet Balance Event Refresh

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/common/NavShellController.java`

Subscribe to `WalletTransactionRecordedEvent` in `setContext()`, refresh `walletAmount` label
via `Platform.runLater()`.
