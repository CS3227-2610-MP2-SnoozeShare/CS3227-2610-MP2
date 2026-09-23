package com.snoozeshare.session;

import java.util.Optional;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

public interface SessionContext {
    Optional<User> currentUser();

    Role currentRole();

    void loginAs(User user);

    void logout();
}
