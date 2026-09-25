package com.snoozeshare.ui.admin;

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
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

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
}
