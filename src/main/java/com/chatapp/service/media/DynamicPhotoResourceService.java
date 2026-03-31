package com.chatapp.service.media;

import com.chatapp.entity.FileResource;
import com.chatapp.repository.FileResourceRepository;
import com.chatapp.service.message.MessageRefreshService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;

@Service
@RequiredArgsConstructor
public class DynamicPhotoResourceService {

    private final FileResourceRepository fileResourceRepository;
    private final MinioClient minioClient;
    private final MessageRefreshService messageRefreshService;

    @Value("${minio.bucket}")
    private String bucket;

    public boolean saveProcessedDynamicPhoto(
            Long coverId,
            Long videoId,
            File coverFile,
            File videoFile,
            Float duration,
            int[] dimensions
    ) throws Exception {
        FileResource cover = fileResourceRepository.findById(coverId).orElse(null);
        FileResource video = fileResourceRepository.findById(videoId).orElse(null);
        if (cover == null || video == null) {
            return false;
        }

        uploadFile(coverFile, cover.getStoragePath(), "image/jpeg");
        uploadFile(videoFile, video.getStoragePath(), "video/mp4");

        cover.setFileSize(coverFile.length());
        cover.setMimeType("image/jpeg");
        fileResourceRepository.save(cover);

        video.setFileSize(videoFile.length());
        video.setMimeType("video/mp4");
        video.setDuration(duration);
        if (dimensions != null && dimensions.length == 2) {
            video.setWidth(dimensions[0]);
            video.setHeight(dimensions[1]);
        }
        fileResourceRepository.save(video);

        messageRefreshService.pushMediaRefreshByResourceId(coverId);
        return true;
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
}
