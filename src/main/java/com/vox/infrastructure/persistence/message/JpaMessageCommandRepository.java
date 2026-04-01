package com.vox.infrastructure.persistence.message;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaMessageCommandRepository implements MessageCommandRepository {

    private final MessageRepository messageRepository;

    @Override
    public Message save(Message message) {
        return messageRepository.save(message);
    }

    @Override
    public Optional<Message> findById(Long id) {
        return messageRepository.findById(id);
    }

    @Override
    public List<Message> findInbox(Long receiverId) {
        return messageRepository.findByReceiverIdOrderByIdAsc(receiverId);
    }

    @Override
    public List<Message> findInboxAfter(Long receiverId, Long lastMessageId) {
        return messageRepository.findByReceiverIdAndIdGreaterThanOrderByIdAsc(receiverId, lastMessageId);
    }

    @Override
    public int deleteConversation(Long userId, Long peerId) {
        return messageRepository.deleteConversation(userId, peerId);
    }
}

