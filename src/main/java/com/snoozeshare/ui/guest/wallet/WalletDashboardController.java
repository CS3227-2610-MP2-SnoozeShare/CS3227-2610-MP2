package com.snoozeshare.ui.guest.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controller for the Guest wallet dashboard screen.
 * Displays balance, transaction history, and provides top-up/withdraw actions.
 */
public final class WalletDashboardController {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    @FXML private StackPane dashboardRoot;
    @FXML private Label balanceLabel;
    @FXML private VBox transactionContainer;
    @FXML private Label emptyLabel;
    @FXML private ScrollPane transactionScroll;

    private AppContext context;
    private final List<Subscription> subscriptions = new ArrayList<>();

    /** Wires the controller to the application context and loads initial data. */
    public void setContext(AppContext context) {
        this.context = context;
        loadData();
        subscribeToEvents();
    }

    /** Unsubscribes from events when navigating away. */
    public void cleanup() {
        for (Subscription sub : subscriptions) {
            sub.unsubscribe();
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
        balanceLabel.setText("SGD " + balance);

        List<WalletTransaction> transactions = context.walletService().statementFor(userId);
        transactionContainer.getChildren().clear();

        if (transactions.isEmpty()) {
            emptyLabel.setText("No transactions yet.");
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
        } else {
            emptyLabel.setVisible(false);
            emptyLabel.setManaged(false);
            List<WalletTransaction> reversed = new ArrayList<>(transactions);
            java.util.Collections.reverse(reversed);
            for (WalletTransaction txn : reversed) {
                transactionContainer.getChildren().add(buildTransactionCard(txn));
            }
        }
    }

    private Node buildTransactionCard(WalletTransaction txn) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.getStyleClass().add("transaction-card");
        card.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label(formatType(txn.type().name()));
        typeLabel.getStyleClass().add("transaction-type");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        BigDecimal amount = txn.amount().setScale(2, RoundingMode.HALF_UP);
        boolean isCredit = amount.compareTo(BigDecimal.ZERO) > 0;
        String amountText = (isCredit ? "+" : "") + "SGD " + amount;
        Label amountLabel = new Label(amountText);
        amountLabel.getStyleClass().add(
                isCredit ? "transaction-amount-positive" : "transaction-amount-negative");

        Label dateLabel = new Label(txn.createdAt()
                .atZone(java.time.ZoneId.systemDefault())
                .format(DATE_FORMAT));
        dateLabel.getStyleClass().add("small");
        dateLabel.setMinWidth(130);

        card.getChildren().addAll(typeLabel, spacer, amountLabel, dateLabel);

        if (txn.relatedBookingId() != null) {
            Label bookingRef = new Label("Booking: "
                    + txn.relatedBookingId().toString().substring(0, 8) + "...");
            bookingRef.getStyleClass().add("small");
            card.getChildren().add(bookingRef);
        }

        return card;
    }

    private static String formatType(String typeName) {
        return typeName.replace('_', ' ').substring(0, 1)
                + typeName.replace('_', ' ').substring(1).toLowerCase();
    }

    private void showModal(WalletActionDialogController.Mode mode) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("wallet-action-dialog.fxml"));
            Node dialogView = loader.load();
            WalletActionDialogController controller = loader.getController();

            StackPane overlay = new StackPane();
            overlay.getStyleClass().add("modal-overlay");
            overlay.getChildren().add(dialogView);
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
