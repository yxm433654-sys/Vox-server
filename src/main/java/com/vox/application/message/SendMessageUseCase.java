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

        SendMessageResult result = new SendMessageResult();
        result.setMessageId(saved.getId());
        result.setStatus(saved.getStatus());
        result.setCreatedAt(saved.getCreateTime());
        return result;
    }

    private Message.MessageType parseType(String rawType) {
        try {
            return Message.MessageType.valueOf(rawType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid message type: " + rawType);
        }
    }

    private void validateRequest(SendMessageCommand request, Message.MessageType type) {
        if (request.getSenderId() == null || request.getReceiverId() == null) {
            throw new IllegalArgumentException("senderId and receiverId are required");
        }
        if (Objects.equals(request.getSenderId(), request.getReceiverId())) {
            throw new IllegalArgumentException("receiverId must be different from senderId");
        }
        // User existence is guaranteed by the JWT token (senderId) and by DB FK constraint
        // (receiverId). Querying the user table on every message send is wasteful and
        // violates the single-responsibility of this use case.
        if (type == Message.MessageType.TEXT
                && (request.getContent() == null || request.getContent().isBlank())) {
            throw new IllegalArgumentException("content is required for TEXT message");
        }
    }
}
