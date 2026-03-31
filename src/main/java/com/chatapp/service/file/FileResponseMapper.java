package com.chatapp.service.file;

import com.chatapp.dto.FileUploadResponse;
import com.chatapp.entity.FileResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileResponseMapper {

    private final StorageUrlService storageUrlService;

    public FileUploadResponse toFileInfo(FileResource resource) {
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

    public FileUploadResponse toUploadResponse(FileResource resource, FileResource coverResource) {
        FileUploadResponse response = toFileInfo(resource);
        if (coverResource != null) {
            response.setCoverId(coverResource.getId());
            if (!"VideoCoverPending".equals(coverResource.getSourceType())) {
                response.setCoverUrl(storageUrlService.toClientUrl(
                        coverResource.getId(),
                        coverResource.getStoragePath()
                ));
            }
        }
        return response;
    }

    public FileUploadResponse toDynamicUploadResponse(FileResource coverResource, FileResource videoResource) {
        FileUploadResponse response = new FileUploadResponse();
        response.setCoverId(coverResource.getId());
        response.setVideoId(videoResource.getId());
        response.setFileType("DYNAMIC_PHOTO");
        response.setSourceType(coverResource.getSourceType());
        response.setCreatedAt(coverResource.getCreateTime());
        response.setUploaderId(coverResource.getUploaderId());
        return response;
    }
}
