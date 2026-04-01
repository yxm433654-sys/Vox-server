package com.vox.infrastructure.persistence.message;

import com.vox.infrastructure.persistence.entity.Message;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MessageLookupRepository {
    Optional<Message> findById(Long id);

    List<Message> findAllByIds(Set<Long> ids);

    Page<Message> findConversation(Long userId, Long peerId, int page, int size);
}

