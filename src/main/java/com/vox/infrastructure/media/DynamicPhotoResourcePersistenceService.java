package com.vox.infrastructure.media;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import com.vox.infrastructure.storage.FileStorageService;
import com.vox.infrastructure.message.MessageRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
public class DynamicPhotoResourcePersistenceService {

    private final FileResourceRepository fileResourceRepository;
    private final FileStorageService fileStorageService;
    private final MessageRefreshService messageRefreshService;

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

        fileStorageService.uploadToMinio(coverFile, cover.getStoragePath(), "image/jpeg");
        fileStorageService.uploadToMinio(videoFile, video.getStoragePath(), "video/mp4");

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
}

