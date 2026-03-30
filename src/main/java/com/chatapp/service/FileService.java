package com.chatapp.service;

import com.chatapp.dto.FileUploadResponse;
import com.chatapp.entity.FileResource;
import com.chatapp.repository.FileResourceRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final FileResourceRepository fileResourceRepository;
    private final StorageUrlService storageUrlService;
    private final FFmpegService ffmpegService;
    private final VideoCoverService videoCoverService;
    private final DynamicPhotoProcessingService dynamicPhotoProcessingService;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${app.temp-dir}")
    private String tempDir;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/heic",
            "image/heif",
            "image/webp",
            "image/gif"
    );

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/quicktime",
            "video/mp4",
            "video/mov",
            "video/x-m4v",
            "video/3gpp",
            "video/3gpp2"
    );

    @Transactional
    public FileUploadResponse handleFileUpload(MultipartFile file, Long userId) throws Exception {
        File tempFile = saveTempFile(file);

        try {
            String contentType = resolveMimeType(file.getContentType(), file.getOriginalFilename(), tempFile);
            if (contentType == null || "application/octet-stream".equals(contentType)) {
                throw new IllegalArgumentException("Unknown file type");
            }
            if (!isAllowedImage(contentType) && !isAllowedVideo(contentType)) {
                throw new IllegalArgumentException("Unsupported file type: " + contentType);
            }

            String objectName = generateObjectName(file.getOriginalFilename());
            String storagePath = uploadToMinio(tempFile, objectName, contentType);

            FileResource resource = new FileResource();
            resource.setOriginalName(file.getOriginalFilename());
            resource.setStoragePath(storagePath);
            resource.setFileType(isAllowedImage(contentType)
                    ? FileResource.FileType.IMAGE
                    : FileResource.FileType.VIDEO);
            resource.setSourceType("Normal");
            resource.setMimeType(contentType);
            resource.setFileSize(file.getSize());
            resource.setUploaderId(userId);

            if (resource.getFileType() == FileResource.FileType.VIDEO) {
                try {
                    resource.setDuration(ffmpegService.getVideoDuration(tempFile));
                } catch (Exception ignored) {
                }
                try {
                    int[] wh = ffmpegService.getVideoDimensions(tempFile);
                    if (wh != null && wh.length == 2) {
                        resource.setWidth(wh[0]);
                        resource.setHeight(wh[1]);
                    }
                } catch (Exception ignored) {
                }
            }

            FileResource saved = fileResourceRepository.save(resource);
            Long coverId = null;
            String coverUrl = null;
            if (saved.getFileType() == FileResource.FileType.VIDEO) {
                FileResource savedCover = videoCoverService.createPlaceholderAndSchedule(
                        saved,
                        file.getOriginalFilename(),
                        userId
                );
                if (savedCover != null) {
                    coverId = savedCover.getId();
                    if (!"VideoCoverPending".equals(savedCover.getSourceType())) {
                        coverUrl = storageUrlService.toClientUrl(savedCover.getId(), savedCover.getStoragePath());
                    }
                }
            }

            FileUploadResponse response = new FileUploadResponse();
            response.setFileId(saved.getId());
            response.setUrl(storageUrlService.toClientUrl(saved.getId(), saved.getStoragePath()));
            response.setCoverId(coverId);
            response.setCoverUrl(coverUrl);
            response.setFileType(saved.getFileType().name());
            response.setSourceType(saved.getSourceType());
            response.setOriginalName(saved.getOriginalName());
            response.setMimeType(saved.getMimeType());
            response.setSize(saved.getFileSize());
            response.setWidth(saved.getWidth());
            response.setHeight(saved.getHeight());
            response.setDuration(saved.getDuration());
            response.setCreatedAt(saved.getCreateTime());
            response.setUploaderId(saved.getUploaderId());
            return response;
        } finally {
            tempFile.delete();
        }
    }

    @Transactional
    public FileUploadResponse handleLivePhotoUpload(MultipartFile jpeg, MultipartFile mov, Long userId)
            throws Exception {
        File jpegFile = saveTempFile(jpeg);
        File movFile = saveTempFile(mov);

        FileResource savedCover = createPendingDynamicResource(
                jpeg.getOriginalFilename(),
                "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                FileResource.FileType.DYNAMIC_COVER,
                "iOS_LivePhoto",
                userId
        );
        FileResource savedVideo = createPendingDynamicResource(
                mov.getOriginalFilename(),
                "dynamic/video_" + UUID.randomUUID() + ".mp4",
                FileResource.FileType.DYNAMIC_VIDEO,
                "iOS_LivePhoto",
                userId
        );
        savedCover.setRelatedFileId(savedVideo.getId());
        savedVideo.setRelatedFileId(savedCover.getId());
        fileResourceRepository.save(savedCover);
        fileResourceRepository.save(savedVideo);

        dynamicPhotoProcessingService.scheduleLivePhotoProcessing(
                jpegFile.getAbsolutePath(),
                movFile.getAbsolutePath(),
                savedCover.getId(),
                savedVideo.getId()
        );

        FileUploadResponse response = new FileUploadResponse();
        response.setCoverId(savedCover.getId());
        response.setVideoId(savedVideo.getId());
        response.setFileType("DYNAMIC_PHOTO");
        response.setSourceType("iOS_LivePhoto");
        response.setCreatedAt(savedCover.getCreateTime());
        response.setUploaderId(userId);
        return response;
    }

    @Transactional
    public FileUploadResponse handleMotionPhotoUpload(MultipartFile file, Long userId) throws Exception {
        File motionPhotoFile = saveTempFile(file);

        FileResource savedCover = createPendingDynamicResource(
                file.getOriginalFilename(),
                "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                FileResource.FileType.DYNAMIC_COVER,
                "Android_MotionPhoto",
                userId
        );
        FileResource savedVideo = createPendingDynamicResource(
                file.getOriginalFilename(),
                "dynamic/video_" + UUID.randomUUID() + ".mp4",
                FileResource.FileType.DYNAMIC_VIDEO,
                "Android_MotionPhoto",
                userId
        );
        savedCover.setRelatedFileId(savedVideo.getId());
        savedVideo.setRelatedFileId(savedCover.getId());
        fileResourceRepository.save(savedCover);
        fileResourceRepository.save(savedVideo);

        dynamicPhotoProcessingService.scheduleMotionPhotoProcessing(
                motionPhotoFile.getAbsolutePath(),
                savedCover.getId(),
                savedVideo.getId()
        );

        FileUploadResponse response = new FileUploadResponse();
        response.setCoverId(savedCover.getId());
        response.setVideoId(savedVideo.getId());
        response.setFileType("DYNAMIC_PHOTO");
        response.setSourceType("Android_MotionPhoto");
        response.setCreatedAt(savedCover.getCreateTime());
        response.setUploaderId(userId);
        return response;
    }

    public void streamFile(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        String contentType = resource.getMimeType();
        response.setContentType(contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType);
        response.setHeader("Accept-Ranges", "bytes");

        long totalSize = resolveObjectSize(resource);
        String range = request.getHeader("Range");
        if (range == null || range.isBlank() || totalSize <= 0) {
            if (totalSize > 0) {
                response.setContentLengthLong(totalSize);
            }
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(resource.getStoragePath()).build())) {
                stream.transferTo(response.getOutputStream());
            }
            return;
        }

        long[] resolved = resolveRange(range, totalSize);
        long start = resolved[0];
        long end = resolved[1];
        if (start < 0 || start >= totalSize || end < start) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + totalSize);
            return;
        }

        long length = end - start + 1;
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);
        response.setContentLengthLong(length);

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(resource.getStoragePath())
                        .offset(start)
                        .length(length)
                        .build())) {
            stream.transferTo(response.getOutputStream());
        }
    }

    private long resolveObjectSize(FileResource resource) {
        Long size = resource.getFileSize();
        if (size != null && size > 0) {
            return size;
        }
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(resource.getStoragePath()).build()
            );
            return stat.size();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static long[] resolveRange(String rangeHeader, long totalSize) {
        String v = rangeHeader.trim().toLowerCase(Locale.ROOT);
        if (!v.startsWith("bytes=")) {
            return new long[]{-1, -1};
        }
        String spec = v.substring("bytes=".length()).trim();
        int comma = spec.indexOf(',');
        if (comma >= 0) {
            spec = spec.substring(0, comma).trim();
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return new long[]{-1, -1};
        }
        String startStr = spec.substring(0, dash).trim();
        String endStr = spec.substring(dash + 1).trim();

        try {
            if (startStr.isEmpty()) {
                long suffix = Long.parseLong(endStr);
                if (suffix <= 0) {
                    return new long[]{-1, -1};
                }
                long start = Math.max(0, totalSize - suffix);
                long end = totalSize - 1;
                return new long[]{start, end};
            }
            long start = Long.parseLong(startStr);
            long end = endStr.isEmpty() ? (totalSize - 1) : Long.parseLong(endStr);
            if (end >= totalSize) {
                end = totalSize - 1;
            }
            return new long[]{start, end};
        } catch (Exception ignored) {
            return new long[]{-1, -1};
        }
    }

    public FileUploadResponse getFileInfo(Long id) {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        FileUploadResponse response = new FileUploadResponse();
        response.setFileId(resource.getId());
        response.setUrl(storageUrlService.toClientUrl(resource.getId(), resource.getStoragePath()));
        response.setFileType(resource.getFileType().name());
        response.setSourceType(resource.getSourceType());
        response.setOriginalName(resource.getOriginalName());
        response.setMimeType(resource.getMimeType());
        response.setSize(resource.getFileSize());
        response.setWidth(resource.getWidth());
        response.setHeight(resource.getHeight());
        response.setDuration(resource.getDuration());
        response.setCreatedAt(resource.getCreateTime());
        response.setUploaderId(resource.getUploaderId());
        return response;
    }

    @Transactional
    public void deleteFile(Long id) throws Exception {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        deleteObjectQuietly(resource.getStoragePath());
        fileResourceRepository.delete(resource);

        Long relatedId = resource.getRelatedFileId();
        if (relatedId != null) {
            fileResourceRepository.findById(relatedId).ifPresent(related -> {
                try {
                    deleteObjectQuietly(related.getStoragePath());
                } catch (Exception ignored) {
                }
                fileResourceRepository.delete(related);
            });
        }
    }

    private File saveTempFile(MultipartFile file) throws IOException {
        String configuredTempDir = tempDir;
        if (configuredTempDir == null || configuredTempDir.isBlank()) {
            configuredTempDir = "tmp/chat-uploads";
        }

        File dir = new File(configuredTempDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), configuredTempDir);
        }

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create temp directory: " + dir.getAbsolutePath());
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "upload";
        }
        originalName = originalName.replace('\\', '_').replace('/', '_');

        File tempFile = new File(dir, UUID.randomUUID() + "_" + originalName);
        file.transferTo(tempFile);
        return tempFile;
    }

    private String uploadToMinio(File file, String objectName, String contentType) throws Exception {
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
        return objectName;
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
        String v = contentType.trim();
        if (v.isEmpty()) {
            return null;
        }
        int semicolon = v.indexOf(';');
        if (semicolon >= 0) {
            v = v.substring(0, semicolon);
        }
        return v.trim().toLowerCase(Locale.ROOT);
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

    private void deleteObjectQuietly(String objectName) throws Exception {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        );
    }
}
