package com.snoozeshare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.domain.model.User;

public interface UserRepository {
    Optional<User> findById(UUID userId);

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    User save(User user);
}
