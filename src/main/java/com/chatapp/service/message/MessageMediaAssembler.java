package com.chatapp.service.message;

import com.chatapp.dto.MessageMediaDto;
import com.chatapp.dto.MessageDto;
import com.chatapp.entity.FileResource;
import com.chatapp.entity.Message;
import com.chatapp.service.file.StorageUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageMediaAssembler {

    private final StorageUrlService storageUrlService;

    public MessageMediaDto buildMediaDto(Message message, FileResource coverResource, FileResource playResource) {
        if (message.getType() == Message.MessageType.TEXT) {
            return null;
        }

        MessageMediaDto media = new MessageMediaDto();
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

    public void applyResolvedUrls(MessageDto dto, MessageMediaDto media) {
        if (dto == null) {
            return;
        }
        dto.setCoverUrl(media == null ? null : media.getCoverUrl());
        dto.setVideoUrl(media == null ? null : media.getPlayUrl());
    }

    private void fillImageMedia(MessageMediaDto media, Message message, FileResource image) {
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
            MessageMediaDto media,
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
        return storageUrlService.toClientUrl(resource.getId(), resource.getStoragePath());
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
