package com.vox.infrastructure.media;

import com.vox.infrastructure.media.live.LivePhotoExtractor;
import com.vox.infrastructure.media.live.LivePhotoMetadata;
import com.vox.infrastructure.media.live.LivePhotoResolver;
import com.vox.infrastructure.media.motion.MotionPhotoContainerParser;
import com.vox.infrastructure.media.motion.MotionPhotoMp4Detector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.UUID;

/**
 * Executes dynamic-photo processing jobs asynchronously.
 *
 * Extracted from DynamicPhotoProcessingService to eliminate the ObjectProvider<Self>
 * self-injection workaround. Spring's @Async proxy requires the method to be called
 * through the bean, not 'this'. The clean solution is a separate class with the async
 * methods, injected by the scheduler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicPhotoProcessingExecutor {

    private final LivePhotoResolver livePhotoResolver;
    private final MotionPhotoContainerParser motionPhotoContainerParser;
    private final MotionPhotoMp4Detector motionPhotoMp4Detector;
    private final MediaTranscodeService mediaTranscodeService;
    private final MediaProbeService mediaProbeService;
    private final DynamicPhotoResourcePersistenceService resourcePersistenceService;

    @Value("${app.temp-dir:tmp/chat-uploads}")
    private String tempDir;

    @Value("${app.dynamic-photo.auto-transcode:true}")
    private boolean autoTranscode;

    @Async("taskExecutor")
    public void processLivePhoto(String imagePath, String videoPath, Long coverId, Long videoId) {
        File imageFile = new File(imagePath);
        File videoFile = new File(videoPath);
        File transcodedVideo = new File(ensureTempDir(), UUID.randomUUID() + "_transcoded.mp4");
        File convertedImage = new File(ensureTempDir(), UUID.randomUUID() + "_converted.jpg");
        File finalVideo = transcodedVideo;
        File finalImage = imageFile;

        try {
            LivePhotoExtractor extractor = livePhotoResolver.resolve(imageFile, videoFile);
            LivePhotoMetadata metadata = extractor.extract(imageFile, videoFile);

            if (isHeic(imageFile)) {
                mediaTranscodeService.convertImageToJpeg(imageFile, convertedImage);
                finalImage = convertedImage;
            }

            if (autoTranscode) {
                mediaTranscodeService.transcodeToMp4(videoFile, transcodedVideo);
            } else {
                finalVideo = videoFile;
            }

            int[] dimensions = null;
            try {
                dimensions = mediaProbeService.getVideoDimensions(finalVideo);
            } catch (Exception ignored) {
            }

            resourcePersistenceService.saveProcessedDynamicPhoto(
                    coverId, videoId, finalImage, finalVideo,
                    metadata.getDuration(), dimensions,
                    metadata.getAssetIdentifier(),
                    metadata.getContentIdentifier());
        } catch (Exception e) {
            log.warn("Failed to process live photo: coverId={}, videoId={}", coverId, videoId, e);
        } finally {
            safeDelete(imageFile);
            safeDelete(videoFile);
            if (finalVideo != videoFile) {
                safeDelete(transcodedVideo);
            }
            if (finalImage != imageFile) {
                safeDelete(convertedImage);
            }
        }
    }

    private boolean isHeic(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".heic") || name.endsWith(".heif");
    }

    @Async("taskExecutor")
    public void processMotionPhoto(String sourcePath, Long coverId, Long videoId) {
        File motionPhotoFile = new File(sourcePath);
        File outputDir = ensureTempDir();
        File coverFile = null;
        File videoFile = null;
        File transcodedVideo = new File(outputDir, UUID.randomUUID() + "_transcoded.mp4");
        File finalVideo = transcodedVideo;

        try {
            MotionPhotoContainerParser.MotionPhotoLayout layout =
                    motionPhotoContainerParser.parse(motionPhotoFile).orElse(null);
            if (layout != null) {
                coverFile = motionPhotoMp4Detector.extractCoverImage(
                        motionPhotoFile, outputDir, layout.primaryLength());
                videoFile = motionPhotoMp4Detector.extractVideo(
                        motionPhotoFile, outputDir, layout.videoStart(), layout.videoLength());
            } else {
                MotionPhotoMp4Detector.EmbeddedMp4 embeddedMp4 =
                        motionPhotoMp4Detector.detect(motionPhotoFile)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "No embedded MP4 segment found in motion photo"));
                coverFile = motionPhotoMp4Detector.extractCoverImage(
                        motionPhotoFile, outputDir, embeddedMp4.videoStart());
                videoFile = motionPhotoMp4Detector.extractVideo(
                        motionPhotoFile, outputDir, embeddedMp4.videoStart(), embeddedMp4.videoLength());
            }
            if (shouldSkipTranscode(videoFile)) {
                finalVideo = videoFile;
                log.info("Skipping transcode for motion photo video: input={}", videoFile.getName());
            } else {
                mediaTranscodeService.transcodeToMp4(videoFile, transcodedVideo);
            }

            Float duration = null;
            int[] dimensions = null;
            try { duration = mediaProbeService.getVideoDuration(finalVideo); } catch (Exception ignored) {}
            try { dimensions = mediaProbeService.getVideoDimensions(finalVideo); } catch (Exception ignored) {}

            resourcePersistenceService.saveProcessedDynamicPhoto(
                    coverId, videoId, coverFile, finalVideo, duration, dimensions,
                    null, null);
        } catch (Exception e) {
            log.warn("Failed to process motion photo: coverId={}, videoId={}", coverId, videoId, e);
        } finally {
            safeDelete(motionPhotoFile);
            safeDelete(coverFile);
            safeDelete(videoFile);
            if (finalVideo != videoFile) {
                safeDelete(transcodedVideo);
            }
        }
    }

    private boolean shouldSkipTranscode(File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            return false;
        }
        if (!autoTranscode) {
            return true;
        }
        return videoFile.getName().toLowerCase().endsWith(".mp4");
    }

    private File ensureTempDir() {
        File dir = new File(tempDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), tempDir);
        }
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static void safeDelete(File file) {
        try { if (file != null) file.delete(); } catch (Exception ignored) {}
    }
}
