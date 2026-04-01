package com.vox.application.message;

import com.chatapp.dto.MessageDto;
import com.chatapp.entity.Message;
import com.chatapp.repository.MessageRepository;
import com.chatapp.service.message.MessageDtoAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PollMessagesUseCase {

    private final MessageRepository messageRepository;
    private final MessageDtoAssembler messageDtoAssembler;

    @Transactional(readOnly = true)
    public List<MessageDto> execute(Long userId, Long lastMessageId) {
        List<Message> messages = lastMessageId == null
                ? messageRepository.findByReceiverIdOrderByIdAsc(userId)
                : messageRepository.findByReceiverIdAndIdGreaterThanOrderByIdAsc(userId, lastMessageId);
        return messageDtoAssembler.toDtos(messages);
    }
}
