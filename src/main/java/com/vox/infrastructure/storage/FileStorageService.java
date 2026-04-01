package com.vox.infrastructure.storage;

import com.vox.infrastructure.persistence.entity.FileResource;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class FileStorageService {

    private final MinioClient minioClient;
    private final String bucket;
    private final String tempDir;

    public FileStorageService(
            MinioClient minioClient,
            @Value("${minio.bucket}") String bucket,
            @Value("${app.temp-dir:tmp/chat-uploads}") String tempDir
    ) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.tempDir = tempDir;
    }

    public File saveTempFile(MultipartFile file) throws IOException {
        File dir = ensureTempDir();
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "upload";
        }
        originalName = originalName.replace('\\', '_').replace('/', '_');

        File tempFile = new File(dir, UUID.randomUUID() + "_" + originalName);
        file.transferTo(tempFile);
        return tempFile;
    }

    public String uploadToMinio(File file, String objectName, String contentType) throws Exception {
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

    public InputStream openObject(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectName).build()
        );
    }

    public InputStream openObjectRange(String objectName, long offset, long length) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .offset(offset)
                        .length(length)
                        .build()
        );
    }

    public long resolveObjectSize(FileResource resource) {
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

    public void deleteObjectQuietly(String objectName) throws Exception {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucket).object(objectName).build()
        );
    }

    public File ensureTempDir() throws IOException {
        File dir = new File(tempDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), tempDir);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create temp directory: " + dir.getAbsolutePath());
        }
        return dir;
    }
}

