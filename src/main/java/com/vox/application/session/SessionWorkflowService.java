package com.vox.application.session;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.entity.Session;
import com.vox.infrastructure.persistence.entity.User;
import com.vox.controller.session.SessionResponse;
import com.vox.controller.session.SessionResponseMapper;
import com.vox.infrastructure.persistence.message.MessageLookupRepository;
import com.vox.infrastructure.persistence.session.SessionStateRepository;
import com.vox.infrastructure.persistence.user.UserProfileRepository;
import com.vox.infrastructure.realtime.RealtimePushGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionWorkflowService {

    private final SessionStateRepository sessionStateRepository;
    private final MessageLookupRepository messageLookupRepository;
    private final UserProfileRepository userProfileRepository;
    private final RealtimePushGateway realtimePushGateway;
    private final SessionResponseMapper sessionResponseMapper;
    private final SessionPreviewBuilder sessionPreviewBuilder;

    @Transactional
    public Session updateAfterMessage(Message message) {
        Session session = getOrCreateSession(message.getSenderId(), message.getReceiverId());
        session.setLastMessageId(message.getId());
        session.setLastMessageTime(message.getCreateTime());
        session.incrementUnreadFor(message.getReceiverId());
        Session saved = sessionStateRepository.save(session);
        pushSessionRefresh(saved, message.getSenderId(), message.getReceiverId(), message);
        return saved;
    }

    @Transactional
    public void clearUnreadForPair(Long currentUserId, Long peerId) {
        Session session = findSessionBetween(currentUserId, peerId).orElse(null);
        if (session == null) {
            return;
        }
        session.clearUnreadFor(currentUserId);
        Session saved = sessionStateRepository.save(session);
        pushSessionRefresh(saved, currentUserId, peerId);
    }

    @Transactional
    public void clearConversation(Long userId, Long peerId) {
        Session session = findSessionBetween(userId, peerId).orElse(null);
        if (session == null) {
            return;
        }
        session.setLastMessageId(null);
        session.setLastMessageTime(null);
        session.clearUnreadFor(userId);
        session.clearUnreadFor(peerId);
        sessionStateRepository.delete(session);
        realtimePushGateway.pushSessionListChanged(userId);
        realtimePushGateway.pushSessionListChanged(peerId);
    }

    @Transactional
    public Session getOrCreateSession(Long userId, Long peerId) {
        if (userId == null || peerId == null) {
            throw new IllegalArgumentException("userId and peerId are required");
        }
        if (userId.equals(peerId)) {
            throw new IllegalArgumentException("peerId must be different from userId");
        }

        Long first = Math.min(userId, peerId);
        Long second = Math.max(userId, peerId);
        return sessionStateRepository.findBetweenUsers(first, second)
                .orElseGet(() -> {
                    Session session = new Session();
                    session.setUser1Id(first);
                    session.setUser2Id(second);
                    session.setUnreadCountUser1(0);
                    session.setUnreadCountUser2(0);
                    return sessionStateRepository.save(session);
                });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Session> findSessionBetween(Long userId, Long peerId) {
        if (userId == null || peerId == null) {
            return java.util.Optional.empty();
        }
        Long first = Math.min(userId, peerId);
        Long second = Math.max(userId, peerId);
        return sessionStateRepository.findBetweenUsers(first, second);
    }

    private void pushSessionRefresh(Session session, Long firstUserId, Long secondUserId, Message lastMessage) {
        if (session == null) {
            return;
        }
        pushSessionRefreshForUser(session, firstUserId, lastMessage);
        pushSessionRefreshForUser(session, secondUserId, lastMessage);
    }

    private void pushSessionRefresh(Session session, Long firstUserId, Long secondUserId) {
        pushSessionRefresh(session, firstUserId, secondUserId, null);
    }

    private void pushSessionRefreshForUser(Session session, Long userId, Message lastMessageOverride) {
        if (userId == null || !session.involves(userId)) {
            return;
        }

        Long peerId = session.peerIdOf(userId);
        User peer = userProfileRepository.findById(peerId).orElse(null);
        Message lastMessage = lastMessageOverride;
        if (lastMessage == null && session.getLastMessageId() != null) {
            lastMessage = messageLookupRepository.findById(session.getLastMessageId()).orElse(null);
        }

        SessionResponse response = sessionResponseMapper.toResponse(
                com.vox.domain.session.SessionSummary.builder()
                        .id(session.getId())
                        .peerId(session.peerIdOf(userId))
                        .peerUsername(peer == null ? null : peer.getUsername())
                        .peerAvatarUrl(peer == null ? null : peer.getAvatarUrl())
                        .lastMessageId(session.getLastMessageId())
                        .lastMessageType(lastMessage == null ? null : lastMessage.getType().name())
                        .lastMessagePreview(sessionPreviewBuilder.build(lastMessage))
                        .unreadCount(session.unreadCountFor(userId))
                        .updatedAt(session.getLastMessageTime() != null ? session.getLastMessageTime() : session.getUpdateTime())
                        .build()
        );
        realtimePushGateway.pushSessionUpdated(userId, response);
    }
}

