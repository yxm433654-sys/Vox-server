package com.vox.application.message;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import com.vox.infrastructure.storage.StorageUrlResolver;
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
    private final StorageUrlResolver storageUrlResolver;

    public MessageView toView(Message message) {
        return toViews(List.of(message)).get(0);
    }

    public List<MessageView> toViews(List<Message> messages) {
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

        List<MessageView> views = new ArrayList<>(messages.size());
        for (Message message : messages) {
            FileResource coverResource = message.getResourceId() == null
                    ? null
                    : resourcesById.get(message.getResourceId());
            FileResource playResource = message.getVideoResourceId() == null
                    ? null
                    : resourcesById.get(message.getVideoResourceId());

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

            MessageMediaView media = buildMediaView(message, coverResource, playResource);
            view.setMedia(media);
            view.setCoverUrl(media == null ? null : media.getCoverUrl());
            view.setVideoUrl(media == null ? null : media.getPlayUrl());
            views.add(view);
        }
        return views;
    }

    private MessageMediaView buildMediaView(Message message, FileResource coverResource, FileResource playResource) {
        if (message.getType() == Message.MessageType.TEXT) {
            return null;
        }

        MessageMediaView media = new MessageMediaView();
        media.setCoverUrl(resolveClientUrl(coverResource, true));
        media.setPlayUrl(resolveClientUrl(playResource, false));
        media.setResourceId(message.getResourceId());
        media.setCoverResourceId(message.getResourceId());
        media.setPlayResourceId(message.getVideoResourceId());

        if (message.getType() == Message.MessageType.IMAGE) {
            fillImageMedia(media, message, coverResource);
            return media;
        }

        if (message.getType() == Message.MessageType.VIDEO) {
            fillTimedMedia(
                    media,
                    "VIDEO",
                    resolveVideoStatus(coverResource, playResource),
                    coverResource,
                    playResource
            );
            return media;
        }

        fillTimedMedia(
                media,
                "LIVE_PHOTO",
                resolveDynamicStatus(coverResource, playResource),
                coverResource,
                playResource
        );
        return media;
    }

    private void fillImageMedia(MessageMediaView media, Message message, FileResource image) {
        media.setMediaKind("IMAGE");
        media.setProcessingStatus(image == null ? "FAILED" : "READY");
        media.setPlayResourceId(message.getResourceId());
        media.setPlayUrl(resolveClientUrl(image, false));
        media.setWidth(image == null ? null : image.getWidth());
        media.setHeight(image == null ? null : image.getHeight());
        media.setAspectRatio(computeAspectRatio(
                image == null ? null : image.getWidth(),
                image == null ? null : image.getHeight()));
        media.setSourceType(image == null ? null : image.getSourceType());
    }

    private void fillTimedMedia(
            MessageMediaView media,
            String mediaKind,
            String processingStatus,
            FileResource coverResource,
            FileResource playResource
    ) {
        media.setMediaKind(mediaKind);
        media.setProcessingStatus(processingStatus);
        media.setWidth(playResource == null ? null : playResource.getWidth());
        media.setHeight(playResource == null ? null : playResource.getHeight());
        media.setDuration(playResource == null ? null : playResource.getDuration());
        media.setAspectRatio(computeAspectRatio(
                playResource == null ? null : playResource.getWidth(),
                playResource == null ? null : playResource.getHeight()));
        media.setSourceType(playResource != null ? playResource.getSourceType()
                : (coverResource == null ? null : coverResource.getSourceType()));
    }

    private String resolveClientUrl(FileResource resource, boolean skipPendingVideoCover) {
        if (resource == null) {
            return null;
        }
        if (isPendingResource(resource)) {
            return null;
        }
        if (skipPendingVideoCover && "VideoCoverPending".equals(resource.getSourceType())) {
            return null;
        }
        return storageUrlResolver.toClientUrl(resource.getId(), resource.getStoragePath());
    }

    private String resolveVideoStatus(FileResource coverResource, FileResource playResource) {
        if (playResource == null) {
            return "FAILED";
        }
        if (coverResource == null || "VideoCoverPending".equals(coverResource.getSourceType())) {
            return "PROCESSING";
        }
        return "READY";
    }

    private String resolveDynamicStatus(FileResource coverResource, FileResource playResource) {
        if (coverResource == null || playResource == null) {
            return "PROCESSING";
        }
        if (isPendingResource(coverResource) || isPendingResource(playResource)) {
            return "PROCESSING";
        }
        return "READY";
    }

    private boolean isPendingResource(FileResource resource) {
        return resource.getFileSize() == null || resource.getFileSize() <= 0;
    }

    private Float computeAspectRatio(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null;
        }
        return width / (float) height;
    }
}

