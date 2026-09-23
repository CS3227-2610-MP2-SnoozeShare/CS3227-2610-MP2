package com.snoozeshare.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.snoozeshare.domain.enums.Role;

class SceneRouterTest {

    @Test
    void routesUnauthenticatedAndEachRoleToItsRootScene() {
        SceneRouter router = new SceneRouter();

        assertEquals(SceneRouter.AUTH_SCENE, router.routeFor(null));
        assertEquals(SceneRouter.GUEST_SCENE, router.routeFor(Role.GUEST));
        assertEquals(SceneRouter.HOST_SCENE, router.routeFor(Role.HOST));
        assertEquals(SceneRouter.ADMIN_SCENE, router.routeFor(Role.AGENT));
    }

    @Test
    void exposesSupportAgentAsTheUserFacingAgentLabel() {
        assertEquals("Support Agent", SceneRouter.displayName(Role.AGENT));
    }

    @Test
    void mainUsesRouterToReplaceAuthSceneAfterAuthentication() throws Exception {
        String main = Files.readString(Path.of("src/main/java/com/snoozeshare/app/Main.java"));

        assertTrue(main.contains("context.sceneRouter().show(primaryStage, context)"));
    }

    @Test
    void shellLogoutIsWiredToReturnToAuthentication() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/ui/common/NavShellController.java"));
        String router = Files.readString(Path.of(
                "src/main/java/com/snoozeshare/app/SceneRouter.java"));

        assertTrue(controller.contains("onLoggedOut.run()"));
        assertTrue(router.contains("setOnLoggedOut"));
    }
}
