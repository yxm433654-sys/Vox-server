package com.vox.application.message;

import com.chatapp.dto.MessageDto;
import com.chatapp.entity.Message;
import com.chatapp.repository.MessageRepository;
import com.chatapp.service.message.MessageDtoAssembler;
import com.vox.application.session.SessionWorkflowService;
import com.vox.infrastructure.realtime.RealtimePushGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MarkMessageReadUseCase {

    private final MessageRepository messageRepository;
    private final MessageDtoAssembler messageDtoAssembler;
    private final RealtimePushGateway realtimePushGateway;
    private final SessionWorkflowService sessionWorkflowService;

    @Transactional
    public void execute(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        message.setStatus("READ");
        message.setReadTime(LocalDateTime.now());
        Message saved = messageRepository.save(message);
        sessionWorkflowService.clearUnreadForPair(saved.getReceiverId(), saved.getSenderId());

        MessageDto dto = messageDtoAssembler.toDto(saved);
        realtimePushGateway.pushNewMessage(saved.getSenderId(), dto);
    }
}
