package com.vox.infrastructure.persistence.message;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JpaMessageLookupRepository implements MessageLookupRepository {

    private final MessageRepository messageRepository;

    @Override
    public Optional<Message> findById(Long id) {
        return messageRepository.findById(id);
    }

    @Override
    public List<Message> findAllByIds(Set<Long> ids) {
        return messageRepository.findAllById(ids);
    }

    @Override
    public Page<Message> findConversation(Long userId, Long peerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return messageRepository.findConversation(userId, peerId, pageable);
    }
}

