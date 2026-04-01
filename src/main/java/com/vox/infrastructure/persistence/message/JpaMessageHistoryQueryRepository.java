package com.vox.infrastructure.persistence.message;

import com.chatapp.entity.Message;
import com.chatapp.entity.Session;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaMessageHistoryQueryRepository implements MessageHistoryQueryRepository {

    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;

    @Override
    public Page<Message> findByPeer(Long userId, Long peerId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (peerId == null) {
            throw new IllegalArgumentException("peerId is required when sessionId is absent");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return messageRepository.findConversation(userId, peerId, pageable);
    }

    @Override
    public Page<Message> findBySession(Long userId, Long sessionId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.involves(userId)) {
            throw new IllegalArgumentException("User is not in this session");
        }
        return findByPeer(userId, session.peerIdOf(userId), page, size);
    }
}
