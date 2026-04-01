package com.vox.infrastructure.persistence.user;

import com.vox.infrastructure.persistence.entity.User;

import java.util.Optional;

public interface UserAccountRepository {
    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    User save(User user);
}

