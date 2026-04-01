package com.vox.application.message;

import com.vox.application.session.SessionWorkflowService;
import com.vox.infrastructure.persistence.message.MessageCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClearConversationUseCase {

    private final MessageCommandRepository messageCommandRepository;
    private final SessionWorkflowService sessionWorkflowService;

    @Transactional
    public int execute(Long userId, Long peerId) {
        if (userId == null || peerId == null) {
            throw new IllegalArgumentException("userId and peerId are required");
        }
        if (Objects.equals(userId, peerId)) {
            throw new IllegalArgumentException("peerId must be different from userId");
        }
        int cleared = messageCommandRepository.deleteConversation(userId, peerId);
        sessionWorkflowService.clearConversation(userId, peerId);
        return cleared;
    }
}
