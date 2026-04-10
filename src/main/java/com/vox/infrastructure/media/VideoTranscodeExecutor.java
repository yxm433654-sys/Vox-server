package com.vox.infrastructure.media;

import com.vox.infrastructure.message.MessageRefreshService;
import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import com.vox.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.UUID;

/**
 * Executes video transcoding asynchronously so the upload HTTP response
 * can return immediately with a PROCESSING status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTranscodeExecutor {

    private final FileResourceRepository fileResourceRepository;
    private final FileStorageService fileStorageService;
    private final MediaTranscodeService mediaTranscodeService;
    private final MediaProbeService mediaProbeService;
    private final VideoCoverWorkflowService videoCoverWorkflowService;
    private final MessageRefreshService messageRefreshService;

    @Async("taskExecutor")
    public void transcodeAndUpload(Long resourceId, String tempFilePath,
                                   String originalName, Long userId) {
        File tempFile = new File(tempFilePath);
        File transcodedVideo = null;
        try {
            File dir = fileStorageService.ensureTempDir();
            transcodedVideo = new File(dir, UUID.randomUUID() + "_transcoded_async.mp4");
            mediaTranscodeService.transcodeToMp4(tempFile, transcodedVideo);

            FileResource resource = fileResourceRepository.findById(resourceId).orElse(null);
            if (resource == null) {
                log.warn("Resource {} not found after transcode, skipping", resourceId);
                return;
            }

            // Upload transcoded file to MinIO
            String objectName = resource.getStoragePath();
            fileStorageService.uploadToMinio(transcodedVideo, objectName, "video/mp4");

            // Update resource metadata
            resource.setFileSize(transcodedVideo.length());
            resource.setMimeType("video/mp4");
            resource.setSourceType("TranscodedToMp4");
            fillVideoMetadata(resource, transcodedVideo);
            FileResource saved = fileResourceRepository.save(resource);

            // Schedule cover generation
            FileResource coverResource = videoCoverWorkflowService.createPlaceholderAndSchedule(
                    saved, originalName, userId);

            // Notify clients
            messageRefreshService.pushMediaRefreshByResourceId(saved.getId());

            log.info("Async transcode completed: resourceId={}, size={} bytes",
                    resourceId, transcodedVideo.length());
        } catch (Exception e) {
            log.error("Async video transcode failed: resourceId={}", resourceId, e);
            try {
                FileResource resource = fileResourceRepository.findById(resourceId).orElse(null);
                if (resource != null) {
                    resource.setSourceType("TranscodeFailed");
                    fileResourceRepository.save(resource);
                    messageRefreshService.pushMediaRefreshByResourceId(resource.getId());
                }
            } catch (Exception ignored) {}
        } finally {
            safeDelete(transcodedVideo);
            safeDelete(tempFile);
        }
    }

    private void fillVideoMetadata(FileResource resource, File videoFile) {
        try {
            resource.setDuration(mediaProbeService.getVideoDuration(videoFile));
        } catch (Exception ignored) {}
        try {
            int[] wh = mediaProbeService.getVideoDimensions(videoFile);
            if (wh != null && wh.length == 2) {
                resource.setWidth(wh[0]);
                resource.setHeight(wh[1]);
            }
        } catch (Exception ignored) {}
    }

    private static void safeDelete(File file) {
        try { if (file != null && file.exists()) file.delete(); } catch (Exception ignored) {}
    }
}
