package com.snoozeshare.ui.host.calendar;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.ui.guest.GuestVisuals;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class HostCalendarController {

    private static final String[] WEEKDAY_LABELS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @FXML
    private Label monthLabel;

    @FXML
    private Hyperlink listingCrumb;

    @FXML
    private GridPane calendarGrid;

    @FXML
    private VBox overridesContainer;

    @FXML
    private DatePicker fromPicker;

    @FXML
    private DatePicker toPicker;

    @FXML
    private TextField reasonField;

    @FXML
    private Label statusLabel;

    private AppContext context;
    private Property property;
    private YearMonth displayedMonth = YearMonth.now();
    private Runnable onBack = () -> { };
    private Runnable onListingDetail = () -> { };

    @FXML
    private void initialize() {
        GuestVisuals.blockPastDates(fromPicker);
        GuestVisuals.blockPastDates(toPicker);
    }

    public void setContext(AppContext appContext) {
        context = appContext;
        refresh();
    }

    public void setProperty(Property selectedProperty) {
        property = selectedProperty;
        listingCrumb.setText(selectedProperty.title());
        displayedMonth = YearMonth.now();
        refresh();
    }

    public void setOnBack(Runnable callback) {
        onBack = callback == null ? () -> { } : callback;
    }

    public void setOnListingDetail(Runnable callback) {
        onListingDetail = callback == null ? () -> { } : callback;
    }

    @FXML
    private void handlePreviousMonth() {
        displayedMonth = displayedMonth.minusMonths(1);
        refresh();
    }

    @FXML
    private void handleNextMonth() {
        displayedMonth = displayedMonth.plusMonths(1);
        refresh();
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }

    @FXML
    private void handleListingDetail() {
        onListingDetail.run();
    }

    @FXML
    private void handleBlockDates() {
        try {
            LocalDate start = requiredDate(fromPicker, "From");
            LocalDate end = requiredDate(toPicker, "To");
            if (reasonField.getText() == null || reasonField.getText().isBlank()) {
                throw new IllegalArgumentException("Reason must be provided.");
            }
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("From date must not be after To date.");
            }
            context.availabilityService().createHostBlock(property.propertyId(), start, end,
                    context.session().currentUser().orElseThrow().userId(), reasonField.getText());
            fromPicker.setValue(null);
            toPicker.setValue(null);
            reasonField.clear();
            statusLabel.setText("");
            refresh();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            statusLabel.setText(exception.getMessage());
        }
    }

    private static LocalDate requiredDate(DatePicker picker, String fieldName) {
        if (picker.getValue() != null) {
            return picker.getValue();
        }
        String editorText = picker.getEditor().getText();
        if (editorText == null || editorText.isBlank()) {
            throw new IllegalArgumentException(fieldName + " date must be provided.");
        }
        throw new IllegalArgumentException(fieldName + " date must be valid.");
    }

    private void handleRemoveOverride(AvailabilityBlock block) {
        try {
            context.availabilityService().removeHostBlock(block.blockId(),
                    context.session().currentUser().orElseThrow().userId());
            statusLabel.setText("Override removed.");
            refresh();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            statusLabel.setText(exception.getMessage());
        }
    }

    public void refresh() {
        if (context == null || property == null || calendarGrid == null) {
            return;
        }
        List<AvailabilityBlock> blocks = context.availabilityService()
                .blocksFor(property.propertyId());
        if (calendarGrid.getClip() == null) {
            Rectangle clip = new Rectangle();
            clip.setArcWidth(24);
            clip.setArcHeight(24);
            clip.widthProperty().bind(calendarGrid.widthProperty());
            clip.heightProperty().bind(calendarGrid.heightProperty());
            calendarGrid.setClip(clip);
        }
        monthLabel.setText(displayedMonth.format(MONTH_FORMAT));
        renderCalendar(blocks);
        renderOverrides(blocks);
    }

    private void renderCalendar(List<AvailabilityBlock> blocks) {
        calendarGrid.getChildren().clear();
        for (int column = 0; column < WEEKDAY_LABELS.length; column++) {
            Label weekday = new Label(WEEKDAY_LABELS[column]);
            weekday.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            weekday.setAlignment(Pos.TOP_CENTER);
            weekday.setFont(Font.font(weekday.getFont().getFamily(), FontWeight.BOLD, 10));
            weekday.getStyleClass().add("calendar-weekday");
            GridPane.setColumnIndex(weekday, column);
            GridPane.setRowIndex(weekday, 0);
            calendarGrid.getChildren().add(weekday);
        }
        LocalDate first = displayedMonth.atDay(1);
        LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() % 7);
        // Keep a stable six-week calendar surface so the page does not jump between months.
        for (int index = 0; index < 42; index++) {
            LocalDate date = gridStart.plusDays(index);
            Label cell = new Label(Integer.toString(date.getDayOfMonth()));
            cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            cell.getStyleClass().add("calendar-cell");
            boolean otherMonth = !YearMonth.from(date).equals(displayedMonth);
            if (otherMonth) {
                cell.getStyleClass().add("calendar-cell-other-month");
            } else if (hasSource(blocks, date, "BOOKING")) {
                cell.getStyleClass().add("calendar-cell-booked");
            } else if (hasSource(blocks, date, "HOST_BLOCK")) {
                cell.getStyleClass().add("calendar-cell-blocked");
            } else {
                cell.getStyleClass().add("calendar-cell-available");
            }
            GridPane.setRowIndex(cell, index / 7 + 1);
            GridPane.setColumnIndex(cell, index % 7);
            calendarGrid.getChildren().add(cell);
        }
    }

    private void renderOverrides(List<AvailabilityBlock> blocks) {
        overridesContainer.getChildren().clear();
        blocks.stream()
                .filter(block -> "HOST_BLOCK".equals(block.source()))
                .sorted((left, right) -> left.startDate().compareTo(right.startDate()))
                .forEach(this::addOverrideRow);
    }

    private void addOverrideRow(AvailabilityBlock block) {
        Label dates = new Label(DATE_FORMAT.format(block.startDate()) + " – "
                + DATE_FORMAT.format(displayEndDate(block)));
        dates.getStyleClass().add("override-dates");
        Label reason = new Label(block.reason() == null || block.reason().isBlank()
                ? "No reason provided" : block.reason());
        reason.setFont(Font.font(reason.getFont().getFamily(), FontWeight.BOLD, 12));
        reason.getStyleClass().add("override-reason");
        Button remove = new Button("remove");
        remove.setFont(Font.font(remove.getFont().getFamily(), FontWeight.NORMAL, 12));
        remove.setAccessibleText("Remove override " + dates.getText());
        remove.getStyleClass().add("text-button");
        remove.setOnAction(event -> handleRemoveOverride(block));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, reason, spacer, remove);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox row = new VBox(2, header, dates);
        row.getStyleClass().add("override-row");
        overridesContainer.getChildren().add(row);
    }

    private static LocalDate displayEndDate(AvailabilityBlock block) {
        return "HOST_BLOCK".equals(block.source())
                ? block.endDate().minusDays(1)
                : block.endDate();
    }

    private static boolean hasSource(List<AvailabilityBlock> blocks, LocalDate date, String source) {
        return blocks.stream()
                .filter(block -> source.equals(block.source()))
                .anyMatch(block -> !date.isBefore(block.startDate())
                        && date.isBefore(block.endDate()));
    }
}
