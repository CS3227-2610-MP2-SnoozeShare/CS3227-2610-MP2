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
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

/** Shared wallet dashboard for Guest and Host. */
public final class WalletDashboardController {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    @FXML private StackPane dashboardRoot;
    @FXML private Label balanceAmountLabel;
    @FXML private Label escrowHeldLabel;
    @FXML private TableView<WalletTransaction> transactionTable;
    @FXML private TableColumn<WalletTransaction, String> dateColumn;
    @FXML private TableColumn<WalletTransaction, String> typeColumn;
    @FXML private TableColumn<WalletTransaction, String> relatedColumn;
    @FXML private TableColumn<WalletTransaction, String> amountColumn;
    @FXML private TableColumn<WalletTransaction, String> balanceAfterColumn;

    private AppContext context;
    private final List<Subscription> subscriptions = new ArrayList<>();

    @FXML
    private void initialize() {
        dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().createdAt().atZone(ZoneId.systemDefault()).format(DATE_FORMAT)));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                WalletTransactionFormatter.typeLabel(cell.getValue().type())));
        relatedColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                WalletTransactionFormatter.relatedLabel(cell.getValue())));
        amountColumn.setCellValueFactory(cell -> new SimpleStringProperty(amountWithFee(
                cell.getValue())));
        balanceAfterColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                WalletTransactionFormatter.balanceLabel(cell.getValue().balanceAfter())));

        typeColumn.setCellFactory(column -> styledCell("transaction-type"));
        amountColumn.setCellFactory(column -> styledAmountCell());
        transactionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

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

        List<WalletTransaction> newestFirst = new ArrayList<>(
                context.walletService().statementFor(userId));
        java.util.Collections.reverse(newestFirst);
        escrowHeldLabel.setText(WalletTransactionFormatter.escrowHeldLabel(newestFirst));
        transactionTable.getItems().setAll(newestFirst);
        if (newestFirst.isEmpty()) {
            return;
        }

    }

    private static String amountWithFee(WalletTransaction transaction) {
        String fee = WalletTransactionFormatter.feeLabel(transaction.feeAmount());
        return fee.isEmpty() ? WalletTransactionFormatter.amountLabel(transaction.amount())
                : WalletTransactionFormatter.amountLabel(transaction.amount()) + "\n" + fee;
    }

    private static TableCell<WalletTransaction, String> styledCell(String styleClass) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().remove(styleClass);
                if (!empty) {
                    getStyleClass().add(styleClass);
                }
            }
        };
    }

    private TableCell<WalletTransaction, String> styledAmountCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("transaction-amount-positive",
                        "transaction-amount-negative");
                if (!empty) {
                    WalletTransaction transaction = getTableView().getItems().get(getIndex());
                    getStyleClass().add(transaction.amount().signum() >= 0
                            ? "transaction-amount-positive" : "transaction-amount-negative");
                }
            }
        };
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
