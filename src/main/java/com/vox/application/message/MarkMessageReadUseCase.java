package com.vox.application.message;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.application.session.SessionWorkflowService;
import com.vox.infrastructure.persistence.message.MessageCommandRepository;
import com.vox.infrastructure.realtime.RealtimePushGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarkMessageReadUseCase {

    private final MessageCommandRepository messageCommandRepository;
    private final RealtimePushGateway realtimePushGateway;
    private final SessionWorkflowService sessionWorkflowService;

    @Transactional
    public void execute(Long messageId) {
        Message message = messageCommandRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        message.setStatus("READ");
        message.setReadTime(LocalDateTime.now());
        Message saved = messageCommandRepository.save(message);
        sessionWorkflowService.clearUnreadForPair(saved.getReceiverId(), saved.getSenderId());

        // Notify the original sender that their message was read.
        // Use a dedicated READ_RECEIPT event so the client can update delivery status
        // without confusing it with a new incoming message.
        realtimePushGateway.pushMessageRead(saved.getSenderId(), Map.of(
                "messageId", saved.getId(),
                "readTime", saved.getReadTime().toString()
        ));
    }
}
