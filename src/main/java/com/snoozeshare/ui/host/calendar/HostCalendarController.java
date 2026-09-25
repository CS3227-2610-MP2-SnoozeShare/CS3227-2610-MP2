package com.snoozeshare.ui.host.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.AvailabilityBlock;
import com.snoozeshare.domain.model.Property;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class HostCalendarController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @FXML
    private Label monthLabel;

    @FXML
    private GridPane calendarGrid;

    @FXML
    private VBox overridesContainer;

    @FXML
    private TextField fromField;

    @FXML
    private TextField toField;

    @FXML
    private TextField reasonField;

    @FXML
    private Label statusLabel;

    private AppContext context;
    private Property property;
    private YearMonth displayedMonth = YearMonth.now();
    private Runnable onBack = () -> { };

    public void setContext(AppContext appContext) {
        context = appContext;
        refresh();
    }

    public void setProperty(Property selectedProperty) {
        property = selectedProperty;
        displayedMonth = YearMonth.now();
        refresh();
    }

    public void setOnBack(Runnable callback) {
        onBack = callback == null ? () -> { } : callback;
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
    private void handleBlockDates() {
        try {
            LocalDate start = LocalDate.parse(fromField.getText(), DATE_FORMAT);
            LocalDate end = LocalDate.parse(toField.getText(), DATE_FORMAT);
            context.availabilityService().createHostBlock(property.propertyId(), start, end,
                    context.session().currentUser().orElseThrow().userId(), reasonField.getText());
            fromField.clear();
            toField.clear();
            reasonField.clear();
            statusLabel.setText("Dates blocked.");
            refresh();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            statusLabel.setText(exception.getMessage());
        }
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
        monthLabel.setText(displayedMonth.format(MONTH_FORMAT));
        renderCalendar(blocks);
        renderOverrides(blocks);
    }

    private void renderCalendar(List<AvailabilityBlock> blocks) {
        calendarGrid.getChildren().clear();
        LocalDate first = displayedMonth.atDay(1);
        LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
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
            GridPane.setRowIndex(cell, index / 7);
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
                + DATE_FORMAT.format(block.endDate()));
        Button remove = new Button("Remove");
        remove.setAccessibleText("Remove override " + dates.getText());
        remove.getStyleClass().add("text-button");
        remove.setOnAction(event -> handleRemoveOverride(block));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, dates, spacer, remove);
        row.getStyleClass().add("override-row");
        overridesContainer.getChildren().add(row);
    }

    private static boolean hasSource(List<AvailabilityBlock> blocks, LocalDate date, String source) {
        return blocks.stream()
                .filter(block -> source.equals(block.source()))
                .anyMatch(block -> !date.isBefore(block.startDate())
                        && date.isBefore(block.endDate()));
    }
}
