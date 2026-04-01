package com.vox.infrastructure.message;

import com.vox.infrastructure.message.dto.MessageDto;
import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MessageDtoAssembler {

    private final FileResourceRepository fileResourceRepository;
    private final MessageMediaAssembler messageMediaAssembler;

    public MessageDto toDto(Message message) {
        return toDtos(List.of(message)).get(0);
    }

    public List<MessageDto> toDtos(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Set<Long> resourceIds = new HashSet<>();
        for (Message message : messages) {
            if (message.getResourceId() != null) {
                resourceIds.add(message.getResourceId());
            }
            if (message.getVideoResourceId() != null) {
                resourceIds.add(message.getVideoResourceId());
            }
        }

        Map<Long, FileResource> resourcesById = resourceIds.isEmpty()
                ? Map.of()
                : fileResourceRepository.findAllById(resourceIds).stream()
                .collect(Collectors.toMap(FileResource::getId, Function.identity()));

        List<MessageDto> dtos = new ArrayList<>(messages.size());
        for (Message message : messages) {
            MessageDto dto = new MessageDto();
            dto.setId(message.getId());
            dto.setSenderId(message.getSenderId());
            dto.setReceiverId(message.getReceiverId());
            dto.setType(message.getType().name());
            dto.setContent(message.getContent());
            dto.setResourceId(message.getResourceId());
            dto.setVideoResourceId(message.getVideoResourceId());
            dto.setStatus(message.getStatus());
            dto.setCreatedAt(message.getCreateTime());

            FileResource coverResource = message.getResourceId() == null
                    ? null
                    : resourcesById.get(message.getResourceId());
            FileResource playResource = message.getVideoResourceId() == null
                    ? null
                    : resourcesById.get(message.getVideoResourceId());

            dto.setMedia(messageMediaAssembler.buildMediaDto(message, coverResource, playResource));
            messageMediaAssembler.applyResolvedUrls(dto, dto.getMedia());
            dtos.add(dto);
        }
        return dtos;
    }
}

