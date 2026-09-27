# W9 — Host Wallet Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the wallet experience into a common Guest/Host implementation and match the approved wallet mockups, including complete transaction statements and amount shortcuts.

**Architecture:** `WalletService` remains the only service used by the wallet UI. The current guest-package dashboard and dialog become common wallet controllers/resources used by both shells. A small common formatter and table-like FXML view render every persisted `WalletTransaction` without changing booking or dispute settlement ownership.

**Tech Stack:** Java 25, JavaFX 25, FXML, existing shared `theme.css`, JUnit 5/TestFX, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-27-w9-host-wallet-management-design.md`

## Global Constraints

- Use the existing `WalletService` / `WalletLedgerWriter` atomic ledger boundary; do not add wallet tables or settlement behavior.
- The common wallet UI must display all seven `WalletTransactionType` values and must not filter by role or type.
- Top-up presets are exactly `$50`, `$100`, `$500`, and `$1000`; each replaces the amount field value.
- Withdrawal's `Withdraw full available balance` control replaces the amount field with the current balance.
- Successful mutations reload balance and statement from `WalletService`; failed mutations leave the modal open and unchanged.
- Preserve `WalletTransactionRecordedEvent` refreshes and unsubscribe on navigation cleanup.
- Keep the existing SGD formatting and the project's JavaFX/FXML/package dependency boundaries.

## Review Focus

- A host wallet must load the common page through Host navigation, not the old guest-package controller — pin with `hostWalletUsesCommonWalletResource` in `WalletUiStructureTest`.
- A statement row must not silently drop payout fee metadata or related booking/ticket/agent references — pin with `rendersAllTransactionTypesAndReferences` in `WalletDashboardControllerTest`.
- A zero/negative/non-numeric amount must not invoke the service — pin with `rejectsInvalidAmountBeforeServiceCall` in `WalletActionDialogControllerTest`.
- An overdraft must keep the withdrawal modal open and preserve the entered value — pin with `keepsModalOpenWhenWithdrawalIsRejected` in `WalletActionDialogControllerTest`.
- An event arriving after navigation must not update a cleaned-up controller — pin with `cleanupUnsubscribesWalletRefresh` in `WalletDashboardControllerTest`.

### Task 1: Extract common wallet controllers and formatter

**Files:**
- Create: `src/main/java/com/snoozeshare/ui/common/wallet/WalletDashboardController.java`
- Create: `src/main/java/com/snoozeshare/ui/common/wallet/WalletActionDialogController.java`
- Create: `src/main/java/com/snoozeshare/ui/common/wallet/WalletTransactionFormatter.java`
- Delete: `src/main/java/com/snoozeshare/ui/guest/wallet/WalletDashboardController.java`
- Delete: `src/main/java/com/snoozeshare/ui/guest/wallet/WalletActionDialogController.java`
- Test: `src/test/java/com/snoozeshare/ui/WalletDashboardControllerTest.java`
- Test: `src/test/java/com/snoozeshare/ui/WalletActionDialogControllerTest.java`

**Interfaces:**
- Consumes: `AppContext`, `WalletService`, `WalletTransactionRecordedEvent`, `WalletTransaction`, and the existing `Subscription` contract.
- Produces: `WalletDashboardController.setContext(AppContext)`, `WalletDashboardController.cleanup()`, `WalletActionDialogController.Mode`, `WalletActionDialogController.configure(Mode, AppContext, BigDecimal, Runnable)`, `WalletActionDialogController.setPresetAmount(BigDecimal)`, `WalletActionDialogController.setFullAvailableBalance()`, `WalletTransactionFormatter.typeLabel(WalletTransactionType)`, `WalletTransactionFormatter.relatedLabel(WalletTransaction)`, `WalletTransactionFormatter.amountLabel(BigDecimal)`, `WalletTransactionFormatter.balanceLabel(BigDecimal)`, and `WalletTransactionFormatter.feeLabel(BigDecimal)`.

- [ ] **Step 1: Write failing formatter and controller tests**

  Add tests for `WalletTransactionFormatter` that assert exact labels for all seven transaction types, em-dash output for no related entity, booking/ticket references, signed SGD amounts, and non-zero payout fee text. Add controller tests for complete rendering, invalid input rejection, overdraft behavior, and event cleanup using the existing JavaFX test setup patterns.

- [ ] **Step 2: Run the focused UI tests and verify they fail**

  Run: `./gradlew test --tests 'com.snoozeshare.ui.WalletDashboardControllerTest' --tests 'com.snoozeshare.ui.WalletActionDialogControllerTest'`

  Expected: FAIL because the common controllers, formatter, and new interaction methods do not yet exist.

- [ ] **Step 3: Implement the common formatter and move controller behavior**

  Move the existing balance/statement/event lifecycle into `ui.common.wallet`, then replace card-only transaction rendering with formatter-backed row data suitable for the statement table. Keep `setContext`, `cleanup`, `Mode`, and `configure` signatures stable for shell callers. Implement `setPresetAmount(BigDecimal)` and `setFullAvailableBalance()` so they only update the text field and do not call the service; FXML event handlers delegate to these methods.

- [ ] **Step 4: Run the focused tests and verify they pass**

  Run: `./gradlew test --tests 'com.snoozeshare.ui.WalletDashboardControllerTest' --tests 'com.snoozeshare.ui.WalletActionDialogControllerTest'`

  Expected: PASS.

- [ ] **Step 5: Commit the common wallet controller extraction**

  ```bash
  git add src/main/java/com/snoozeshare/ui/common/wallet src/main/java/com/snoozeshare/ui/guest/wallet src/test/java/com/snoozeshare/ui/WalletDashboardControllerTest.java src/test/java/com/snoozeshare/ui/WalletActionDialogControllerTest.java
  git commit -m "refactor: move wallet UI controllers to common package"
  ```

### Task 2: Build the mockup-based common wallet page and action dialogs

**Files:**
- Create: `src/main/resources/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml`
- Create: `src/main/resources/com/snoozeshare/ui/common/wallet/wallet-action-dialog.fxml`
- Modify: `src/main/resources/com/snoozeshare/ui/common/theme.css`
- Test: `src/test/java/com/snoozeshare/ui/WalletUiStructureTest.java`

**Interfaces:**
- Consumes: common controllers and the existing shell stylesheet.
- Produces: a balance card, action stack, statement columns (`DATE`, `TYPE`, `RELATED TO`, `AMOUNT`, `BALANCE AFTER`), mockup modal scrim/card, four top-up preset controls, and the full-balance withdrawal control.

- [ ] **Step 1: Write failing FXML structure tests**

  Add `WalletUiStructureTest` assertions that both common FXML files reference common controllers; the dashboard contains the five statement headings, `Top up`, `Withdraw`, and `AVAILABLE BALANCE`; the top-up modal contains `$50`, `$100`, `$500`, `$1000`; and the withdrawal modal contains `Withdraw full available balance`.

- [ ] **Step 2: Run the structure test to verify it fails**

  Run: `./gradlew test --tests 'com.snoozeshare.ui.WalletUiStructureTest'`

  Expected: FAIL because the common resources do not exist.

- [ ] **Step 3: Implement the common FXML and stylesheet rules**

  Build the page as a responsive `VBox` with a balance/action header and a bordered statement table area. Use the supplied Fall Light palette: warm balance/card surfaces, rounded borders, green top-up/credit styling, red withdrawal/debit styling, and a grey modal scrim. Wire preset buttons and the full-balance text control to the explicit controller handlers.

- [ ] **Step 4: Run the structure test and verify it passes**

  Run: `./gradlew test --tests 'com.snoozeshare.ui.WalletUiStructureTest'`

  Expected: PASS.

- [ ] **Step 5: Commit the wallet page and modal resources**

  ```bash
  git add src/main/resources/com/snoozeshare/ui/common/wallet src/main/resources/com/snoozeshare/ui/common/theme.css src/test/java/com/snoozeshare/ui/WalletUiStructureTest.java
  git commit -m "feat: style common wallet page and action modals"
  ```

### Task 3: Rewire Guest and Host shells to the common wallet

**Files:**
- Modify: `src/main/java/com/snoozeshare/ui/guest/GuestShellController.java`
- Modify: `src/main/java/com/snoozeshare/ui/host/HostShellController.java`
- Modify: `src/test/java/com/snoozeshare/ui/ShellNavigationTest.java`
- Delete: `src/main/resources/com/snoozeshare/ui/guest/wallet/wallet-dashboard.fxml`
- Delete: `src/main/resources/com/snoozeshare/ui/guest/wallet/wallet-action-dialog.fxml`
- Delete: `src/main/resources/com/snoozeshare/ui/host/wallet/host-wallet-dashboard.fxml`

**Interfaces:**
- Consumes: `ui.common.wallet.WalletDashboardController` and `/com/snoozeshare/ui/common/wallet/wallet-dashboard.fxml`.
- Produces: Guest and Host navigation that load the same common wallet view, set context, and call `cleanup()` when leaving the page.

- [ ] **Step 1: Extend shell navigation tests with common-resource assertions**

  Assert that both shell controllers import/use the common `WalletDashboardController`, load the common wallet FXML path, set context, and clean up the controller. Assert that no shell points to the deleted guest or host wallet resources.

- [ ] **Step 2: Run the shell tests to verify the old paths are detected**

  Run: `./gradlew test --tests 'com.snoozeshare.ui.ShellNavigationTest'`

  Expected: FAIL until both shell controllers use the common path.

- [ ] **Step 3: Update both shell controllers and delete duplicate resources**

  Change imports, controller fields, and `FXMLLoader` paths only; preserve the existing navigation labels, center replacement, context setup, and cleanup order. Remove the duplicate role-specific wallet FXML files after both shells use the common resources.

- [ ] **Step 4: Run shell and FXML-loading tests**

  Run: `./gradlew test --tests 'com.snoozeshare.ui.ShellNavigationTest' --tests 'com.snoozeshare.ui.HostShellControllerTest'`

  Expected: PASS, including host and guest wallet resource assertions.

- [ ] **Step 5: Commit common wallet shell wiring**

  ```bash
  git add src/main/java/com/snoozeshare/ui/guest/GuestShellController.java src/main/java/com/snoozeshare/ui/host/HostShellController.java src/test/java/com/snoozeshare/ui/ShellNavigationTest.java
  git rm src/main/resources/com/snoozeshare/ui/guest/wallet/wallet-dashboard.fxml src/main/resources/com/snoozeshare/ui/guest/wallet/wallet-action-dialog.fxml src/main/resources/com/snoozeshare/ui/host/wallet/host-wallet-dashboard.fxml
  git commit -m "refactor: share wallet page across guest and host"
  ```

### Task 4: Verify host ledger behavior and full W9 integration

**Files:**
- Modify: `src/test/java/com/snoozeshare/service/WalletLedgerTest.java`
- Modify: `src/test/java/com/snoozeshare/ui/WalletDashboardControllerTest.java`
- Modify: `PROJECT_STATE.md`
- Modify: `docs/project-state/done-ledger.md`

**Interfaces:**
- Consumes: completed common wallet UI and existing `WalletService` implementation.
- Produces: evidence that host top-up/withdrawal, all transaction types, statement ordering, amount shortcuts, refresh, and navigation cleanup meet the spec.

- [ ] **Step 1: Add host-focused ledger assertions**

  Use a host fixture to assert positive top-up and withdrawal, chronological statement ordering, and overdraft rejection without changing the existing service contract. Add a complete mixed transaction fixture for all seven transaction types.

- [ ] **Step 2: Run focused service and UI verification**

  Run: `./gradlew test --tests 'com.snoozeshare.service.WalletLedgerTest' --tests 'com.snoozeshare.ui.WalletDashboardControllerTest' --tests 'com.snoozeshare.ui.WalletActionDialogControllerTest' --tests 'com.snoozeshare.ui.WalletUiStructureTest' --tests 'com.snoozeshare.ui.ShellNavigationTest'`

  Expected: PASS.

- [ ] **Step 3: Run Checkstyle, the full test suite, and build**

  Run: `./gradlew check test build`

  Expected: PASS, with any pre-existing unrelated UI failures recorded rather than hidden.

- [ ] **Step 4: Reconcile project state and Done ledger**

  Mark W9 `Done`, record the verification commands and any deviations in `PROJECT_STATE.md`, and add the newest W9 entry to `docs/project-state/done-ledger.md`. Leave the Developer Guide at `—` until the operator confirms the completed workstream and approves the feature checkpoint.

- [ ] **Step 5: Commit verification and project-state records**

  ```bash
  git add src/test/java/com/snoozeshare/service/WalletLedgerTest.java src/test/java/com/snoozeshare/ui/WalletDashboardControllerTest.java PROJECT_STATE.md docs/project-state/done-ledger.md
  git commit -m "test: verify W9 host wallet management"
  ```

## Final verification checklist

- [ ] `./gradlew check test build` passes or documented pre-existing failures are isolated.
- [ ] Guest and Host load the same common wallet FXML/controller.
- [ ] Statement renders all wallet transaction types newest first with references and fee metadata.
- [ ] Top-up presets populate `$50`, `$100`, `$500`, and `$1000`.
- [ ] Withdrawal full-balance control populates the current balance.
- [ ] Invalid and insufficient operations preserve modal state and do not write transactions.
- [ ] Navigation cleanup unsubscribes wallet event refreshes.
