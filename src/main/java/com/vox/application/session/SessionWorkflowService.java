package com.vox.application.session;

import com.chatapp.entity.Message;
import com.chatapp.entity.Session;
import com.chatapp.entity.User;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.SessionRepository;
import com.chatapp.repository.UserRepository;
import com.vox.controller.session.SessionResponse;
import com.vox.controller.session.SessionResponseMapper;
import com.vox.infrastructure.realtime.RealtimePushGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionWorkflowService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RealtimePushGateway realtimePushGateway;
    private final SessionResponseMapper sessionResponseMapper;

    @Transactional
    public Session updateAfterMessage(Message message) {
        Session session = getOrCreateSession(message.getSenderId(), message.getReceiverId());
        session.setLastMessageId(message.getId());
        session.setLastMessageTime(message.getCreateTime());
        session.incrementUnreadFor(message.getReceiverId());
        Session saved = sessionRepository.save(session);
        pushSessionRefresh(saved, message.getSenderId(), message.getReceiverId());
        return saved;
    }

    @Transactional
    public void clearUnreadForPair(Long currentUserId, Long peerId) {
        Session session = findSessionBetween(currentUserId, peerId).orElse(null);
        if (session == null) {
            return;
        }
        session.clearUnreadFor(currentUserId);
        Session saved = sessionRepository.save(session);
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
        sessionRepository.delete(session);
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
        return sessionRepository.findByUser1IdAndUser2Id(first, second)
                .orElseGet(() -> {
                    Session session = new Session();
                    session.setUser1Id(first);
                    session.setUser2Id(second);
                    session.setUnreadCountUser1(0);
                    session.setUnreadCountUser2(0);
                    return sessionRepository.save(session);
                });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Session> findSessionBetween(Long userId, Long peerId) {
        if (userId == null || peerId == null) {
            return java.util.Optional.empty();
        }
        Long first = Math.min(userId, peerId);
        Long second = Math.max(userId, peerId);
        return sessionRepository.findByUser1IdAndUser2Id(first, second);
    }

    private void pushSessionRefresh(Session session, Long firstUserId, Long secondUserId) {
        if (session == null) {
            return;
        }
        pushSessionRefreshForUser(session, firstUserId);
        pushSessionRefreshForUser(session, secondUserId);
    }

    private void pushSessionRefreshForUser(Session session, Long userId) {
        if (userId == null || !session.involves(userId)) {
            return;
        }

        Long peerId = session.peerIdOf(userId);
        User peer = userRepository.findById(peerId).orElse(null);
        Message lastMessage = session.getLastMessageId() == null
                ? null
                : messageRepository.findById(session.getLastMessageId()).orElse(null);

        SessionResponse response = sessionResponseMapper.toResponse(
                com.vox.domain.session.SessionSummary.builder()
                        .id(session.getId())
                        .peerId(session.peerIdOf(userId))
                        .peerUsername(peer == null ? null : peer.getUsername())
                        .peerAvatarUrl(peer == null ? null : peer.getAvatarUrl())
                        .lastMessageId(session.getLastMessageId())
                        .lastMessageType(lastMessage == null ? null : lastMessage.getType().name())
                        .lastMessagePreview(buildPreview(lastMessage))
                        .unreadCount(session.unreadCountFor(userId))
                        .updatedAt(session.getLastMessageTime() != null ? session.getLastMessageTime() : session.getUpdateTime())
                        .build()
        );
        realtimePushGateway.pushSessionUpdated(userId, response);
    }

    private String buildPreview(Message message) {
        if (message == null) {
            return "";
        }
        return switch (message.getType()) {
            case TEXT -> message.getContent() == null ? "" : message.getContent();
            case IMAGE -> "[图片]";
            case VIDEO -> "[视频]";
            case DYNAMIC_PHOTO -> "[动态图片]";
            case FILE -> "[文件]";
        };
    }
}
