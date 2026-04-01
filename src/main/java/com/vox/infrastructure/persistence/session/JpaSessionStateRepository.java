package com.vox.infrastructure.persistence.session;

import com.vox.infrastructure.persistence.entity.Session;
import com.vox.infrastructure.persistence.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaSessionStateRepository implements SessionStateRepository {

    private final SessionRepository sessionRepository;

    @Override
    public Session save(Session session) {
        return sessionRepository.save(session);
    }

    @Override
    public void delete(Session session) {
        sessionRepository.delete(session);
    }

    @Override
    public Optional<Session> findById(Long id) {
        return sessionRepository.findById(id);
    }

    @Override
    public Optional<Session> findBetweenUsers(Long firstUserId, Long secondUserId) {
        return sessionRepository.findByUser1IdAndUser2Id(firstUserId, secondUserId);
    }

    @Override
    public List<Session> findRecentByUserId(Long userId) {
        return sessionRepository.findByUserIdOrderByRecent(userId);
    }
}

