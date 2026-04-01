package com.vox.infrastructure.persistence.session;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.entity.Session;
import com.vox.infrastructure.persistence.entity.User;
import com.vox.application.session.SessionPreviewBuilder;
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

    private final SessionStateRepository sessionStateRepository;
    private final com.vox.infrastructure.persistence.user.UserProfileRepository userProfileRepository;
    private final com.vox.infrastructure.persistence.message.MessageLookupRepository messageLookupRepository;
    private final SessionPreviewBuilder sessionPreviewBuilder;

    @Override
    public List<SessionSummary> listByUserId(Long userId) {
        List<Session> sessions = sessionStateRepository.findRecentByUserId(userId);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Set<Long> peerIds = sessions.stream()
                .map(session -> session.peerIdOf(userId))
                .collect(Collectors.toSet());
        Map<Long, User> usersById = userProfileRepository.findAllByIds(peerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Set<Long> lastMessageIds = sessions.stream()
                .map(Session::getLastMessageId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, Message> messagesById = messageLookupRepository.findAllByIds(lastMessageIds).stream()
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
                .lastMessagePreview(sessionPreviewBuilder.build(lastMessage))
                .unreadCount(session.unreadCountFor(currentUserId))
                .updatedAt(resolveUpdatedAt(session))
                .build();
    }

    private LocalDateTime resolveUpdatedAt(Session session) {
        return session.getLastMessageTime() != null ? session.getLastMessageTime() : session.getUpdateTime();
    }
}

