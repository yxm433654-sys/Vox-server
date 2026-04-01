package com.vox.infrastructure.persistence.session;

import com.vox.infrastructure.persistence.entity.Session;

import java.util.List;
import java.util.Optional;

public interface SessionStateRepository {
    Session save(Session session);

    void delete(Session session);

    Optional<Session> findById(Long id);

    Optional<Session> findBetweenUsers(Long firstUserId, Long secondUserId);

    List<Session> findRecentByUserId(Long userId);
}

