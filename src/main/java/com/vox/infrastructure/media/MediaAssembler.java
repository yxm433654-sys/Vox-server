package com.vox.infrastructure.media;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.entity.Message;
import com.vox.infrastructure.storage.StorageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for assembling media metadata from FileResource pairs.
 *
 * Previously duplicated between MessageViewAssembler (application layer) and
 * MessageMediaAssembler (infrastructure layer). Both consumed the same inputs and
 * produced structurally identical outputs; only the DTO type differed.
 *
 * Callers receive a MediaInfo record and map it to whatever DTO they need.
 */
@Component
@RequiredArgsConstructor
public class MediaAssembler {

    private final StorageUrlResolver storageUrlResolver;

    /**
     * Builds a platform-neutral MediaInfo from a Message and its resolved resources.
     * Returns null for TEXT messages.
     */
    public MediaInfo assemble(Message message, FileResource coverResource, FileResource playResource) {
        if (message.getType() == Message.MessageType.TEXT) {
            return null;
        }

        String coverUrl = resolveClientUrl(coverResource, true);
        String playUrl  = resolveClientUrl(playResource, false);

        return switch (message.getType()) {
            case IMAGE -> assembleImage(message, coverResource, coverUrl, playUrl);
            case VIDEO -> assembleVideo(message, coverResource, playResource, coverUrl, playUrl);
            case FILE  -> assembleFile(message, coverResource, coverUrl, playUrl);
            case DYNAMIC_PHOTO -> assembleDynamic(message, coverResource, playResource, coverUrl, playUrl);
            default -> null;
        };
    }

    // ── private builders ─────────────────────────────────────────────────────

    private MediaInfo assembleImage(
            Message message, FileResource image, String coverUrl, String playUrl) {
        String imagePlayUrl = resolveClientUrl(image, false);
        return MediaInfo.builder()
                .mediaKind("IMAGE")
                .processingStatus(image == null ? "FAILED" : "READY")
                .resourceId(message.getResourceId())
                .coverResourceId(message.getResourceId())
                .playResourceId(message.getResourceId())
                .coverUrl(coverUrl)
                .playUrl(imagePlayUrl)
                .width(image == null ? null : image.getWidth())
                .height(image == null ? null : image.getHeight())
                .aspectRatio(computeAspectRatio(
                        image == null ? null : image.getWidth(),
                        image == null ? null : image.getHeight()))
                .sourceType(image == null ? null : image.getSourceType())
                .build();
    }

    private MediaInfo assembleVideo(
            Message message, FileResource cover, FileResource play,
            String coverUrl, String playUrl) {
        return MediaInfo.builder()
                .mediaKind("VIDEO")
                .processingStatus(resolveVideoStatus(cover, play))
                .resourceId(message.getResourceId())
                .coverResourceId(message.getResourceId())
                .playResourceId(message.getVideoResourceId())
                .coverUrl(coverUrl)
                .playUrl(playUrl)
                .width(play == null ? null : play.getWidth())
                .height(play == null ? null : play.getHeight())
                .duration(play == null ? null : play.getDuration())
                .aspectRatio(computeAspectRatio(
                        play == null ? null : play.getWidth(),
                        play == null ? null : play.getHeight()))
                .sourceType(play != null ? play.getSourceType()
                        : (cover == null ? null : cover.getSourceType()))
                .build();
    }

    private MediaInfo assembleDynamic(
            Message message, FileResource cover, FileResource play,
            String coverUrl, String playUrl) {
        return MediaInfo.builder()
                .mediaKind("DYNAMIC_PHOTO")
                .processingStatus(resolveDynamicStatus(cover, play))
                .resourceId(message.getResourceId())
                .coverResourceId(message.getResourceId())
                .playResourceId(message.getVideoResourceId())
                .coverUrl(coverUrl)
                .playUrl(playUrl)
                .width(play == null ? null : play.getWidth())
                .height(play == null ? null : play.getHeight())
                .duration(play == null ? null : play.getDuration())
                .aspectRatio(computeAspectRatio(
                        play == null ? null : play.getWidth(),
                        play == null ? null : play.getHeight()))
                .sourceType(play != null ? play.getSourceType()
                        : (cover == null ? null : cover.getSourceType()))
                .build();
    }

    private MediaInfo assembleFile(
            Message message, FileResource file, String coverUrl, String playUrl) {
        String filePlayUrl = resolveClientUrl(file, false);
        return MediaInfo.builder()
                .mediaKind("FILE")
                .processingStatus(file == null ? "FAILED" : "READY")
                .resourceId(message.getResourceId())
                .coverResourceId(message.getResourceId())
                .playResourceId(message.getResourceId())
                .coverUrl(coverUrl)
                .playUrl(filePlayUrl)
                .sourceType(file == null ? null : file.getMimeType())
                .duration(file == null || file.getFileSize() == null
                        ? null : file.getFileSize().floatValue())
                .build();
    }

    // ── URL resolution ────────────────────────────────────────────────────────

    private String resolveClientUrl(FileResource resource, boolean skipPendingVideoCover) {
        if (resource == null) return null;
        if (isPendingResource(resource)) return null;
        if (skipPendingVideoCover && "VideoCoverPending".equals(resource.getSourceType())) {
            return null;
        }
        return storageUrlResolver.toClientUrl(resource.getId(), resource.getStoragePath());
    }

    // ── status helpers ────────────────────────────────────────────────────────

    private String resolveVideoStatus(FileResource cover, FileResource play) {
        if (play == null) return "FAILED";
        if (cover == null || "VideoCoverPending".equals(cover.getSourceType())) {
            return "PROCESSING";
        }
        return "READY";
    }

    private String resolveDynamicStatus(FileResource cover, FileResource play) {
        if (cover == null || play == null) return "PROCESSING";
        if (isPendingResource(cover) || isPendingResource(play)) return "PROCESSING";
        return "READY";
    }

    private boolean isPendingResource(FileResource resource) {
        return resource.getFileSize() == null || resource.getFileSize() <= 0;
    }

    private Float computeAspectRatio(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) return null;
        return width / (float) height;
    }

    // ── Value object ──────────────────────────────────────────────────────────

    /**
     * Platform-neutral media descriptor. Callers copy fields into their own DTO.
     */
    @lombok.Builder
    @lombok.Value
    public static class MediaInfo {
        String mediaKind;
        String processingStatus;
        Long resourceId;
        Long coverResourceId;
        Long playResourceId;
        String coverUrl;
        String playUrl;
        Integer width;
        Integer height;
        Float duration;
        Float aspectRatio;
        String sourceType;
    }
}
