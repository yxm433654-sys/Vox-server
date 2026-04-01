package com.vox.infrastructure.persistence.message;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.entity.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaMessageHistoryQueryRepository implements MessageHistoryQueryRepository {

    private final MessageLookupRepository messageLookupRepository;
    private final com.vox.infrastructure.persistence.session.SessionStateRepository sessionStateRepository;

    @Override
    public Page<Message> findByPeer(Long userId, Long peerId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (peerId == null) {
            throw new IllegalArgumentException("peerId is required when sessionId is absent");
        }
        return messageLookupRepository.findConversation(userId, peerId, page, size);
    }

    @Override
    public Page<Message> findBySession(Long userId, Long sessionId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }

        Session session = sessionStateRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.involves(userId)) {
            throw new IllegalArgumentException("User is not in this session");
        }
        return findByPeer(userId, session.peerIdOf(userId), page, size);
    }
}

