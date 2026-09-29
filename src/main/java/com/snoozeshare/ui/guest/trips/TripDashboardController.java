package com.snoozeshare.ui.guest.trips;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.enums.BookingStatus;
import com.snoozeshare.domain.model.Booking;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.domain.model.Ticket;
import com.snoozeshare.infra.events.Subscription;
import com.snoozeshare.infra.events.events.BookingCancelledEvent;
import com.snoozeshare.infra.events.events.BookingConfirmedEvent;
import com.snoozeshare.infra.events.events.TicketOpenedEvent;
import com.snoozeshare.ui.guest.GuestVisuals;
import com.snoozeshare.ui.guest.tickets.TicketFilingController;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

public final class TripDashboardController {

    /** The three collapsible groups on the page, in display order. */
    enum Group {
        UPCOMING("Upcoming", "No upcoming trips."),
        ACTIVE("Active", "No active trips."),
        PAST("Past", "No past trips.");

        private final String title;
        private final String emptyText;

        Group(String title, String emptyText) {
            this.title = title;
            this.emptyText = emptyText;
        }
    }

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private static final Set<BookingStatus> CANCELLED_STATUSES = Set.of(
            BookingStatus.CANCELLED_BY_GUEST, BookingStatus.CANCELLED_BY_HOST,
            BookingStatus.REJECTED, BookingStatus.FORCE_CANCELLED);

    @FXML private StackPane tripRoot;
    @FXML private VBox tripsContainer;

    private AppContext context;
    private Consumer<UUID> onMessageHost;
    private Set<UUID> bookedTicketIds = Set.of();
    private final Set<Group> collapsed = new HashSet<>();
    private final Map<UUID, Property> propertyCache = new HashMap<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    public void setContext(AppContext context) {
        this.context = context;
        context.runBookingCompletionSweep();
        loadTrips();
        subscribeToEvents();
    }

    public AppContext getContext() {
        return context;
    }

    /** Called with a booking id when the guest presses "Message host". */
    public void setOnMessageHost(Consumer<UUID> onMessageHost) {
        this.onMessageHost = onMessageHost;
    }

    public void cleanup() {
        for (Subscription sub : subscriptions) {
            sub.unsubscribe();
        }
        subscriptions.clear();
    }

    /**
     * Which group a booking belongs to. A pending request is an upcoming trip awaiting the host, and a
     * confirmed stay whose end date has passed (still inside the dispute window) is past.
     */
    static Group groupOf(Booking booking, LocalDate today) {
        if (booking.status() == BookingStatus.PENDING) {
            return Group.UPCOMING;
        }
        if (booking.status() == BookingStatus.CONFIRMED) {
            if (booking.startDate().isAfter(today)) {
                return Group.UPCOMING;
            }
            return booking.endDate().isBefore(today) ? Group.PAST : Group.ACTIVE;
        }
        return Group.PAST;
    }

    void loadTrips() {
        var userId = context.session().currentUser().orElseThrow().userId();
        List<Booking> all = context.bookingService().tripsFor(userId, null);
        bookedTicketIds = context.ticketService().myTickets(userId).stream()
                .map(Ticket::bookingId)
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now();

        tripsContainer.getChildren().clear();
        for (Group group : Group.values()) {
            List<Booking> members = all.stream()
                    .filter(booking -> groupOf(booking, today) == group)
                    .sorted(group == Group.PAST
                            ? Comparator.comparing(Booking::endDate).reversed()
                            : Comparator.comparing(Booking::startDate))
                    .toList();
            tripsContainer.getChildren().add(buildSection(group, members, today));
        }
    }

