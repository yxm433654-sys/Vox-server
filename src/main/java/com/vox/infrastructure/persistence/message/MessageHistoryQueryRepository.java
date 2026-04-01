package com.vox.infrastructure.persistence.message;

import com.chatapp.entity.Message;
import org.springframework.data.domain.Page;

public interface MessageHistoryQueryRepository {
    Page<Message> findByPeer(Long userId, Long peerId, int page, int size);

    Page<Message> findBySession(Long userId, Long sessionId, int page, int size);
}
