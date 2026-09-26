package com.snoozeshare.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.domain.model.User;
import com.snoozeshare.testsupport.MockDbFixture;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ComboBox;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Test-only snapshot harness: renders agent screens at 1280x800 to build/ui-snapshots/ so they can be compared
 * with the design canvas by eye. It never asserts on pixels.
 */
class AdminUiSnapshotTest {

    static final Path OUTPUT = Path.of("build", "ui-snapshots");
    private static final String THEME = "/com/snoozeshare/ui/common/theme.css";

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

    /** Runs work on the FX thread and rethrows any failure on the caller's thread. */
    static <T> T onFx(Callable<T> work) throws Exception {
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

    /**
     * Puts {@code root} in a fresh scene of the given size (with the shared theme), applies CSS and layout, and
     * writes a PNG to build/ui-snapshots/name.png. Must be called on the FX thread.
     */
    static Path snapshot(Parent root, String name, double width, double height) throws IOException {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(AdminUiSnapshotTest.class.getResource(THEME).toExternalForm());
        root.applyCss();
        root.layout();
        WritableImage image = scene.snapshot(null);
        Files.createDirectories(OUTPUT);
        Path file = OUTPUT.resolve(name + ".png");
        ImageIO.write(toBuffered(image), "png", file.toFile());
        return file;
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

    /** Loads the agent shell signed in as an agent; the caller keeps the returned holder to pick a tab. */
    static Shell loadShell(AppContext context) throws Exception {
        FXMLLoader loader = new FXMLLoader(AdminUiSnapshotTest.class.getResource(
                "/com/snoozeshare/ui/admin/admin-shell.fxml"));
        Parent root = loader.load();
        AdminShellController controller = loader.getController();
        controller.setContext(context);
        return new Shell(root, controller);
    }

    record Shell(Parent root, AdminShellController controller) {
        void show(String method) throws Exception {
            Method target = AdminShellController.class.getDeclaredMethod(method);
            target.setAccessible(true);
            target.invoke(controller);
        }
    }

    @Test
    void writesQueueAndCategorySnapshots(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            User agent = context.userService().authenticate("amy.tanaka@snoozeshare.test");
            context.session().loginAs(agent);

            Path queue = onFx(() -> snapshot(loadShell(context).root(), "agent-queue", 1280, 800));
            Path categories = onFx(() -> {
                Shell shell = loadShell(context);
                shell.show("showCategories");
                return snapshot(shell.root(), "agent-categories", 1280, 800);
            });

            assertTrue(Files.size(queue) > 0);
            assertTrue(Files.size(categories) > 0);
        }
    }

    /** Shows the real queue in a stage, opens the status dropdown and snapshots both the header and the popup. */
    @Test
    void writesStatusDropdownSnapshots(@TempDir Path directory) throws Exception {
        assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable");
        try (MockDbFixture db = MockDbFixture.open(directory);
             AppContext context = AppContext.create(db.jdbcUrl())) {
            context.session().loginAs(context.userService().authenticate("amy.tanaka@snoozeshare.test"));
            Stage[] stage = new Stage[1];
            onFx(() -> {
                Parent root = loadShell(context).root();
                stage[0] = new Stage();
                stage[0].setScene(new Scene(root, 1280, 400));
                stage[0].setX(20);
                stage[0].setY(20);
                stage[0].show();
                root.applyCss();
                root.layout();
                stage[0].toFront();
                stage[0].requestFocus();
                return null;
            });
            Thread.sleep(500);
            Path[] files = null;
            double[] comboWidth = new double[1];
            double[] listWidth = new double[1];
            for (int attempt = 0; attempt < 4 && files == null; attempt++) {
                onFx(() -> {
                    ComboBox<?> combo = (ComboBox<?>) stage[0].getScene().getRoot().lookup(".combo-box");
                    combo.getSelectionModel().select(2);
                    combo.hide();
                    combo.show();
                    return null;
                });
                Thread.sleep(700);
                files = onFx(this::snapshotOpenDropdown);
                if (files != null) {
                    comboWidth[0] = onFx(() -> ((ComboBox<?>) stage[0].getScene().getRoot()
                            .lookup(".combo-box")).getWidth());
                    listWidth[0] = onFx(() -> Window.getWindows().stream().filter(w -> w instanceof PopupWindow)
                            .map(w -> w.getScene().getRoot().lookup(".list-view")).filter(n -> n != null)
                            .mapToDouble(n -> ((Region) n).getWidth()).findFirst().orElse(-1));
                }
            }
            onFx(() -> {
                stage[0].close();
                return null;
            });

            assumeTrue(files != null, "dropdown popup did not open (window not focused)");
            assertTrue(Files.size(files[0]) > 0);
            assertTrue(Files.size(files[1]) > 0);
            assertEquals(comboWidth[0], listWidth[0], 1.0, "popup list must be exactly as wide as the selector");
        }
    }

    private Path[] snapshotOpenDropdown() throws IOException {
        Window popup = Window.getWindows().stream().filter(w -> w instanceof PopupWindow && w.getWidth() > 10
                && w.getScene().getRoot().lookup(".list-view") != null).findFirst().orElse(null);
        if (popup == null) {
            return null;
        }
        Window page = Window.getWindows().stream().filter(w -> w instanceof Stage).findFirst().orElseThrow();
        Path closed = writePng(page.getScene().snapshot(null), "agent-status-dropdown-open-page");
        popup.getScene().getRoot().applyCss();
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.web("#b0c0ff"));
        Path list = writePng(popup.getScene().getRoot().snapshot(params, null), "agent-status-dropdown-popup");
        return new Path[] {closed, list};
    }

    /** Like writePng, but composites over an opaque background so a transparent window's extent is visible. */
    static Path writePngOver(WritableImage image, String name, java.awt.Color background) throws IOException {
        BufferedImage source = toBuffered(image);
        BufferedImage flat = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = flat.createGraphics();
        graphics.setColor(background);
        graphics.fillRect(0, 0, flat.getWidth(), flat.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        Files.createDirectories(OUTPUT);
        Path file = OUTPUT.resolve(name + ".png");
        ImageIO.write(flat, "png", file.toFile());
        return file;
    }

    static Path writePng(WritableImage image, String name) throws IOException {
        Files.createDirectories(OUTPUT);
        Path file = OUTPUT.resolve(name + ".png");
        ImageIO.write(toBuffered(image), "png", file.toFile());
        return file;
    }
}
