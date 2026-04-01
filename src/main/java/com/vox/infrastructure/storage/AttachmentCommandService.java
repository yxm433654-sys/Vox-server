package com.vox.infrastructure.storage;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import com.vox.infrastructure.storage.FileStorageService;
import com.vox.infrastructure.media.MediaProbeService;
import com.vox.infrastructure.media.MediaSourceTypes;
import com.vox.domain.attachment.AttachmentSummary;
import com.vox.infrastructure.media.DynamicPhotoProcessingService;
import com.vox.infrastructure.media.VideoCoverWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentCommandService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/heic", "image/heif", "image/webp", "image/gif"
    );
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/quicktime", "video/mp4", "video/mov", "video/x-m4v", "video/3gpp", "video/3gpp2"
    );

    private final FileResourceRepository fileResourceRepository;
    private final FileStorageService fileStorageService;
    private final AttachmentSummaryMapper attachmentSummaryMapper;
    private final MediaProbeService mediaProbeService;
    private final VideoCoverWorkflowService videoCoverWorkflowService;
    private final DynamicPhotoProcessingService dynamicPhotoProcessingService;

    @Transactional
    public AttachmentSummary uploadFile(MultipartFile file, Long userId) throws Exception {
        File tempFile = fileStorageService.saveTempFile(file);
        try {
            String contentType = resolveMimeType(file.getContentType(), file.getOriginalFilename(), tempFile);
            if (contentType == null || "application/octet-stream".equals(contentType)) {
                throw new IllegalArgumentException("Unknown file type");
            }
            if (!isAllowedImage(contentType) && !isAllowedVideo(contentType)) {
                throw new IllegalArgumentException("Unsupported file type: " + contentType);
            }

            String storagePath = fileStorageService.uploadToMinio(
                    tempFile,
                    generateObjectName(file.getOriginalFilename()),
                    contentType
            );

            FileResource resource = new FileResource();
            resource.setOriginalName(file.getOriginalFilename());
            resource.setStoragePath(storagePath);
            resource.setFileType(isAllowedImage(contentType) ? FileResource.FileType.IMAGE : FileResource.FileType.VIDEO);
            resource.setSourceType("Normal");
            resource.setMimeType(contentType);
            resource.setFileSize(file.getSize());
            resource.setUploaderId(userId);

            if (resource.getFileType() == FileResource.FileType.VIDEO) {
                fillVideoMetadata(resource, tempFile);
            }

            FileResource saved = fileResourceRepository.save(resource);
            FileResource coverResource = null;
            if (saved.getFileType() == FileResource.FileType.VIDEO) {
                coverResource = videoCoverWorkflowService.createPlaceholderAndSchedule(
                        saved,
                        file.getOriginalFilename(),
                        userId
                );
            }
            return attachmentSummaryMapper.toUploadSummary(saved, coverResource);
        } finally {
            tempFile.delete();
        }
    }

    @Transactional
    public AttachmentSummary uploadLivePhoto(MultipartFile jpeg, MultipartFile mov, Long userId) throws Exception {
        File jpegFile = fileStorageService.saveTempFile(jpeg);
        File movFile = fileStorageService.saveTempFile(mov);

        FileResource savedCover = createPendingDynamicResource(
                jpeg.getOriginalFilename(),
                "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                FileResource.FileType.DYNAMIC_COVER,
                MediaSourceTypes.IOS_LIVE_PHOTO,
                userId
        );
        FileResource savedVideo = createPendingDynamicResource(
                mov.getOriginalFilename(),
                "dynamic/video_" + UUID.randomUUID() + ".mp4",
                FileResource.FileType.DYNAMIC_VIDEO,
                MediaSourceTypes.IOS_LIVE_PHOTO,
                userId
        );
        linkRelatedResources(savedCover, savedVideo);

        dynamicPhotoProcessingService.scheduleLivePhotoProcessing(
                jpegFile.getAbsolutePath(),
                movFile.getAbsolutePath(),
                savedCover.getId(),
                savedVideo.getId()
        );
        return attachmentSummaryMapper.toDynamicPhotoSummary(savedCover, savedVideo);
    }

    @Transactional
    public AttachmentSummary uploadMotionPhoto(MultipartFile file, Long userId) throws Exception {
        File motionPhotoFile = fileStorageService.saveTempFile(file);

        FileResource savedCover = createPendingDynamicResource(
                file.getOriginalFilename(),
                "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                FileResource.FileType.DYNAMIC_COVER,
                MediaSourceTypes.ANDROID_MOTION_PHOTO,
                userId
        );
        FileResource savedVideo = createPendingDynamicResource(
                file.getOriginalFilename(),
                "dynamic/video_" + UUID.randomUUID() + ".mp4",
                FileResource.FileType.DYNAMIC_VIDEO,
                MediaSourceTypes.ANDROID_MOTION_PHOTO,
                userId
        );
        linkRelatedResources(savedCover, savedVideo);

        dynamicPhotoProcessingService.scheduleMotionPhotoProcessing(
                motionPhotoFile.getAbsolutePath(),
                savedCover.getId(),
                savedVideo.getId()
        );
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
            String originalName,
            String storagePath,
            FileResource.FileType fileType,
            String sourceType,
            Long userId
    ) {
        FileResource resource = new FileResource();
        resource.setOriginalName(originalName);
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

    private String resolveMimeType(String declaredContentType, String originalFilename, File tempFile)
            throws IOException {
        String normalized = normalizeMime(declaredContentType);
        if (normalized != null && !"application/octet-stream".equals(normalized)) {
            return normalized;
        }
        String fromName = inferMimeFromFilename(originalFilename);
        if (fromName != null) {
            return fromName;
        }
        String fromMagic = inferMimeFromMagicBytes(tempFile);
        if (fromMagic != null) {
            return fromMagic;
        }
        return normalized;
    }

    private boolean isAllowedImage(String contentType) {
        return contentType != null && ALLOWED_IMAGE_TYPES.contains(contentType);
    }

    private boolean isAllowedVideo(String contentType) {
        return contentType != null && ALLOWED_VIDEO_TYPES.contains(contentType);
    }

    private String normalizeMime(String contentType) {
        if (contentType == null) {
            return null;
        }
        String value = contentType.trim();
        if (value.isEmpty()) {
            return null;
        }
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon);
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String inferMimeFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String name = filename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "mp4" -> "video/mp4";
            case "m4v" -> "video/x-m4v";
            case "mov", "qt" -> "video/quicktime";
            case "3gp" -> "video/3gpp";
            case "3g2" -> "video/3gpp2";
            default -> null;
        };
    }

    private String inferMimeFromMagicBytes(File file) throws IOException {
        byte[] header = new byte[16];
        int read;
        try (InputStream in = new FileInputStream(file)) {
            read = in.read(header);
        }
        if (read <= 0) {
            return null;
        }
        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (read >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return "image/png";
        }
        if (read >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return "image/gif";
        }
        if (read >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            return "image/webp";
        }
        if (read >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            String brand = new String(header, 8, 4, StandardCharsets.US_ASCII);
            if ("qt  ".equals(brand)) {
                return "video/quicktime";
            }
            if (brand.startsWith("3gp")) {
                return "video/3gpp";
            }
            if (isHeifBrand(brand)) {
                return "image/heic";
            }
            return "video/mp4";
        }
        return null;
    }

    private boolean isHeifBrand(String brand) {
        return "heic".equals(brand)
                || "heix".equals(brand)
                || "hevc".equals(brand)
                || "hevx".equals(brand)
                || "mif1".equals(brand)
                || "msf1".equals(brand);
    }
}

