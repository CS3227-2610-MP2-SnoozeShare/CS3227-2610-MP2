package com.snoozeshare.ui.guest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.ui.guest.trips.ReviewDialogController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

/** Loads the real Guest shell on the mock DB, checks the mockup structure and writes PNGs to build/ui-snapshots. */
class GuestUiSnapshotTest {

    private static final String AGENT_THEME =
            GuestUiSnapshotTest.class.getResource("/com/snoozeshare/ui/admin/agent-theme.css").toExternalForm();

    private static boolean toolkitAvailable;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
            toolkitAvailable = true;
        } catch (IllegalStateException alreadyStarted) {
            toolkitAvailable = true;
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            toolkitAvailable = false;
        }
        if (toolkitAvailable) {
            Platform.setImplicitExit(false);
        }
    }

    @Test
    void searchShowsTheSegmentedBarAndACardGrid(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                Shell shell = openShell(context, "wei.zhang@snoozeshare.test");
                Node search = shell.root.getCenter();
                assertNotNull(search.lookup(".guest-search-bar"));
                assertEquals(5, search.lookupAll(".guest-search-segment").size());
                GridPane grid = (GridPane) search.lookup(".guest-results");
                assertFalse(grid.getChildren().isEmpty(), "results load on entry");
                assertEquals(3, grid.getColumnConstraints().size());
                Node card = grid.getChildren().get(0);
                assertTrue(card.getStyleClass().contains("guest-listing-card"));
                assertNotNull(card.lookup(".guest-card-banner"));
                snapshot(shell.scene, "guest-search");
                return null;
            });
        }
    }

    @Test
    void tripsAreThreeCollapsibleSectionsWithPendingUnderUpcoming(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                Shell shell = openShell(context, "wei.zhang@snoozeshare.test");
                show(shell, "showMyTrips");
                Node trips = shell.root.getCenter();
                List<Node> headers = List.copyOf(trips.lookupAll(".guest-section-header"));
                assertEquals(3, headers.size());
                List<String> titles = trips.lookupAll(".guest-section-title").stream()
                        .map(node -> ((Label) node).getText()).toList();
                assertTrue(titles.get(0).startsWith("UPCOMING"), titles.toString());
                assertTrue(titles.get(1).startsWith("ACTIVE"), titles.toString());
                assertTrue(titles.get(2).startsWith("PAST"), titles.toString());
                // Wei has one pending request (upcoming) plus a completed and a cancelled stay (past).
                assertTrue(titles.get(0).contains("(1)"), titles.toString());
                assertTrue(titles.get(2).contains("(2)"), titles.toString());
                assertNotNull(trips.lookup(".guest-pill-warning"));
                snapshot(shell.scene, "guest-trips");

                Node upcomingContent = ((Parent) headers.get(0).getParent()).getChildrenUnmodifiable().get(1);
                assertTrue(upcomingContent.isVisible());
                click(headers.get(0));
                assertFalse(upcomingContent.isVisible(), "clicking a header collapses its section");
                assertFalse(upcomingContent.isManaged());
                snapshot(shell.scene, "guest-trips-collapsed");
                click(headers.get(0));
                assertTrue(upcomingContent.isVisible(), "clicking again expands it");
                return null;
            });
        }
    }

    @Test
    void listingDetailModalFollowsTheMockup(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                Shell shell = openShell(context, "wei.zhang@snoozeshare.test");
                Property property = context.listingService()
                        .search(new com.snoozeshare.service.SearchCriteria(null, null, null, null))
                        .get(0).property();
                Method open = GuestShellController.class.getDeclaredMethod("showDetailModal",
                        Property.class, LocalDate.class, LocalDate.class);
                open.setAccessible(true);
                open.invoke(shell.controller, property, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 5));
                settle(shell.root);
                Node modal = shell.root.getCenter();
                assertNotNull(modal.lookup(".guest-detail-crumbs"));
                assertNotNull(modal.lookup(".guest-detail-banner"));
                assertEquals(5, modal.lookupAll(".guest-fact-value").size());
                assertNotNull(modal.lookup(".guest-host-card"));
                assertNotNull(modal.lookup(".guest-price-card"));
                assertTrue(modal.lookupAll(".guest-price-total-label").stream()
                        .anyMatch(node -> ((Label) node).getText().startsWith("SGD")));
                Button book = (Button) modal.lookup(".guest-full-button");
                assertFalse(book.isDisabled(), "valid dates enable Book now");
                snapshot(shell.scene, "guest-listing-detail");
                return null;
            });
        }
    }

    @Test
    void reviewModalHasStaySummaryStarsAndFullWidthSubmit(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                Shell shell = openShell(context, "wei.zhang@snoozeshare.test");
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/guest/trips/review-dialog.fxml"));
                Node card = loader.load();
                ReviewDialogController controller = loader.getController();
                UUID listing = UUID.randomUUID();
                controller.configure(context, UUID.randomUUID(),
                        new ReviewDialogController.StaySummary(listing, "Riverside Apartment",
                                "Jul 15 – 18", "Olivia Bennett"), () -> { });
                StackPane overlay = new StackPane(card);
                overlay.getStyleClass().addAll("agent-root", "modal-overlay");
                Scene scene = new Scene(overlay, 1280, 800);
                scene.getStylesheets().add(AGENT_THEME);
                overlay.applyCss();
                overlay.layout();
                assertEquals(480, card.getLayoutBounds().getWidth(), 1);
                assertEquals(5, card.lookupAll(".guest-star").size());
                Label thirdStar = (Label) List.copyOf(card.lookupAll(".guest-star")).get(2);
                thirdStar.getOnMouseClicked().handle(null);
                assertEquals(3, card.lookupAll(".guest-star-on").size());
                assertEquals(3, controllerRating(controller));
                snapshot(scene, "guest-review-modal");
                return shell;
            });
        }
    }

    @Test
    void walletBalanceCardIsBorderlessBrandBackgroundWithDarkBrownText(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                Shell shell = openShell(context, "wei.zhang@snoozeshare.test");
                show(shell, "showWallet");
                Node card = shell.root.getCenter().lookup(".wallet-balance-section");
                assertNotNull(card);
                javafx.scene.layout.Region region = (javafx.scene.layout.Region) card;
                assertEquals(javafx.scene.paint.Color.web("#f0e8d8"),
                        region.getBackground().getFills().get(0).getFill());
                assertTrue(region.getBorder() == null || region.getBorder().isEmpty(), "borderless");
                for (String style : Set.of(".wallet-balance-caption", ".wallet-balance-amount",
                        ".wallet-balance-currency", ".wallet-balance-hint")) {
                    Label label = (Label) card.lookup(style);
                    assertEquals(javafx.scene.paint.Color.web("#381a10"), label.getTextFill(), style);
                }
                snapshot(shell.scene, "guest-wallet");
                return null;
            });
        }
    }

    @Test
    void messagesUseSmallerTypeAndAStatusPillOnlyForTickets(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                // Wei has ticket conversations; Liam has a booking conversation.
                double[] wei = conversationHeights(context, "wei.zhang@snoozeshare.test", "wei");
                double[] liam = conversationHeights(context, "liam.osullivan@snoozeshare.test", "liam");
                double pillHeight = Math.max(wei[0], liam[0]);
                double buttonHeight = Math.max(wei[1], liam[1]);
                assertTrue(pillHeight > 0, "a ticket conversation shows the status pill");
                assertTrue(buttonHeight > 0, "a booking conversation shows New Ticket");
                assertEquals(buttonHeight, pillHeight, 1.0, "the pill has the New Ticket button's height");
                return null;
            });
        }
    }

    /** Walks every conversation; returns {pill height, New Ticket height}, 0 where never shown. */
    private static double[] conversationHeights(AppContext context, String email, String tag) throws Exception {
        Shell shell = openShell(context, email);
        show(shell, "showMessages");
        Node messages = shell.root.getCenter();
        @SuppressWarnings("unchecked")
        ListView<Object> list = (ListView<Object>) messages.lookup(".list-view");
        assertFalse(list.getItems().isEmpty());
        Label headerTitle = (Label) messages.lookup(".host-message-header-title");
        Label sidebarTitle = (Label) messages.lookup(".host-message-sidebar-title");
        assertEquals(15, headerTitle.getFont().getSize(), 0.1);
        assertEquals(16, sidebarTitle.getFont().getSize(), 0.1);
        Label pill = (Label) messages.lookup("#headerStatusLabel");
        Button newTicket = (Button) messages.lookup("#newTicketButton");
        double pillHeight = 0;
        double buttonHeight = 0;
        for (int index = 0; index < list.getItems().size(); index++) {
            list.getSelectionModel().select(index);
            settle(shell.root);
            boolean showsPill = pill.isVisible() && pill.isManaged();
            boolean showsButton = newTicket.isVisible() && newTicket.isManaged();
            assertTrue(showsPill ^ showsButton, "exactly one of pill and New Ticket per conversation");
            if (showsPill && pillHeight == 0) {
                pillHeight = pill.getHeight();
                snapshot(shell.scene, "guest-messages-ticket-" + tag);
            } else if (showsButton && buttonHeight == 0) {
                buttonHeight = newTicket.getHeight();
                snapshot(shell.scene, "guest-messages-booking-" + tag);
            }
        }
        return new double[] {pillHeight, buttonHeight};
    }

    @Test
    void hostMessagesShareTheSmallerHeaderAndShowTheTicketPill(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                context.session().loginAs(context.userService().authenticate("olivia.bennett@snoozeshare.test"));
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/host/host-shell.fxml"));
                BorderPane root = loader.load();
                com.snoozeshare.ui.host.HostShellController controller = loader.getController();
                controller.setContext(context);
                Scene scene = new Scene(root, 1280, 800);
                Method showMessages = controller.getClass().getDeclaredMethod("showMessages");
                showMessages.setAccessible(true);
                showMessages.invoke(controller);
                settle(root);
                Node messages = root.getCenter();
                @SuppressWarnings("unchecked")
                ListView<Object> list = (ListView<Object>) messages.lookup(".list-view");
                Label headerTitle = (Label) messages.lookup(".host-message-header-title");
                assertEquals(15, headerTitle.getFont().getSize(), 0.1);
                Label pill = (Label) messages.lookup("#headerStatusLabel");
                boolean pillShown = false;
                for (int index = 0; index < list.getItems().size(); index++) {
                    list.getSelectionModel().select(index);
                    settle(root);
                    if (pill.isVisible()) {
                        pillShown = true;
                        assertTrue(pill.getText().equals("OPEN") || pill.getText().equals("RESOLVED"));
                        assertEquals(36, pill.getHeight(), 1.0);
                        snapshot(scene, "host-messages-ticket");
                        break;
                    }
                }
                assertTrue(pillShown, "the host inbox has a ticket conversation whose header shows the pill");
                return null;
            });
        }
    }

    @Test
    void cancelDialogShowsThePolicyRefundAndConfirmsTheCancellation(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                Shell shell = openShell(context, "wei.zhang@snoozeshare.test");
                show(shell, "showMyTrips");
                Node trips = shell.root.getCenter();
                Button cancel = (Button) trips.lookup(".booking-reject-button");
                assertNotNull(cancel, "the pending request can be cancelled");
                cancel.fire();
                settle(shell.root);
                Label caption = (Label) trips.lookup(".guest-refund-caption");
                Label amount = (Label) trips.lookup(".guest-refund-amount");
                assertTrue(caption.getText().contains("100%"), caption.getText());
                assertTrue(amount.getText().startsWith("SGD 480"), amount.getText());
                assertNotNull(trips.lookup(".guest-policy-note"));
                snapshot(shell.scene, "guest-cancel-dialog");
                Button keep = (Button) trips.lookup(".guest-dialog-secondary");
                keep.fire();
                assertTrue(trips.lookup(".guest-refund-banner") == null, "Keep booking closes the dialog");
                assertNotNull(trips.lookup(".booking-reject-button"), "and the booking is still there");
                Button cancelAgain = (Button) trips.lookup(".booking-reject-button");
                cancelAgain.fire();
                settle(shell.root);
                Button confirm = (Button) trips.lookup(".guest-dialog-danger");
                confirm.fire();
                settle(shell.root);
                assertTrue(trips.lookup(".guest-refund-banner") == null, "Confirm closes the dialog");
                assertTrue(trips.lookupAll(".guest-pill-warning").isEmpty(), "the pending request is gone");
                return null;
            });
        }
    }

    @Test
    void anEmptyTableShowsItsMessageAsARowInsideTheTable(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                // Sophia's wallet is empty of nothing; a fresh account has no transactions at all.
                var newGuest = context.userService().register("Empty Guest", "empty.guest@snoozeshare.test",
                        com.snoozeshare.domain.enums.Role.GUEST, null);
                context.session().loginAs(newGuest);
                FXMLLoader loader = new FXMLLoader(GuestUiSnapshotTest.class.getResource(
                        "/com/snoozeshare/ui/guest/guest-shell.fxml"));
                BorderPane root = loader.load();
                GuestShellController controller = loader.getController();
                controller.setContext(context);
                Scene scene = new Scene(root, 1280, 800);
                Method wallet = GuestShellController.class.getDeclaredMethod("showWallet");
                wallet.setAccessible(true);
                wallet.invoke(controller);
                settle(root);
                Label row = (Label) root.lookup(".table-empty-row");
                assertNotNull(row);
                assertEquals("No transactions yet.", row.getText());
                assertEquals(47, row.getHeight(), 1.0, "one table row of space");
                var table = (javafx.scene.control.TableView<?>) root.lookup(".wallet-transaction-table");
                assertTrue(row.localToScene(row.getBoundsInLocal()).getMinY()
                        >= table.localToScene(table.getBoundsInLocal()).getMinY(), "inside the table");
                snapshot(scene, "guest-wallet-empty");
                return null;
            });
        }
    }

    @Test
    void guestsSelectorIsBorderlessAndScrollBarsAreSlim(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                Shell shell = openShell(context, "wei.zhang@snoozeshare.test");
                Node combo = shell.root.getCenter().lookup("#guestsCombo");
                javafx.scene.layout.Region region = (javafx.scene.layout.Region) combo;
                assertTrue(region.getBorder() == null || region.getBorder().getStrokes().stream()
                        .allMatch(stroke -> stroke.getTopStroke().equals(javafx.scene.paint.Color.TRANSPARENT)),
                        "the guests selector has no visible border");
                var bars = shell.root.getCenter().lookupAll(".scroll-bar").stream()
                        .map(node -> (javafx.scene.control.ScrollBar) node)
                        .filter(bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                        .filter(javafx.scene.Node::isVisible)
                        .toList();
                assertFalse(bars.isEmpty());
                for (var bar : bars) {
                    assertEquals(10, bar.getMaxWidth(), 0.5, "slim, like the table scroll bars");
                    assertEquals(10, bar.getMinWidth(), 0.5);
                }
                return null;
            });
        }
    }

    @Test
    void pastDatesAreBlockedInGuestPickers(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            onFx(() -> {
                Shell shell = openShell(context, "wei.zhang@snoozeshare.test");
                javafx.scene.control.DatePicker picker =
                        (javafx.scene.control.DatePicker) shell.root.getCenter().lookup("#checkInPicker");
                javafx.scene.control.DateCell yesterday = picker.getDayCellFactory().call(picker);
                yesterday.updateItem(LocalDate.now().minusDays(1), false);
                javafx.scene.control.DateCell today = picker.getDayCellFactory().call(picker);
                today.updateItem(LocalDate.now(), false);
                assertTrue(yesterday.isDisable(), "yesterday cannot be picked");
                assertFalse(today.isDisable(), "today can");
                javafx.scene.layout.Region combo =
                        (javafx.scene.layout.Region) shell.root.getCenter().lookup("#guestsCombo");
                assertTrue(combo.getBorder() == null || combo.getBorder().getStrokes().stream()
                        .allMatch(stroke -> stroke.getTopStroke().equals(javafx.scene.paint.Color.TRANSPARENT)),
                        "no visible border on the guests selector");
                return null;
            });
        }
    }

    private record Shell(BorderPane root, GuestShellController controller, Scene scene) { }

    private static Shell openShell(AppContext context, String email) throws Exception {
        context.session().loginAs(context.userService().authenticate(email));
        FXMLLoader loader = new FXMLLoader(GuestUiSnapshotTest.class.getResource(
                "/com/snoozeshare/ui/guest/guest-shell.fxml"));
        BorderPane root = loader.load();
        GuestShellController controller = loader.getController();
        controller.setContext(context);
        Scene scene = new Scene(root, 1280, 800);
        root.applyCss();
        root.layout();
        return new Shell(root, controller, scene);
    }

    private static void show(Shell shell, String method) throws Exception {
        Method m = shell.controller.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(shell.controller);
        settle(shell.root);
    }

    /** ScrollPane skins (and so their content) only exist after CSS and layout have run. */
    private static void settle(Parent root) {
        root.applyCss();
        root.layout();
        root.applyCss();
        root.layout();
    }

    private static int controllerRating(ReviewDialogController controller) throws Exception {
        Method m = ReviewDialogController.class.getDeclaredMethod("selectedRating");
        m.setAccessible(true);
        return (int) m.invoke(controller);
    }

    private static void click(Node node) {
        node.getOnMouseClicked().handle(null);
    }

    private static void snapshot(Scene scene, String name) throws Exception {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        WritableImage image = scene.snapshot(null);
        Path file = Path.of("build", "ui-snapshots", name + ".png");
        Files.createDirectories(file.getParent());
        ImageIO.write(toBuffered(image), "png", file.toFile());
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future.get(60, TimeUnit.SECONDS);
    }

    private static BufferedImage toBuffered(WritableImage image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return buffered;
    }
}
