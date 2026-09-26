package com.snoozeshare.ui.common.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.WalletTransaction;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared wallet dashboard for Guest and Host. */
public final class WalletDashboardController {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    @FXML private StackPane dashboardRoot;
    @FXML private Label balanceAmountLabel;
    @FXML private VBox transactionContainer;
    @FXML private Label emptyLabel;
    @FXML private ScrollPane transactionScroll;

    private AppContext context;
    private final List<Subscription> subscriptions = new ArrayList<>();

    public void setContext(AppContext context) {
        this.context = context;
        loadData();
        subscribeToEvents();
    }

    public void cleanup() {
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
    }

    @FXML
    private void handleTopUp() {
        showModal(WalletActionDialogController.Mode.TOP_UP);
    }

    @FXML
    private void handleWithdraw() {
        showModal(WalletActionDialogController.Mode.WITHDRAW);
    }

    private void loadData() {
        java.util.UUID userId = context.session().currentUser().orElseThrow().userId();
        BigDecimal balance = context.walletService().balanceOf(userId)
                .setScale(2, RoundingMode.HALF_UP);
        balanceAmountLabel.setText("$" + balance.toPlainString());

        List<WalletTransaction> transactions = context.walletService().statementFor(userId);
        transactionContainer.getChildren().clear();
        if (transactions.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }

        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        List<WalletTransaction> newestFirst = new ArrayList<>(transactions);
        java.util.Collections.reverse(newestFirst);
        for (WalletTransaction transaction : newestFirst) {
            transactionContainer.getChildren().add(buildTransactionRow(transaction));
        }
    }

    private Node buildTransactionRow(WalletTransaction transaction) {
        GridPane row = new GridPane();
        row.setHgap(0);
        row.setVgap(4);
        row.setMinHeight(47);
        row.setPrefHeight(47);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setPadding(new Insets(0, 18, 0, 18));
        row.getStyleClass().add("transaction-row");
        addWalletColumnConstraints(row);

        Label date = new Label(transaction.createdAt().atZone(ZoneId.systemDefault())
                .format(DATE_FORMAT));
        Label type = new Label(WalletTransactionFormatter.typeLabel(transaction.type()));
        Label related = new Label(WalletTransactionFormatter.relatedLabel(transaction));
        Label amount = new Label(WalletTransactionFormatter.amountLabel(transaction.amount()));
        Label balanceAfter = new Label(
                WalletTransactionFormatter.balanceLabel(transaction.balanceAfter()));

        type.getStyleClass().add("transaction-type");
        amount.getStyleClass().add(transaction.amount().signum() >= 0
                ? "transaction-amount-positive" : "transaction-amount-negative");
        String fee = WalletTransactionFormatter.feeLabel(transaction.feeAmount());
        if (!fee.isEmpty()) {
            Label feeLabel = new Label(fee);
            feeLabel.getStyleClass().add("small");
            row.add(feeLabel, 3, 1);
        }

        row.add(date, 0, 0);
        row.add(type, 1, 0);
        row.add(related, 2, 0);
        row.add(amount, 3, 0);
        row.add(balanceAfter, 4, 0);
        return row;
    }

    private static void addWalletColumnConstraints(GridPane grid) {
        for (double width : new double[] {16, 18, 28, 18, 20}) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(width);
            grid.getColumnConstraints().add(constraints);
        }
    }

    private void showModal(WalletActionDialogController.Mode mode) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("wallet-action-dialog.fxml"));
            Node dialogView = loader.load();
            WalletActionDialogController controller = loader.getController();

            StackPane overlay = new StackPane(dialogView);
            overlay.getStyleClass().add("modal-overlay");
            StackPane.setAlignment(dialogView, Pos.CENTER);
            dashboardRoot.getChildren().add(overlay);

            java.util.UUID userId = context.session().currentUser().orElseThrow().userId();
            BigDecimal balance = context.walletService().balanceOf(userId);
            controller.configure(mode, context, balance, () -> {
                dashboardRoot.getChildren().remove(overlay);
                loadData();
            });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to load wallet action dialog", exception);
        }
    }

    private void subscribeToEvents() {
        if (context.eventBus() == null) {
            return;
        }
        subscriptions.add(context.eventBus().subscribe(WalletTransactionRecordedEvent.class,
                event -> Platform.runLater(this::loadData)));
    }
}
