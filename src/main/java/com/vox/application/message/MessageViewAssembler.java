package com.vox.application.message;

import com.vox.infrastructure.media.MediaAssembler;
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
public class MessageViewAssembler {

    private final FileResourceRepository fileResourceRepository;
    private final MediaAssembler mediaAssembler;

    public MessageView toView(Message message) {
        return toViews(List.of(message)).get(0);
    }

    public List<MessageView> toViews(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Set<Long> resourceIds = new HashSet<>();
        for (Message message : messages) {
            if (message.getResourceId() != null) resourceIds.add(message.getResourceId());
            if (message.getVideoResourceId() != null) resourceIds.add(message.getVideoResourceId());
        }

        Map<Long, FileResource> resourcesById = resourceIds.isEmpty()
                ? Map.of()
                : fileResourceRepository.findAllById(resourceIds).stream()
                        .collect(Collectors.toMap(FileResource::getId, Function.identity()));

        List<MessageView> views = new ArrayList<>(messages.size());
        for (Message message : messages) {
            FileResource coverResource = message.getResourceId() == null
                    ? null : resourcesById.get(message.getResourceId());
            FileResource playResource = message.getVideoResourceId() == null
                    ? null : resourcesById.get(message.getVideoResourceId());

            MessageView view = new MessageView();
            view.setId(message.getId());
            view.setSenderId(message.getSenderId());
            view.setReceiverId(message.getReceiverId());
            view.setType(message.getType().name());
            view.setContent(message.getContent());
            view.setResourceId(message.getResourceId());
            view.setVideoResourceId(message.getVideoResourceId());
            view.setStatus(message.getStatus());
            view.setCreatedAt(message.getCreateTime());

            MediaAssembler.MediaInfo info = mediaAssembler.assemble(message, coverResource, playResource);
            view.setMedia(toMediaView(info));
            view.setCoverUrl(info == null ? null : info.getCoverUrl());
            view.setVideoUrl(info == null ? null : info.getPlayUrl());
            views.add(view);
        }
        return views;
    }

    private MessageMediaView toMediaView(MediaAssembler.MediaInfo info) {
        if (info == null) return null;
        MessageMediaView v = new MessageMediaView();
        v.setMediaKind(info.getMediaKind());
        v.setProcessingStatus(info.getProcessingStatus());
        v.setResourceId(info.getResourceId());
        v.setCoverResourceId(info.getCoverResourceId());
        v.setPlayResourceId(info.getPlayResourceId());
        v.setCoverUrl(info.getCoverUrl());
        v.setPlayUrl(info.getPlayUrl());
        v.setWidth(info.getWidth());
        v.setHeight(info.getHeight());
        v.setDuration(info.getDuration());
        v.setAspectRatio(info.getAspectRatio());
        v.setSourceType(info.getSourceType());
        return v;
    }
}
