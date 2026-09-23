package com.snoozeshare.session;

import java.util.Optional;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

public final class MockSessionContext implements SessionContext {

    private User currentUser;

    @Override
    public Optional<User> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    @Override
    public Role currentRole() {
        return currentUser == null ? null : currentUser.role();
    }

    @Override
    public void loginAs(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        currentUser = user;
    }

    @Override
    public void logout() {
        currentUser = null;
    }
}
