package com.chatapp.service;

import com.chatapp.entity.FileResource;
import com.chatapp.repository.FileResourceRepository;
import com.chatapp.service.parser.LivePhotoParser;
import com.chatapp.service.parser.MotionPhotoParser;
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
import java.io.FileInputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicPhotoProcessingService {

    private final FileResourceRepository fileResourceRepository;
    private final LivePhotoParser livePhotoParser;
    private final MotionPhotoParser motionPhotoParser;
    private final FFmpegService ffmpegService;
    private final MinioClient minioClient;
    private final MessageService messageService;
    private final ObjectProvider<DynamicPhotoProcessingService> selfProvider;

    @Value("${minio.bucket}")
    private String bucket;

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
            LivePhotoParser.LivePhotoMetadata metadata = livePhotoParser.extractMetadata(jpegFile, movFile);
            ffmpegService.transcodeToMp4(movFile, transcodedVideo);

            FileResource cover = fileResourceRepository.findById(coverId).orElse(null);
            FileResource video = fileResourceRepository.findById(videoId).orElse(null);
            if (cover == null || video == null) {
                return;
            }

            uploadFile(jpegFile, cover.getStoragePath(), "image/jpeg");
            uploadFile(transcodedVideo, video.getStoragePath(), "video/mp4");

            cover.setFileSize(jpegFile.length());
            cover.setMimeType("image/jpeg");
            fileResourceRepository.save(cover);

            video.setFileSize(transcodedVideo.length());
            video.setMimeType("video/mp4");
            video.setDuration(metadata.getDuration());
            try {
                int[] wh = ffmpegService.getVideoDimensions(transcodedVideo);
                if (wh != null && wh.length == 2) {
                    video.setWidth(wh[0]);
                    video.setHeight(wh[1]);
                }
            } catch (Exception ignored) {
            }
            fileResourceRepository.save(video);

            messageService.pushMediaRefreshByResourceId(coverId);
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
            coverFile = motionPhotoParser.extractCoverImage(motionPhotoFile, outputDir);
            videoFile = motionPhotoParser.extractVideo(motionPhotoFile, outputDir);
            ffmpegService.transcodeToMp4(videoFile, transcodedVideo);

            FileResource cover = fileResourceRepository.findById(coverId).orElse(null);
            FileResource video = fileResourceRepository.findById(videoId).orElse(null);
            if (cover == null || video == null) {
                return;
            }

            uploadFile(coverFile, cover.getStoragePath(), "image/jpeg");
            uploadFile(transcodedVideo, video.getStoragePath(), "video/mp4");

            cover.setFileSize(coverFile.length());
            cover.setMimeType("image/jpeg");
            fileResourceRepository.save(cover);

            video.setFileSize(transcodedVideo.length());
            video.setMimeType("video/mp4");
            try {
                video.setDuration(ffmpegService.getVideoDuration(transcodedVideo));
            } catch (Exception ignored) {
            }
            try {
                int[] wh = ffmpegService.getVideoDimensions(transcodedVideo);
                if (wh != null && wh.length == 2) {
                    video.setWidth(wh[0]);
                    video.setHeight(wh[1]);
                }
            } catch (Exception ignored) {
            }
            fileResourceRepository.save(video);

            messageService.pushMediaRefreshByResourceId(coverId);
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

    private void uploadFile(File file, String objectName, String contentType) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(in, file.length(), -1)
                            .contentType(contentType)
                            .build()
            );
        }
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
