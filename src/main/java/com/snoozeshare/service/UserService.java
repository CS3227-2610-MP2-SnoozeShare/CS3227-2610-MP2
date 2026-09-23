package com.snoozeshare.service;

import java.util.List;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

public interface UserService {
    User register(String displayName, String email, Role role, String registrationCode);

    List<User> listByRole(Role role);

    User suspend(UUID userId, UUID agentId);
}
