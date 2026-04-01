package com.vox.application.message;

import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.message.MessageCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PollMessagesUseCase {

    private final MessageCommandRepository messageCommandRepository;
    private final MessageViewAssembler messageViewAssembler;

    @Transactional(readOnly = true)
    public List<MessageView> execute(Long userId, Long lastMessageId) {
        List<Message> messages = lastMessageId == null
                ? messageCommandRepository.findInbox(userId)
                : messageCommandRepository.findInboxAfter(userId, lastMessageId);
        return messageViewAssembler.toViews(messages);
    }
}

