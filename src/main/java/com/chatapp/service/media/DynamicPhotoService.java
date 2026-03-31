package com.chatapp.service.media;

import com.chatapp.service.media.live.LivePhotoExtractor;
import com.chatapp.service.media.live.LivePhotoMetadata;
import com.chatapp.service.media.live.LivePhotoResolver;
import com.chatapp.service.media.motion.MotionPhotoExtractor;
import com.chatapp.service.media.motion.MotionPhotoResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicPhotoService {

    private final LivePhotoResolver livePhotoResolver;
    private final MotionPhotoResolver motionPhotoResolver;
    private final MediaTranscodeService mediaTranscodeService;
    private final MediaProbeService mediaProbeService;
    private final DynamicPhotoResourceService dynamicPhotoResourceService;
    private final ObjectProvider<DynamicPhotoService> selfProvider;

    @Value("${app.temp-dir:tmp/chat-uploads}")
    private String tempDir;

    public void scheduleLivePhotoProcessing(String jpegPath, String movPath, Long coverId, Long videoId) {
        runAfterCommit(() -> selfProvider.getObject().processLivePhoto(jpegPath, movPath, coverId, videoId));
    }

    public void scheduleMotionPhotoProcessing(String sourcePath, Long coverId, Long videoId) {
        runAfterCommit(() -> selfProvider.getObject().processMotionPhoto(sourcePath, coverId, videoId));
    }

    @Async
    public void processLivePhoto(String jpegPath, String movPath, Long coverId, Long videoId) {
        File jpegFile = new File(jpegPath);
        File movFile = new File(movPath);
        File transcodedVideo = new File(ensureTempDir(), UUID.randomUUID() + "_transcoded.mp4");

        try {
            LivePhotoExtractor extractor = livePhotoResolver.resolve(jpegFile, movFile);
            LivePhotoMetadata metadata = extractor.extract(jpegFile, movFile);
            mediaTranscodeService.transcodeToMp4(movFile, transcodedVideo);

            int[] dimensions = null;
            try {
                dimensions = mediaProbeService.getVideoDimensions(transcodedVideo);
            } catch (Exception ignored) {
            }

            dynamicPhotoResourceService.saveProcessedDynamicPhoto(
                    coverId,
                    videoId,
                    jpegFile,
                    transcodedVideo,
                    metadata.getDuration(),
                    dimensions
            );
        } catch (Exception e) {
            log.warn("Failed to process live photo: coverId={}, videoId={}", coverId, videoId, e);
        } finally {
            safeDelete(jpegFile);
            safeDelete(movFile);
            safeDelete(transcodedVideo);
        }
    }

    @Async
    public void processMotionPhoto(String sourcePath, Long coverId, Long videoId) {
        File motionPhotoFile = new File(sourcePath);
        File outputDir = ensureTempDir();
        File coverFile = null;
        File videoFile = null;
        File transcodedVideo = new File(outputDir, UUID.randomUUID() + "_transcoded.mp4");

        try {
            MotionPhotoExtractor extractor = motionPhotoResolver.resolve(motionPhotoFile);
            coverFile = extractor.extractCoverImage(motionPhotoFile, outputDir);
            videoFile = extractor.extractVideo(motionPhotoFile, outputDir);
            mediaTranscodeService.transcodeToMp4(videoFile, transcodedVideo);

            Float duration = null;
            int[] dimensions = null;
            try {
                duration = mediaProbeService.getVideoDuration(transcodedVideo);
            } catch (Exception ignored) {
            }
            try {
                dimensions = mediaProbeService.getVideoDimensions(transcodedVideo);
            } catch (Exception ignored) {
            }

            dynamicPhotoResourceService.saveProcessedDynamicPhoto(
                    coverId,
                    videoId,
                    coverFile,
                    transcodedVideo,
                    duration,
                    dimensions
            );
        } catch (Exception e) {
            log.warn("Failed to process motion photo: coverId={}, videoId={}", coverId, videoId, e);
        } finally {
            safeDelete(motionPhotoFile);
            safeDelete(coverFile);
            safeDelete(videoFile);
            safeDelete(transcodedVideo);
        }
    }

    private void runAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
            return;
        }
        runnable.run();
    }

    private File ensureTempDir() {
        File dir = new File(tempDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), tempDir);
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
