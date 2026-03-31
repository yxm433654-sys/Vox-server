package com.chatapp.service.message;

import com.chatapp.dto.MessageDto;
import com.chatapp.entity.Message;
import com.chatapp.repository.MessageRepository;
import com.chatapp.websocket.WebSocketPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MessageRefreshService {

    private final MessageRepository messageRepository;
    private final MessageDtoAssembler messageDtoAssembler;
    private final WebSocketPushService webSocketPushService;

    public void pushMediaRefreshByResourceId(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        List<Message> messages = messageRepository.findByResourceIdOrVideoResourceId(resourceId, resourceId);
        if (messages.isEmpty()) {
            return;
        }

        for (MessageDto dto : messageDtoAssembler.toDtos(messages)) {
            webSocketPushService.pushNewMessage(dto.getSenderId(), dto);
            if (!Objects.equals(dto.getSenderId(), dto.getReceiverId())) {
                webSocketPushService.pushNewMessage(dto.getReceiverId(), dto);
            }
        }
    }
}
