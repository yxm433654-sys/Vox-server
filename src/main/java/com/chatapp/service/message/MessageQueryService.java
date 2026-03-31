package com.chatapp.service.message;

import com.chatapp.dto.MessageDto;
import com.chatapp.dto.MessageHistoryResponse;
import com.chatapp.dto.Pagination;
import com.chatapp.entity.Message;
import com.chatapp.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageQueryService {

    private final MessageRepository messageRepository;
    private final MessageDtoAssembler messageDtoAssembler;

    public List<MessageDto> poll(Long userId, Long lastMessageId) {
        List<Message> messages = lastMessageId == null
                ? messageRepository.findByReceiverIdOrderByIdAsc(userId)
                : messageRepository.findByReceiverIdAndIdGreaterThanOrderByIdAsc(userId, lastMessageId);
        return messageDtoAssembler.toDtos(messages);
    }

    public MessageHistoryResponse history(Long userId, Long peerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Message> result = messageRepository.findConversation(userId, peerId, pageable);

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
