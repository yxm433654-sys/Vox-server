package com.vox.infrastructure.persistence.session;

import com.chatapp.entity.Message;
import com.chatapp.entity.Session;
import com.chatapp.entity.User;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.SessionRepository;
import com.chatapp.repository.UserRepository;
import com.vox.domain.session.SessionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class JpaSessionSummaryQueryRepository implements SessionSummaryQueryRepository {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    @Override
    public List<SessionSummary> listByUserId(Long userId) {
        List<Session> sessions = sessionRepository.findByUserIdOrderByRecent(userId);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Set<Long> peerIds = sessions.stream()
                .map(session -> session.peerIdOf(userId))
                .collect(Collectors.toSet());
        Map<Long, User> usersById = userRepository.findAllById(peerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Set<Long> lastMessageIds = sessions.stream()
                .map(Session::getLastMessageId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, Message> messagesById = messageRepository.findAllById(lastMessageIds).stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));

        return sessions.stream()
                .map(session -> toSummary(
                        session,
                        userId,
                        usersById.get(session.peerIdOf(userId)),
                        messagesById.get(session.getLastMessageId())
                ))
                .sorted(Comparator.comparing(
                        SessionSummary::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    private SessionSummary toSummary(
            Session session,
            Long currentUserId,
            User peer,
            Message lastMessage
    ) {
        return SessionSummary.builder()
                .id(session.getId())
                .peerId(session.peerIdOf(currentUserId))
                .peerUsername(peer == null ? null : peer.getUsername())
                .peerAvatarUrl(peer == null ? null : peer.getAvatarUrl())
                .lastMessageId(session.getLastMessageId())
                .lastMessageType(lastMessage == null ? null : lastMessage.getType().name())
                .lastMessagePreview(buildPreview(lastMessage))
                .unreadCount(session.unreadCountFor(currentUserId))
                .updatedAt(resolveUpdatedAt(session))
                .build();
    }

    private LocalDateTime resolveUpdatedAt(Session session) {
        return session.getLastMessageTime() != null ? session.getLastMessageTime() : session.getUpdateTime();
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
