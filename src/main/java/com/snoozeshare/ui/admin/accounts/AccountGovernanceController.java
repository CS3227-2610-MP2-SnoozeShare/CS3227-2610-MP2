package com.snoozeshare.ui.admin.accounts;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.AccountStatus;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.AccountStatusChangedEvent;
import com.snoozeshare.service.AccountSummary;
import com.snoozeshare.ui.admin.accounts.SuspensionDialogController.Mode;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** The agent Accounts tab: live-searchable list with Suspend / Reactivate actions (F10.1.1). */
public final class AccountGovernanceController {

    private static final double[] COLUMN_SHARES = {17, 24, 12, 15, 20, 12};
    private static final String DASH = "—";

    @FXML private TextField searchField;
    @FXML private ScrollPane rowsScroll;
    @FXML private VBox rows;
    @FXML private Label emptyLabel;
    @FXML private Label errorLabel;

    private AppContext context;
    private Subscription subscription;
    private List<AccountSummary> accounts = List.of();

    @FXML
    private void initialize() {
        emptyLabel.visibleProperty().bind(emptyLabel.textProperty().isNotEmpty());
        emptyLabel.managedProperty().bind(emptyLabel.visibleProperty());
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        // Fit the scroll area to the laid-out rows (wrapped reasons included); the card still shrinks with the window.
        rowsScroll.prefHeightProperty().bind(rows.heightProperty().add(2));
        rowsScroll.minHeightProperty().bind(rowsScroll.prefHeightProperty()
                .map(height -> Math.min(height.doubleValue(), 60)));
        searchField.textProperty().addListener((observable, previous, text) -> render());
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        subscription = context.eventBus().subscribe(AccountStatusChangedEvent.class,
                event -> Platform.runLater(this::refresh));
        refresh();
    }

    /** Stops listening; the shell calls this when another tab replaces the screen. */
    public void dispose() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private void refresh() {
        accounts = context.accountGovernanceService().listAccounts();
        render();
    }

    private void render() {
        rows.getChildren().clear();
        ZoneId zone = ZoneId.systemDefault();
        String query = searchField.getText();
        for (AccountSummary account : accounts) {
            if (AccountSearch.matches(account, query, zone)) {
                rows.getChildren().add(row(account));
            }
        }
        emptyLabel.setText(rows.getChildren().isEmpty() ? "No accounts match your search" : "");
    }

    private GridPane row(AccountSummary account) {
        GridPane row = new GridPane();
        row.getStyleClass().addAll("agent-grid-row", "agent-account-row");
        for (double share : COLUMN_SHARES) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(share);
            row.getColumnConstraints().add(constraints);
        }
        row.add(cell(account.displayName(), "agent-cell-strong"), 0, 0);
        row.add(cell(account.email(), "agent-cell"), 1, 0);
        row.add(cell(AccountText.role(account.role()), "agent-cell"), 2, 0);
        row.add(cell(AccountText.joined(account.createdAt()), "agent-cell"), 3, 0);
        row.add(statusCell(account), 4, 0);
        row.add(actionCell(account), 5, 0);
        row.getChildren().forEach(child -> GridPane.setValignment(child, VPos.CENTER));
        return row;
    }

    private static Label cell(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("agent-cell", styleClass);
        return label;
    }

    private static VBox statusCell(AccountSummary account) {
        boolean suspended = account.status() == AccountStatus.SUSPENDED;
        Label pill = new Label(AccountText.status(account.status()).toUpperCase(Locale.ROOT));
        pill.getStyleClass().addAll("agent-pill", suspended ? "agent-pill-danger" : "agent-pill-success");
        VBox box = new VBox(3, pill);
        box.setAlignment(Pos.CENTER_LEFT);
        if (suspended && account.suspensionReason() != null) {
            Label reason = new Label("Reason: " + account.suspensionReason());
            reason.setWrapText(true);
            reason.getStyleClass().add("small");
            box.getChildren().add(reason);
        }
        return box;
    }

    private Node actionCell(AccountSummary account) {
        if (!account.governable()) {
            Label dash = new Label(DASH);
            dash.getStyleClass().add("agent-cell");
            return dash;
        }
        boolean suspended = account.status() == AccountStatus.SUSPENDED;
        Button button = new Button(suspended ? "Reactivate" : "Suspend");
        button.getStyleClass().addAll(suspended ? "outline-button" : "agent-button-danger", "agent-account-action");
        button.setOnAction(event -> openDialog(suspended ? Mode.REACTIVATE : Mode.SUSPEND, account));
        return button;
    }

    private void openDialog(Mode mode, AccountSummary account) {
        UUID agentId = context.session().currentUser().orElseThrow().userId();
        errorLabel.setText("");
        SuspensionDialogController.show(mode, account, reason -> {
            if (mode == Mode.SUSPEND) {
                context.accountGovernanceService().suspend(account.userId(), agentId, reason);
            } else {
                context.accountGovernanceService().reactivate(account.userId(), agentId, reason);
            }
        });
        refresh();
    }
}
