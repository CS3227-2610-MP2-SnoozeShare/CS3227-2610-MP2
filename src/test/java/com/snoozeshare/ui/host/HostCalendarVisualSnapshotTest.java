package com.snoozeshare.ui.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.Property;
import com.snoozeshare.testsupport.MockDbFixture;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;

/** Native visual diagnostic for the Host booking-calendar page. */
class HostCalendarVisualSnapshotTest {

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
    void writesCurrentHostCalendarSnapshot(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("olivia.bennett@snoozeshare.test"));
            Property property = context.listingService().findByHostId(
                    context.session().currentUser().orElseThrow().userId()).get(0);

            Path output = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/snoozeshare/ui/host/host-shell.fxml"));
                Parent root = loader.load();
                HostShellController controller = loader.getController();
                controller.setContext(context);
                Method showCalendar = HostShellController.class.getDeclaredMethod("showCalendar", Property.class);
                showCalendar.setAccessible(true);
                showCalendar.invoke(controller, property);
                Parent calendarPage = (Parent) ((BorderPane) root).getCenter();
                Scene scene = new Scene(root, 1280, 800);
                root.applyCss();
                calendarPage.applyCss();
                root.layout();
                assertTrue(calendarPage.getStylesheets().stream()
                        .anyMatch(stylesheet -> stylesheet.endsWith("/host-theme.css")));
                Label weekday = (Label) calendarPage.lookup(".calendar-weekday");
                Label reason = (Label) calendarPage.lookup(".override-reason");
                ScrollPane blockedDates = (ScrollPane) calendarPage.lookup(".scroll-pane");
                assertNotNull(weekday);
                assertNotNull(reason);
                assertNotNull(blockedDates);
                assertEquals(Pos.TOP_CENTER, weekday.getAlignment());
                assertTrue(weekday.getFont().getStyle().toLowerCase().contains("bold"));
                assertTrue(reason.getFont().getStyle().toLowerCase().contains("bold"));
                assertEquals(ScrollPane.ScrollBarPolicy.NEVER, blockedDates.getHbarPolicy());
                assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, blockedDates.getVbarPolicy());
                WritableImage image = scene.snapshot(null);
                Path file = Path.of("build", "ui-snapshots", "host-calendar-current.png");
                Files.createDirectories(file.getParent());
                ImageIO.write(toBuffered(image), "png", file.toFile());
                return file;
            });
            if (!Files.exists(output) || Files.size(output) == 0) {
                throw new IOException("Native calendar snapshot was not written");
            }
        }
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> work) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future.get(30, TimeUnit.SECONDS);
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
