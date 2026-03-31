package com.chatapp.service.message;

import com.chatapp.dto.MessageDto;
import com.chatapp.entity.Message;
import com.chatapp.repository.MessageRepository;
import com.chatapp.websocket.WebSocketPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MessageReadService {

    private final MessageRepository messageRepository;
    private final MessageDtoAssembler messageDtoAssembler;
    private final WebSocketPushService webSocketPushService;

    @Transactional
    public void markRead(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        message.setStatus("READ");
        message.setReadTime(LocalDateTime.now());
        Message saved = messageRepository.save(message);

        MessageDto dto = messageDtoAssembler.toDto(saved);
        webSocketPushService.pushNewMessage(saved.getSenderId(), dto);
    }
}
