package com.vox.infrastructure.persistence.message;

import com.vox.infrastructure.persistence.entity.Message;

import java.util.List;
import java.util.Optional;

public interface MessageCommandRepository {
    Message save(Message message);

    Optional<Message> findById(Long id);

    List<Message> findInbox(Long receiverId);

    List<Message> findInboxAfter(Long receiverId, Long lastMessageId);

    int deleteConversation(Long userId, Long peerId);
}

