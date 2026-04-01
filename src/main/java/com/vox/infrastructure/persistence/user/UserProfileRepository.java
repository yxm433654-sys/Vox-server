package com.vox.infrastructure.persistence.user;

import com.vox.infrastructure.persistence.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserProfileRepository {
    Optional<User> findById(Long id);

    List<User> findAllByIds(Set<Long> ids);
}

