package com.chatapp.service;

import com.chatapp.dto.FileUploadResponse;
import com.chatapp.entity.FileResource;
import com.chatapp.repository.FileResourceRepository;
import com.chatapp.service.parser.LivePhotoParser;
import com.chatapp.service.parser.MotionPhotoParser;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final FileResourceRepository fileResourceRepository;
    private final StorageUrlService storageUrlService;
    private final LivePhotoParser livePhotoParser;
    private final MotionPhotoParser motionPhotoParser;
    private final FFmpegService ffmpegService;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${app.temp-dir}")
    private String tempDir;

    /**
     * 处理普通文件上传
     */
    @Transactional
    public FileUploadResponse handleFileUpload(MultipartFile file, Long userId) throws Exception {
        File tempFile = saveTempFile(file);
        
        try {
            String contentType = file.getContentType();
            String objectName = generateObjectName(file.getOriginalFilename());
            String storagePath = uploadToMinio(tempFile, objectName, contentType);

            FileResource resource = new FileResource();
            resource.setOriginalName(file.getOriginalFilename());
            resource.setStoragePath(storagePath);
            resource.setFileType((contentType != null && contentType.startsWith("image"))
                    ? FileResource.FileType.IMAGE
                    : FileResource.FileType.VIDEO);
            resource.setSourceType("Normal");
            resource.setMimeType(contentType);
            resource.setFileSize(file.getSize());
            resource.setUploaderId(userId);

            FileResource saved = fileResourceRepository.save(resource);
            
            FileUploadResponse response = new FileUploadResponse();
            response.setFileId(saved.getId());
            response.setUrl(storageUrlService.toPublicUrl(saved.getStoragePath()));
            response.setFileType(saved.getFileType().name());
            response.setSourceType(saved.getSourceType());
            response.setOriginalName(saved.getOriginalName());
            response.setMimeType(saved.getMimeType());
            response.setSize(saved.getFileSize());
            response.setCreatedAt(saved.getCreateTime());
            response.setUploaderId(saved.getUploaderId());
            
            return response;
        } finally {
            tempFile.delete();
        }
    }

    /**
     * 处理Live Photo上传
     */
    @Transactional
    public FileUploadResponse handleLivePhotoUpload(MultipartFile jpeg, MultipartFile mov, Long userId) 
            throws Exception {
        
        File jpegFile = saveTempFile(jpeg);
        File movFile = saveTempFile(mov);
        
        try {
            LivePhotoParser.LivePhotoMetadata metadata = livePhotoParser.extractMetadata(jpegFile, movFile);
            
            // 转码视频
            File transcodedVideo = new File(tempDir, UUID.randomUUID() + "_transcoded.mp4");
            ffmpegService.transcodeToMp4(movFile, transcodedVideo);
            
            // 上传文件
            String coverPath = uploadToMinio(jpegFile, "dynamic/cover_" + UUID.randomUUID() + ".jpg",
                    "image/jpeg");
            String videoPath = uploadToMinio(transcodedVideo, "dynamic/video_" + UUID.randomUUID() + ".mp4",
                    "video/mp4");

            FileResource coverResource = new FileResource();
            coverResource.setOriginalName(jpeg.getOriginalFilename());
            coverResource.setStoragePath(coverPath);
            coverResource.setFileType(FileResource.FileType.DYNAMIC_COVER);
            coverResource.setSourceType("iOS_LivePhoto");
            coverResource.setMimeType("image/jpeg");
            coverResource.setFileSize(jpeg.getSize());
            coverResource.setUploaderId(userId);

            FileResource savedCover = fileResourceRepository.save(coverResource);

            FileResource videoResource = new FileResource();
            videoResource.setOriginalName(mov.getOriginalFilename());
            videoResource.setStoragePath(videoPath);
            videoResource.setFileType(FileResource.FileType.DYNAMIC_VIDEO);
            videoResource.setSourceType("iOS_LivePhoto");
            videoResource.setMimeType("video/mp4");
            videoResource.setFileSize(transcodedVideo.length());
            videoResource.setDuration(metadata.getDuration());
            videoResource.setUploaderId(userId);
            videoResource.setRelatedFileId(savedCover.getId());

            FileResource savedVideo = fileResourceRepository.save(videoResource);
            savedCover.setRelatedFileId(savedVideo.getId());
            fileResourceRepository.save(savedCover);
            
            FileUploadResponse response = new FileUploadResponse();
            response.setCoverId(savedCover.getId());
            response.setVideoId(savedVideo.getId());
            response.setCoverUrl(storageUrlService.toPublicUrl(savedCover.getStoragePath()));
            response.setVideoUrl(storageUrlService.toPublicUrl(savedVideo.getStoragePath()));
            response.setFileType("DYNAMIC_PHOTO");
            response.setSourceType("iOS_LivePhoto");
            response.setVerified(metadata.isVerified());
            response.setDuration(metadata.getDuration());
            response.setCreatedAt(savedCover.getCreateTime());
            response.setUploaderId(userId);
            
            transcodedVideo.delete();
            return response;
            
        } finally {
            jpegFile.delete();
            movFile.delete();
        }
    }

    /**
     * 处理Motion Photo上传
     */
    @Transactional
    public FileUploadResponse handleMotionPhotoUpload(MultipartFile file, Long userId) throws Exception {
        File motionPhotoFile = saveTempFile(file);
        
        try {
            String xmp = motionPhotoParser.extractXmpMetadata(motionPhotoFile);
            MotionPhotoParser.MotionPhotoMetadata metadata = (xmp == null)
                    ? null
                    : motionPhotoParser.parseMetadata(xmp);

            // 提取封面和视频
            File outputDir = new File(tempDir);
            File coverFile = motionPhotoParser.extractCoverImage(motionPhotoFile, outputDir);
            File videoFile = motionPhotoParser.extractVideo(motionPhotoFile, outputDir);
            
            // 转码视频
            File transcodedVideo = new File(tempDir, UUID.randomUUID() + "_transcoded.mp4");
            ffmpegService.transcodeToMp4(videoFile, transcodedVideo);
            
            // 上传
            String coverPath = uploadToMinio(coverFile, "dynamic/cover_" + UUID.randomUUID() + ".jpg", 
                    "image/jpeg");
            String videoPath = uploadToMinio(transcodedVideo, "dynamic/video_" + UUID.randomUUID() + ".mp4", 
                    "video/mp4");

            FileResource coverResource = new FileResource();
            coverResource.setOriginalName(file.getOriginalFilename());
            coverResource.setStoragePath(coverPath);
            coverResource.setFileType(FileResource.FileType.DYNAMIC_COVER);
            coverResource.setSourceType("Android_MotionPhoto");
            coverResource.setMimeType("image/jpeg");
            coverResource.setFileSize(coverFile.length());
            coverResource.setUploaderId(userId);

            FileResource savedCover = fileResourceRepository.save(coverResource);

            FileResource videoResource = new FileResource();
            videoResource.setOriginalName(file.getOriginalFilename());
            videoResource.setStoragePath(videoPath);
            videoResource.setFileType(FileResource.FileType.DYNAMIC_VIDEO);
            videoResource.setSourceType("Android_MotionPhoto");
            videoResource.setMimeType("video/mp4");
            videoResource.setFileSize(transcodedVideo.length());
            videoResource.setUploaderId(userId);
            videoResource.setRelatedFileId(savedCover.getId());

            FileResource savedVideo = fileResourceRepository.save(videoResource);
            savedCover.setRelatedFileId(savedVideo.getId());
            fileResourceRepository.save(savedCover);
            
            FileUploadResponse response = new FileUploadResponse();
            response.setCoverId(savedCover.getId());
            response.setVideoId(savedVideo.getId());
            response.setCoverUrl(storageUrlService.toPublicUrl(savedCover.getStoragePath()));
            response.setVideoUrl(storageUrlService.toPublicUrl(savedVideo.getStoragePath()));
            response.setFileType("DYNAMIC_PHOTO");
            response.setSourceType("Android_MotionPhoto");
            response.setVideoOffset(metadata == null ? null : metadata.getVideoOffset());
            response.setCreatedAt(savedCover.getCreateTime());
            response.setUploaderId(userId);
            
            coverFile.delete();
            videoFile.delete();
            transcodedVideo.delete();
            return response;
            
        } finally {
            motionPhotoFile.delete();
        }
    }

    /**
     * 流式传输文件
     */
    public void streamFile(Long id, HttpServletResponse response) throws Exception {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(resource.getStoragePath()).build())) {
            
            String contentType = resource.getMimeType();
            response.setContentType(contentType == null || contentType.isBlank()
                    ? "application/octet-stream"
                    : contentType);
            stream.transferTo(response.getOutputStream());
        }
    }

    /**
     * 获取文件信息
     */
    public FileUploadResponse getFileInfo(Long id) {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        FileUploadResponse response = new FileUploadResponse();
        response.setFileId(resource.getId());
        response.setUrl(storageUrlService.toPublicUrl(resource.getStoragePath()));
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

    /**
     * 删除文件
     */
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
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(new FileInputStream(file), file.length(), -1)
                        .contentType(contentType)
                        .build()
        );
        return objectName;
    }

    private String generateObjectName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "files/" + UUID.randomUUID();
        }
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot >= 0 ? originalFilename.substring(dot) : "";
        return "files/" + UUID.randomUUID() + extension;
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
