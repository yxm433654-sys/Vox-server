package com.vox.infrastructure.storage;

import com.vox.domain.attachment.AttachmentSummary;
import com.vox.infrastructure.media.DynamicPhotoProcessingService;
import com.vox.infrastructure.media.MediaProbeService;
import com.vox.infrastructure.media.MediaSourceTypes;
import com.vox.infrastructure.media.MediaTranscodeService;
import com.vox.infrastructure.media.VideoCoverWorkflowService;
import com.vox.infrastructure.media.VideoTranscodeExecutor;
import com.vox.infrastructure.media.motion.MotionPhotoContainerParser;
import com.vox.infrastructure.media.motion.MotionPhotoMp4Detector;
import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import com.vox.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentCommandService {

    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png",
            "image/heic", "image/heif", "image/webp", "image/gif"
    );
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/quicktime", "video/mp4", "video/mov",
            "video/x-m4v", "video/3gpp", "video/3gpp2"
    );

    private final FileResourceRepository fileResourceRepository;
    private final FileStorageService fileStorageService;
    private final AttachmentSummaryMapper attachmentSummaryMapper;
    private final MediaProbeService mediaProbeService;
    private final VideoCoverWorkflowService videoCoverWorkflowService;
    private final DynamicPhotoProcessingService dynamicPhotoProcessingService;
    private final MediaTranscodeService mediaTranscodeService;
    private final MimeTypeResolver mimeTypeResolver;
    private final UserRepository userRepository;
    private final MotionPhotoContainerParser motionPhotoContainerParser;
    private final MotionPhotoMp4Detector motionPhotoMp4Detector;
    private final VideoTranscodeExecutor videoTranscodeExecutor;

    @Transactional
    public AttachmentSummary uploadFile(MultipartFile file, Long userId) throws Exception {
        return uploadFile(file, userId, false);
    }

    @Transactional
    public AttachmentSummary uploadFile(MultipartFile file, Long userId, boolean skipMotionDetect) throws Exception {
        validateUploader(userId);
        File tempFile = fileStorageService.saveTempFile(file);
        File transcodedVideo = null;
        boolean keepTempFileForAsyncProcessing = false;
        try {
            String contentType = mimeTypeResolver.resolve(
                    file.getContentType(), file.getOriginalFilename(), tempFile);
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            FileResource.FileType fileType = classifyFileType(contentType);
            if (!skipMotionDetect
                    && fileType == FileResource.FileType.IMAGE
                    && (motionPhotoContainerParser.parse(tempFile).isPresent()
                    || motionPhotoMp4Detector.hasEmbeddedMp4(tempFile))) {
                keepTempFileForAsyncProcessing = true;
                return createPendingMotionPhoto(file, tempFile, userId);
            }

            File uploadFile = tempFile;
            String uploadContentType = contentType;
            String sourceType = "Normal";
            String normalizedOriginalName = normalizeOriginalName(file.getOriginalFilename());

            // Async transcode: save original to MinIO immediately, transcode in background
            if (fileType == FileResource.FileType.VIDEO
                    && shouldTranscodeVideo(contentType, file.getOriginalFilename())) {
                normalizedOriginalName = ensureMp4Extension(normalizedOriginalName);
                String storagePath = fileStorageService.uploadToMinio(
                        tempFile, generateObjectName(normalizedOriginalName), contentType);

                FileResource resource = new FileResource();
                resource.setOriginalName(normalizedOriginalName);
                resource.setStoragePath(storagePath);
                resource.setFileType(fileType);
                resource.setSourceType("PendingTranscode");
                resource.setMimeType(contentType);
                resource.setFileSize(tempFile.length());
                resource.setUploaderId(userId);
                fillVideoMetadata(resource, tempFile);

                FileResource saved = fileResourceRepository.save(resource);
                keepTempFileForAsyncProcessing = true;
                videoTranscodeExecutor.transcodeAndUpload(
                        saved.getId(), tempFile.getAbsolutePath(),
                        file.getOriginalFilename(), userId);

                FileResource coverResource = videoCoverWorkflowService.createPlaceholderAndSchedule(
                        saved, file.getOriginalFilename(), userId);
                return attachmentSummaryMapper.toUploadSummary(saved, coverResource);
            }

            String storagePath = fileStorageService.uploadToMinio(
                    uploadFile, generateObjectName(normalizedOriginalName), uploadContentType);

            FileResource resource = new FileResource();
            resource.setOriginalName(normalizedOriginalName);
            resource.setStoragePath(storagePath);
            resource.setFileType(fileType);
            resource.setSourceType(sourceType);
            resource.setMimeType(uploadContentType);
            resource.setFileSize(uploadFile.length());
            resource.setUploaderId(userId);

            if (resource.getFileType() == FileResource.FileType.IMAGE) {
                fillImageMetadata(resource, tempFile);
            }
            if (resource.getFileType() == FileResource.FileType.VIDEO) {
                fillVideoMetadata(resource, uploadFile);
            }

            FileResource saved = fileResourceRepository.save(resource);
            FileResource coverResource = null;
            if (saved.getFileType() == FileResource.FileType.VIDEO) {
                coverResource = videoCoverWorkflowService.createPlaceholderAndSchedule(
                        saved, file.getOriginalFilename(), userId);
            }
            return attachmentSummaryMapper.toUploadSummary(saved, coverResource);
        } finally {
            if (transcodedVideo != null && transcodedVideo.exists()) {
                transcodedVideo.delete();
            }
            if (!keepTempFileForAsyncProcessing) {
                tempFile.delete();
            }
        }
    }

    @Transactional
    public AttachmentSummary uploadLivePhoto(MultipartFile image, MultipartFile video, Long userId)
            throws Exception {
        validateUploader(userId);
        File imageFile = fileStorageService.saveTempFile(image);
        File videoFile = fileStorageService.saveTempFile(video);

        FileResource savedCover = createPendingDynamicResource(
                image.getOriginalFilename(),
                "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                FileResource.FileType.DYNAMIC_COVER,
                MediaSourceTypes.IOS_LIVE_PHOTO,
                userId);
        FileResource savedVideo = createPendingDynamicResource(
                video.getOriginalFilename(),
                "dynamic/video_" + UUID.randomUUID() + ".mp4",
                FileResource.FileType.DYNAMIC_VIDEO,
                MediaSourceTypes.IOS_LIVE_PHOTO,
                userId);
        linkRelatedResources(savedCover, savedVideo);

        dynamicPhotoProcessingService.scheduleLivePhotoProcessing(
                imageFile.getAbsolutePath(), videoFile.getAbsolutePath(),
                savedCover.getId(), savedVideo.getId());
        return attachmentSummaryMapper.toDynamicPhotoSummary(savedCover, savedVideo);
    }

    @Transactional
    public AttachmentSummary uploadMotionPhoto(MultipartFile file, Long userId) throws Exception {
        validateUploader(userId);
        File motionPhotoFile = fileStorageService.saveTempFile(file);

        FileResource savedCover = createPendingDynamicResource(
                file.getOriginalFilename(),
                "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                FileResource.FileType.DYNAMIC_COVER,
                MediaSourceTypes.ANDROID_MOTION_PHOTO,
                userId);
        FileResource savedVideo = createPendingDynamicResource(
                file.getOriginalFilename(),
                "dynamic/video_" + UUID.randomUUID() + ".mp4",
                FileResource.FileType.DYNAMIC_VIDEO,
                MediaSourceTypes.ANDROID_MOTION_PHOTO,
                userId);
        linkRelatedResources(savedCover, savedVideo);

        dynamicPhotoProcessingService.scheduleMotionPhotoProcessing(
                motionPhotoFile.getAbsolutePath(),
                savedCover.getId(), savedVideo.getId());
        return attachmentSummaryMapper.toDynamicPhotoSummary(savedCover, savedVideo);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private void fillImageMetadata(FileResource resource, File tempFile) {
        try {
            BufferedImage image = ImageIO.read(tempFile);
            if (image == null) return;
            if (image.getWidth() > 0) resource.setWidth(image.getWidth());
            if (image.getHeight() > 0) resource.setHeight(image.getHeight());
        } catch (Exception ignored) {
        }
    }

    private void validateUploader(Long userId) {
        if (userId == null) {
            return;
        }
        if (userId <= 0 || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Uploader userId not found: " + userId);
        }
    }

    private AttachmentSummary createPendingMotionPhoto(
            MultipartFile file, File motionPhotoFile, Long userId) {
        FileResource savedCover = createPendingDynamicResource(
                file.getOriginalFilename(),
                "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                FileResource.FileType.DYNAMIC_COVER,
                MediaSourceTypes.ANDROID_MOTION_PHOTO,
                userId);
        FileResource savedVideo = createPendingDynamicResource(
                file.getOriginalFilename(),
                "dynamic/video_" + UUID.randomUUID() + ".mp4",
                FileResource.FileType.DYNAMIC_VIDEO,
                MediaSourceTypes.ANDROID_MOTION_PHOTO,
                userId);
        linkRelatedResources(savedCover, savedVideo);

        dynamicPhotoProcessingService.scheduleMotionPhotoProcessing(
                motionPhotoFile.getAbsolutePath(),
                savedCover.getId(), savedVideo.getId());
        return attachmentSummaryMapper.toDynamicPhotoSummary(savedCover, savedVideo);
    }

    private void fillVideoMetadata(FileResource resource, File tempFile) {
        try {
            resource.setDuration(mediaProbeService.getVideoDuration(tempFile));
        } catch (Exception ignored) {
        }
        try {
            int[] wh = mediaProbeService.getVideoDimensions(tempFile);
            if (wh != null && wh.length == 2) {
                resource.setWidth(wh[0]);
                resource.setHeight(wh[1]);
            }
        } catch (Exception ignored) {
        }
    }

    private FileResource createPendingDynamicResource(
            String originalName, String storagePath,
            FileResource.FileType fileType, String sourceType, Long userId) {
        FileResource resource = new FileResource();
        resource.setOriginalName(normalizeOriginalName(originalName));
        resource.setStoragePath(storagePath);
        resource.setFileType(fileType);
        resource.setSourceType(sourceType);
        resource.setFileSize(0L);
        resource.setUploaderId(userId);
        return fileResourceRepository.save(resource);
    }

    private void linkRelatedResources(FileResource cover, FileResource video) {
        cover.setRelatedFileId(video.getId());
        video.setRelatedFileId(cover.getId());
        fileResourceRepository.save(cover);
        fileResourceRepository.save(video);
    }

    private String generateObjectName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "files/" + UUID.randomUUID();
        }
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot >= 0 ? originalFilename.substring(dot) : "";
        return "files/" + UUID.randomUUID() + extension;
    }

    private String normalizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "attachment";
        }
        String trimmed = originalName.trim();
        if (trimmed.length() <= MAX_ORIGINAL_NAME_LENGTH) {
            return trimmed;
        }
        int dot = trimmed.lastIndexOf('.');
        if (dot <= 0 || dot == trimmed.length() - 1) {
            return trimmed.substring(0, MAX_ORIGINAL_NAME_LENGTH);
        }
        String extension = trimmed.substring(dot);
        int maxBaseLength = Math.max(1, MAX_ORIGINAL_NAME_LENGTH - extension.length());
        String baseName = trimmed.substring(0, dot);
        if (baseName.length() > maxBaseLength) {
            baseName = baseName.substring(0, maxBaseLength);
        }
        return baseName + extension;
    }

    private boolean shouldTranscodeVideo(String contentType, String originalFilename) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalizedContentType = contentType.toLowerCase();
        if (!normalizedContentType.startsWith("video/")) {
            return false;
        }
        if (!"video/mp4".equals(normalizedContentType)) {
            return true;
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            return false;
        }
        String normalizedName = originalFilename.toLowerCase();
        return !normalizedName.endsWith(".mp4");
    }

    private String ensureMp4Extension(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "attachment.mp4";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot <= 0) {
            return originalName + ".mp4";
        }
        return originalName.substring(0, dot) + ".mp4";
    }

    private FileResource.FileType classifyFileType(String contentType) {
        if (contentType != null && ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return FileResource.FileType.IMAGE;
        }
        if (contentType != null && ALLOWED_VIDEO_TYPES.contains(contentType)) {
            return FileResource.FileType.VIDEO;
        }
        return FileResource.FileType.FILE;
    }
}
