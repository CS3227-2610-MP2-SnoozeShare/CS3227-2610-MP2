package com.snoozeshare.service.impl;

import java.util.Set;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

public final class AuthorizationService {

    private AuthorizationService() {
    }

    public static void requireRole(Role required, User actor) {
        if (actor == null || actor.role() != required) {
            throw new IllegalStateException("Actor does not have the required role");
        }
    }

    public static void requireAnyRole(Set<Role> required, User actor) {
        if (actor == null || required == null || !required.contains(actor.role())) {
            throw new IllegalStateException("Actor does not have an allowed role");
        }
    }
}
