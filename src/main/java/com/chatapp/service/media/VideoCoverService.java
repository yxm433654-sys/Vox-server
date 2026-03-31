package com.chatapp.service.media;

import com.chatapp.entity.FileResource;
import com.chatapp.repository.FileResourceRepository;
import com.chatapp.service.message.MessageRefreshService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCoverService {

    private final MinioClient minioClient;
    private final FileResourceRepository fileResourceRepository;
    private final MediaTranscodeService mediaTranscodeService;
    private final MediaProbeService mediaProbeService;
    private final MessageRefreshService messageRefreshService;
    private final ObjectProvider<VideoCoverService> selfProvider;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${app.temp-dir:tmp/chat-uploads}")
    private String tempDir;

    public FileResource createPlaceholderAndSchedule(FileResource videoResource, String originalName, Long userId) {
        if (videoResource == null || videoResource.getId() == null || videoResource.getStoragePath() == null) {
            return null;
        }

        String coverObjectName = "video/cover_" + UUID.randomUUID() + ".jpg";

        FileResource cover = new FileResource();
        cover.setOriginalName(originalName);
        cover.setStoragePath(coverObjectName);
        cover.setFileType(FileResource.FileType.IMAGE);
        cover.setSourceType("VideoCoverPending");
        cover.setMimeType("image/jpeg");
        cover.setFileSize(0L);
        cover.setUploaderId(userId);
        cover.setRelatedFileId(videoResource.getId());
        FileResource savedCover = fileResourceRepository.save(cover);

        videoResource.setRelatedFileId(savedCover.getId());
        fileResourceRepository.save(videoResource);

        Runnable trigger = () -> selfProvider.getObject().regenerateCover(videoResource.getId(), savedCover.getId());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
        } else {
            trigger.run();
        }

        return savedCover;
    }

    @Async
    public void regenerateCover(Long videoId, Long coverId) {
        FileResource video = fileResourceRepository.findById(videoId).orElse(null);
        FileResource cover = fileResourceRepository.findById(coverId).orElse(null);
        if (video == null || cover == null || video.getStoragePath() == null || cover.getStoragePath() == null) {
            return;
        }

        File dir = ensureTempDir();
        File tempVideo = new File(dir, UUID.randomUUID() + "_video");
        File outCover = new File(dir, UUID.randomUUID() + "_cover.jpg");

        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(video.getStoragePath())
                        .build()
        ); FileOutputStream fos = new FileOutputStream(tempVideo)) {
            in.transferTo(fos);
        } catch (Exception e) {
            safeDelete(tempVideo);
            safeDelete(outCover);
            log.warn("Failed to download video for cover generation: videoId={}", videoId, e);
            return;
        }

        try {
            extractCoverWithFallbacks(tempVideo, outCover);

            if (!outCover.exists() || outCover.length() <= 0) {
                markCoverFailed(cover);
                return;
            }

            try (InputStream fis = new java.io.FileInputStream(outCover)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(cover.getStoragePath())
                                .stream(fis, outCover.length(), -1)
                                .contentType("image/jpeg")
                                .build()
                );
            }

            cover.setFileSize(outCover.length());
            cover.setMimeType("image/jpeg");
            cover.setSourceType("VideoCover");
            FileResource saved = fileResourceRepository.save(cover);
            messageRefreshService.pushMediaRefreshByResourceId(saved.getId());
        } catch (Exception e) {
            markCoverFailed(cover);
            log.warn("Failed to generate/upload video cover: videoId={}, coverId={}", videoId, coverId, e);
        } finally {
            safeDelete(tempVideo);
            safeDelete(outCover);
        }
    }

    private void extractCoverWithFallbacks(File tempVideo, File outCover) throws Exception {
        float duration = 0f;
        try {
            duration = mediaProbeService.getVideoDuration(tempVideo);
        } catch (Exception ignored) {
        }

        Set<Float> candidates = new LinkedHashSet<>();
        candidates.add(0f);
        candidates.add(0.08f);
        candidates.add(0.35f);
        candidates.add(0.75f);
        if (duration > 0f) {
            candidates.add(Math.min(duration * 0.12f, 1.2f));
            candidates.add(Math.min(duration * 0.25f, 2.5f));
            candidates.add(Math.max(0f, duration / 2f));
            candidates.add(Math.max(0f, duration - 0.1f));
        }

        Exception lastError = null;
        for (float candidate : candidates) {
            safeDelete(outCover);
            try {
                mediaTranscodeService.extractCoverFrame(tempVideo, outCover, candidate);
                if (outCover.exists() && outCover.length() > 0) {
                    return;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException("No usable cover frame extracted");
    }

    private void markCoverFailed(FileResource cover) {
        try {
            cover.setSourceType("VideoCoverFailed");
            cover.setFileSize(0L);
            FileResource saved = fileResourceRepository.save(cover);
            messageRefreshService.pushMediaRefreshByResourceId(saved.getId());
        } catch (Exception ignored) {
        }
    }

    private File ensureTempDir() {
        String configured = tempDir;
        if (configured == null || configured.isBlank()) {
            configured = "tmp/chat-uploads";
        }

        File dir = new File(configured);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), configured);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static void safeDelete(File file) {
        try {
            if (file != null) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }
}
