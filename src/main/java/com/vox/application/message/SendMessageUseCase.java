package com.vox.application.message;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.application.session.SessionWorkflowService;
import com.vox.infrastructure.persistence.message.MessageCommandRepository;
import com.vox.infrastructure.realtime.RealtimePushGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SendMessageUseCase {

    private final MessageCommandRepository messageCommandRepository;
    private final MessageViewAssembler messageViewAssembler;
    private final RealtimePushGateway realtimePushGateway;
    private final SessionWorkflowService sessionWorkflowService;

    @Transactional
    public SendMessageResult execute(SendMessageCommand request) {
        Message.MessageType type = parseType(request.getType());
        validateRequest(request, type);

        Message message = new Message();
        message.setSenderId(request.getSenderId());
        message.setReceiverId(request.getReceiverId());
        message.setType(type);
        message.setContent(request.getContent());
        message.setResourceId(request.getResourceId());
        message.setVideoResourceId(request.getVideoResourceId());
        message.setStatus("SENT");

        Message saved = messageCommandRepository.save(message);
        sessionWorkflowService.updateAfterMessage(saved);
        MessageView messageView = messageViewAssembler.toView(saved);
        realtimePushGateway.pushNewMessage(saved.getReceiverId(), messageView);

        SendMessageResult response = new SendMessageResult();
        response.setMessageId(saved.getId());
        response.setStatus(saved.getStatus());
        response.setCreatedAt(saved.getCreateTime());
        return response;
    }

    private Message.MessageType parseType(String rawType) {
        try {
            return Message.MessageType.valueOf(rawType);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid message type");
        }
    }

    private void validateRequest(SendMessageCommand request, Message.MessageType type) {
        if (request.getSenderId() == null || request.getReceiverId() == null) {
            throw new IllegalArgumentException("senderId and receiverId are required");
        }
        if (Objects.equals(request.getSenderId(), request.getReceiverId())) {
            throw new IllegalArgumentException("receiverId must be different from senderId");
        }
        if (type == Message.MessageType.TEXT
                && (request.getContent() == null || request.getContent().isBlank())) {
            throw new IllegalArgumentException("content is required for TEXT message");
        }
    }
}