    private Node buildSection(Group group, List<Booking> members, LocalDate today) {
        boolean open = !collapsed.contains(group);

        SVGPath chevron = new SVGPath();
        chevron.setContent("M1 1 L5 5 L9 1");
        chevron.getStyleClass().add("guest-chevron");
        chevron.setRotate(open ? 0 : -90);
        Label title = new Label(group.title.toUpperCase(Locale.ENGLISH) + "  (" + members.size() + ")");
        title.getStyleClass().add("guest-section-title");
        HBox header = new HBox(8, chevron, title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("guest-section-header");

        VBox content = new VBox(10);
        if (members.isEmpty()) {
            Label empty = new Label(group.emptyText);
            empty.getStyleClass().add("small");
            content.getChildren().add(empty);
        }
        for (Booking booking : members) {
            content.getChildren().add(buildTripCard(booking, group, today));
        }
        content.setVisible(open);
        content.setManaged(open);

        header.setOnMouseClicked(event -> {
            boolean nowOpen = !content.isVisible();
            content.setVisible(nowOpen);
            content.setManaged(nowOpen);
            chevron.setRotate(nowOpen ? 0 : -90);
            if (nowOpen) {
                collapsed.remove(group);
            } else {
                collapsed.add(group);
            }
        });

        return new VBox(10, header, content);
    }

    private Node buildTripCard(Booking booking, Group group, LocalDate today) {
        Property property = property(booking.listingId());
        String propertyTitle = property == null ? "Unknown Property" : property.title();

        Region thumb = new Region();
        thumb.getStyleClass().addAll("guest-thumb", GuestVisuals.gradientClass(booking.listingId()));

        Label pill = new Label(pillText(booking, group).toUpperCase(Locale.ENGLISH));
        pill.getStyleClass().addAll("guest-pill", pillStyleClass(booking, group));
        Label title = new Label(propertyTitle);
        title.getStyleClass().add("guest-trip-title");
        BigDecimal total = booking.totalAmount().setScale(2, RoundingMode.HALF_UP);
        Label subtitle = new Label(dateRange(booking.startDate(), booking.endDate(), today)
                + " · Host: " + hostName(property) + " · SGD " + total);
        subtitle.getStyleClass().add("guest-trip-subtitle");
        VBox info = new VBox(4, pill, title, subtitle);
        if (booking.hostDecisionMessage() != null && !booking.hostDecisionMessage().isBlank()) {
            Label hostMessage = new Label("Host message: " + booking.hostDecisionMessage());
            hostMessage.getStyleClass().add("guest-trip-subtitle");
            hostMessage.setWrapText(true);
            info.getChildren().add(hostMessage);
        }
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (booking.status() == BookingStatus.CONFIRMED) {
            Button message = new Button("Message host");
            message.getStyleClass().add("outline-button");
            message.setOnAction(event -> {
                if (onMessageHost != null) {
                    onMessageHost.accept(booking.bookingId());
                }
            });
            actions.getChildren().add(message);
        }
        if (canFileDispute(booking)) {
            Button dispute = new Button("File dispute");
            dispute.getStyleClass().add("outline-button");
            dispute.setOnAction(event -> showDisputeModal(booking));
            actions.getChildren().add(dispute);
        }
        if (canCancel(booking)) {
            Button cancel = new Button("Cancel");
            cancel.getStyleClass().add("booking-reject-button");
            cancel.setOnAction(event -> showCancelModal(booking, property, today));
            actions.getChildren().add(cancel);
        }
        if (canReview(booking)) {
            Button review = new Button("Leave a review");
            review.getStyleClass().add("button");
            review.setOnAction(event -> showReviewModal(booking, property, today));
            actions.getChildren().add(review);
        }

        HBox card = new HBox(14, thumb, info, actions);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("guest-trip-card");
        if (CANCELLED_STATUSES.contains(booking.status())) {
            card.getStyleClass().add("guest-trip-card-cancelled");
        }
        return card;
    }

    private Property property(UUID listingId) {
        return propertyCache.computeIfAbsent(listingId, id -> {
            try {
                return context.listingService().getDetail(id);
            } catch (Exception exception) {
                return null;
            }
        });
    }

    private String hostName(Property property) {
        if (property == null) {
            return "Host";
        }
        try {
            return context.userService().findById(property.hostId()).displayName();
        } catch (Exception exception) {
            return "Host";
        }
    }

    /** "Sep 24 – 28", "Sep 28 – Oct 2", with the year added when a date is outside this year. */
    static String dateRange(LocalDate start, LocalDate end, LocalDate today) {
        boolean outsideThisYear = start.getYear() != today.getYear() || end.getYear() != today.getYear();
        String tail = outsideThisYear ? ", " + end.getYear() : "";
        if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()
                && !start.equals(end)) {
            return MONTH_DAY.format(start) + " – " + end.getDayOfMonth() + tail;
        }
        String head = start.getYear() == end.getYear() ? "" : ", " + start.getYear();
        return MONTH_DAY.format(start) + head + " – " + MONTH_DAY.format(end) + tail;
    }

    private static String pillText(Booking booking, Group group) {
        return switch (booking.status()) {
            case PENDING -> "Pending";
            case CONFIRMED -> group == Group.ACTIVE ? "Checked in" : "Confirmed";
            case REJECTED -> "Rejected";
            case CANCELLED_BY_GUEST -> "Cancelled";
            case CANCELLED_BY_HOST -> "Cancelled by host";
            case COMPLETED, FORCE_COMPLETED -> "Completed";
            case FORCE_CANCELLED -> "Cancelled";
        };
    }

