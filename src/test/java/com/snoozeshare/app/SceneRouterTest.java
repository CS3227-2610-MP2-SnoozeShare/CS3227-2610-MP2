package com.snoozeshare.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
