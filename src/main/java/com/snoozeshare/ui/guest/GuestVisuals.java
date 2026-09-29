package com.snoozeshare.ui.guest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

/** Small presentation helpers shared by the Guest screens (search, trips, listing detail, review). */
public final class GuestVisuals {

    /** Number of {@code guest-gradient-N} classes defined in {@code agent-theme.css}. */
    public static final int GRADIENT_COUNT = 6;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private GuestVisuals() {
    }

    /** Unambiguous dates for the Guest date pickers, e.g. "1 Mar 2027" (the default 1/3/2027 reads two ways). */
    public static StringConverter<LocalDate> dateConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : DATE.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return LocalDate.parse(text.trim(), DATE);
                } catch (DateTimeParseException exception) {
                    return null;
                }
            }
        };
    }

    /** Greys out and disables every date before today, so a past date cannot be picked. */
    public static void blockPastDates(DatePicker picker) {
        picker.setDayCellFactory(view -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(!empty && date != null && date.isBefore(LocalDate.now()));
            }
        });
    }

    /** Listings have no photos, so each gets one of six palette gradients, stable per listing id. */
    public static String gradientClass(UUID listingId) {
        return "guest-gradient-" + Math.floorMod(listingId.hashCode(), GRADIENT_COUNT);
    }

    /** Up to two initials for an avatar, e.g. "Marcus Tan" gives "MT". */
    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }
}
