package com.vox.infrastructure.storage;

import com.vox.domain.attachment.AttachmentSummary;
import com.vox.infrastructure.media.DynamicPhotoProcessingService;
import com.vox.infrastructure.media.MediaProbeService;
import com.vox.infrastructure.media.MediaSourceTypes;
import com.vox.infrastructure.media.VideoCoverWorkflowService;
import com.vox.infrastructure.media.motion.MotionPhotoContainerParser;
import com.vox.infrastructure.media.motion.MotionPhotoMp4Detector;
import com.vox.infrastructure.media.motion.MotionPhotoResolver;
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
    private final MimeTypeResolver mimeTypeResolver;
    private final UserRepository userRepository;
    private final MotionPhotoContainerParser motionPhotoContainerParser;
    private final MotionPhotoMp4Detector motionPhotoMp4Detector;
    private final MotionPhotoResolver motionPhotoResolver;

    @Transactional
    public AttachmentSummary uploadFile(MultipartFile file, Long userId) throws Exception {
        validateUploader(userId);
        File tempFile = fileStorageService.saveTempFile(file);
        boolean keepTempFileForAsyncProcessing = false;
        try {
            String contentType = mimeTypeResolver.resolve(
                    file.getContentType(), file.getOriginalFilename(), tempFile);
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            FileResource.FileType fileType = classifyFileType(contentType);
            if (fileType == FileResource.FileType.IMAGE
                    && (motionPhotoContainerParser.parse(tempFile).isPresent()
                    || motionPhotoResolver.supports(tempFile)
                    || motionPhotoMp4Detector.hasEmbeddedMp4(tempFile))) {
                keepTempFileForAsyncProcessing = true;
                return createPendingMotionPhoto(file, tempFile, userId);
            }
            String storagePath = fileStorageService.uploadToMinio(
                    tempFile, generateObjectName(file.getOriginalFilename()), contentType);

            FileResource resource = new FileResource();
            resource.setOriginalName(normalizeOriginalName(file.getOriginalFilename()));
            resource.setStoragePath(storagePath);
            resource.setFileType(fileType);
            resource.setSourceType("Normal");
            resource.setMimeType(contentType);
            resource.setFileSize(file.getSize());
            resource.setUploaderId(userId);

            if (resource.getFileType() == FileResource.FileType.IMAGE) {
                fillImageMetadata(resource, tempFile);
            }
            if (resource.getFileType() == FileResource.FileType.VIDEO) {
                fillVideoMetadata(resource, tempFile);
            }

            FileResource saved = fileResourceRepository.save(resource);
            FileResource coverResource = null;
            if (saved.getFileType() == FileResource.FileType.VIDEO) {
                coverResource = videoCoverWorkflowService.createPlaceholderAndSchedule(
                        saved, file.getOriginalFilename(), userId);
            }
            return attachmentSummaryMapper.toUploadSummary(saved, coverResource);
        } finally {
            if (!keepTempFileForAsyncProcessing) {
                tempFile.delete();
            }
        }
    }

    @Transactional
    public AttachmentSummary uploadLivePhoto(MultipartFile jpeg, MultipartFile mov, Long userId)
            throws Exception {
        validateUploader(userId);
        File jpegFile = fileStorageService.saveTempFile(jpeg);
        File movFile = fileStorageService.saveTempFile(mov);

        FileResource savedCover = createPendingDynamicResource(
                jpeg.getOriginalFilename(),
                "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                FileResource.FileType.DYNAMIC_COVER,
                MediaSourceTypes.IOS_LIVE_PHOTO,
                userId);
        FileResource savedVideo = createPendingDynamicResource(
                mov.getOriginalFilename(),
                "dynamic/video_" + UUID.randomUUID() + ".mp4",
                FileResource.FileType.DYNAMIC_VIDEO,
                MediaSourceTypes.IOS_LIVE_PHOTO,
                userId);
        linkRelatedResources(savedCover, savedVideo);

        dynamicPhotoProcessingService.scheduleLivePhotoProcessing(
                jpegFile.getAbsolutePath(), movFile.getAbsolutePath(),
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