    private static String pillStyleClass(Booking booking, Group group) {
        return switch (booking.status()) {
            case PENDING -> "guest-pill-warning";
            case CONFIRMED -> group == Group.ACTIVE ? "guest-pill-accent" : "guest-pill-success";
            case COMPLETED, FORCE_COMPLETED -> "guest-pill-muted";
            case REJECTED, CANCELLED_BY_GUEST, CANCELLED_BY_HOST,
                    FORCE_CANCELLED -> "guest-pill-danger";
        };
    }

    private static boolean canCancel(Booking booking) {
        return booking.status() == BookingStatus.PENDING
                || (booking.status() == BookingStatus.CONFIRMED
                        && booking.startDate().isAfter(LocalDate.now()));
    }

    private void showCancelModal(Booking booking, Property property, LocalDate today) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/trips/cancel-booking-dialog.fxml"));
            Node dialogView = loader.load();
            CancelBookingDialogController controller = loader.getController();

            StackPane overlay = new StackPane();
            overlay.getStyleClass().add("modal-overlay");
            overlay.getChildren().add(dialogView);
            StackPane.setAlignment(dialogView, Pos.CENTER);
            tripRoot.getChildren().add(overlay);

            String bookingRef = booking.bookingId().toString();
            var summary = new CancelBookingDialogController.BookingSummary(
                    property == null ? "Unknown Property" : property.title(),
                    dateRange(booking.startDate(), booking.endDate(), today),
                    bookingRef.substring(bookingRef.length() - 4));
            Runnable close = () -> tripRoot.getChildren().remove(overlay);
            controller.configure(context, booking.bookingId(), summary, () -> {
                close.run();
                loadTrips();
            }, close);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load cancel dialog", exception);
        }
    }

    private boolean canFileDispute(Booking booking) {
        if (bookedTicketIds.contains(booking.bookingId())) {
            return false;
        }
        LocalDate today = LocalDate.now();
        boolean tripStarted = !today.isBefore(booking.startDate());
        boolean inWindow = !today.isAfter(booking.endDate().plusDays(7));
        boolean statusOk = booking.status() == BookingStatus.CONFIRMED
                || booking.status() == BookingStatus.COMPLETED;
        return statusOk && tripStarted && inWindow;
    }

    private boolean canReview(Booking booking) {
        return booking.status() == BookingStatus.COMPLETED
                && !context.reviewService().hasReview(booking.bookingId());
    }

    private void showDisputeModal(Booking booking) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/tickets/ticket-filing.fxml"));
            Node dialogView = loader.load();
            TicketFilingController controller = loader.getController();

            StackPane overlay = new StackPane();
            overlay.getStyleClass().add("modal-overlay");
            overlay.getChildren().add(dialogView);
            StackPane.setAlignment(dialogView, Pos.CENTER);
            tripRoot.getChildren().add(overlay);

            controller.configure(context, booking.bookingId(), () -> {
                tripRoot.getChildren().remove(overlay);
                loadTrips();
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load ticket filing dialog", exception);
        }
    }

    private void showReviewModal(Booking booking, Property property, LocalDate today) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/snoozeshare/ui/guest/trips/review-dialog.fxml"));
            Node dialogView = loader.load();
            ReviewDialogController controller = loader.getController();

            StackPane overlay = new StackPane();
            overlay.getStyleClass().add("modal-overlay");
            overlay.getChildren().add(dialogView);
            StackPane.setAlignment(dialogView, Pos.CENTER);
            tripRoot.getChildren().add(overlay);

            var stay = new ReviewDialogController.StaySummary(booking.listingId(),
                    property == null ? "Unknown Property" : property.title(),
                    dateRange(booking.startDate(), booking.endDate(), today),
                    hostName(property));
            controller.configure(context, booking.bookingId(), stay, () -> {
                tripRoot.getChildren().remove(overlay);
                loadTrips();
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load review dialog", exception);
        }
    }

    private void subscribeToEvents() {
        if (context.eventBus() == null) {
            return;
        }
        subscriptions.add(context.eventBus().subscribe(BookingConfirmedEvent.class,
                event -> Platform.runLater(this::loadTrips)));
        subscriptions.add(context.eventBus().subscribe(BookingCancelledEvent.class,
                event -> Platform.runLater(this::loadTrips)));
        subscriptions.add(context.eventBus().subscribe(TicketOpenedEvent.class,
                event -> Platform.runLater(this::loadTrips)));
    }
}
