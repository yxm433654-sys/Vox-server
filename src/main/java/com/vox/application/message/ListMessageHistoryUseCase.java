package com.vox.application.message;

import com.chatapp.dto.MessageHistoryResponse;
import com.chatapp.dto.Pagination;
import com.chatapp.entity.Message;
import com.chatapp.service.message.MessageDtoAssembler;
import com.vox.infrastructure.persistence.message.MessageHistoryQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListMessageHistoryUseCase {

    private final MessageHistoryQueryRepository messageHistoryQueryRepository;
    private final MessageDtoAssembler messageDtoAssembler;

    @Transactional(readOnly = true)
    public MessageHistoryResponse byPeer(Long userId, Long peerId, int page, int size) {
        Page<Message> result = messageHistoryQueryRepository.findByPeer(userId, peerId, page, size);
        return toResponse(result, page, size);
    }

    @Transactional(readOnly = true)
    public MessageHistoryResponse bySession(Long userId, Long sessionId, int page, int size) {
        Page<Message> result = messageHistoryQueryRepository.findBySession(userId, sessionId, page, size);
        return toResponse(result, page, size);
    }

    private MessageHistoryResponse toResponse(Page<Message> result, int page, int size) {
        MessageHistoryResponse response = new MessageHistoryResponse();
        response.setData(messageDtoAssembler.toDtos(result.getContent()));

        Pagination pagination = new Pagination();
        pagination.setPage(page);
        pagination.setSize(size);
        pagination.setTotal(result.getTotalElements());
        response.setPagination(pagination);
        return response;
    }
}
