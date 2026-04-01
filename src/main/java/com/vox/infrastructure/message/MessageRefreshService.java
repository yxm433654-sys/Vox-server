package com.vox.infrastructure.message;

import com.vox.infrastructure.message.dto.MessageDto;
import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.repository.MessageRepository;
import com.vox.infrastructure.realtime.RealtimePushGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MessageRefreshService {

    private final MessageRepository messageRepository;
    private final MessageDtoAssembler messageDtoAssembler;
    private final RealtimePushGateway realtimePushGateway;

    public void pushMediaRefreshByResourceId(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        List<Message> messages = messageRepository.findByResourceIdOrVideoResourceId(resourceId, resourceId);
        if (messages.isEmpty()) {
            return;
        }

        for (MessageDto dto : messageDtoAssembler.toDtos(messages)) {
            realtimePushGateway.pushNewMessage(dto.getSenderId(), dto);
            if (!Objects.equals(dto.getSenderId(), dto.getReceiverId())) {
                realtimePushGateway.pushNewMessage(dto.getReceiverId(), dto);
            }
        }
    }
}

