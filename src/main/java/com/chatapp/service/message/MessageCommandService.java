package com.chatapp.service.message;

import com.chatapp.dto.MessageDto;
import com.chatapp.dto.MessageSendRequest;
import com.chatapp.dto.MessageSendResponse;
import com.chatapp.entity.Message;
import com.chatapp.repository.MessageRepository;
import com.chatapp.websocket.WebSocketPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MessageCommandService {

    private final MessageRepository messageRepository;
    private final MessageDtoAssembler messageDtoAssembler;
    private final WebSocketPushService webSocketPushService;

    @Transactional
    public MessageSendResponse send(MessageSendRequest request) {
        Message.MessageType type;
        try {
            type = Message.MessageType.valueOf(request.getType());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid message type");
        }

        if (type == Message.MessageType.TEXT
                && (request.getContent() == null || request.getContent().isBlank())) {
            throw new IllegalArgumentException("content is required for TEXT message");
        }

        Message message = new Message();
        message.setSenderId(request.getSenderId());
        message.setReceiverId(request.getReceiverId());
        message.setType(type);
        message.setContent(request.getContent());
        message.setResourceId(request.getResourceId());
        message.setVideoResourceId(request.getVideoResourceId());
        message.setStatus("SENT");

        Message saved = messageRepository.save(message);
        MessageDto dto = messageDtoAssembler.toDto(saved);
        webSocketPushService.pushNewMessage(saved.getReceiverId(), dto);

        MessageSendResponse response = new MessageSendResponse();
        response.setMessageId(saved.getId());
        response.setStatus(saved.getStatus());
        response.setCreatedAt(saved.getCreateTime());
        return response;
    }

    @Transactional
    public int clearConversation(Long userId, Long peerId) {
        if (userId == null || peerId == null) {
            throw new IllegalArgumentException("userId and peerId are required");
        }
        if (Objects.equals(userId, peerId)) {
            throw new IllegalArgumentException("peerId must be different from userId");
        }
        return messageRepository.deleteConversation(userId, peerId);
    }
}
