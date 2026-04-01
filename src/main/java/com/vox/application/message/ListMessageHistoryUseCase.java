package com.vox.application.message;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.message.MessageHistoryQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListMessageHistoryUseCase {

    private final MessageHistoryQueryRepository messageHistoryQueryRepository;
    private final MessageViewAssembler messageViewAssembler;

    @Transactional(readOnly = true)
    public MessageHistoryResult byPeer(Long userId, Long peerId, int page, int size) {
        Page<Message> result = messageHistoryQueryRepository.findByPeer(userId, peerId, page, size);
        return toResponse(result, page, size);
    }

    @Transactional(readOnly = true)
    public MessageHistoryResult bySession(Long userId, Long sessionId, int page, int size) {
        Page<Message> result = messageHistoryQueryRepository.findBySession(userId, sessionId, page, size);
        return toResponse(result, page, size);
    }

    private MessageHistoryResult toResponse(Page<Message> result, int page, int size) {
        MessageHistoryResult response = new MessageHistoryResult();
        response.setData(messageViewAssembler.toViews(result.getContent()));

        MessagePageResult pagination = new MessagePageResult();
        pagination.setPage(page);
        pagination.setSize(size);
        pagination.setTotal(result.getTotalElements());
        response.setPagination(pagination);
        return response;
    }
}

