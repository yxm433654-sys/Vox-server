package com.vox.infrastructure.storage;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.domain.attachment.AttachmentSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttachmentSummaryMapper {

    private final StorageUrlResolver storageUrlResolver;

    public AttachmentSummary toSummary(FileResource resource) {
        if (resource == null) {
            return null;
        }
        AttachmentSummary summary = new AttachmentSummary();
        summary.setAttachmentId(resource.getId());
        summary.setUrl(storageUrlResolver.toClientUrl(resource.getId(), resource.getStoragePath()));
        summary.setType(resource.getFileType().name());
        summary.setSourceType(resource.getSourceType());
        summary.setOriginalName(resource.getOriginalName());
        summary.setMimeType(resource.getMimeType());
        summary.setSize(resource.getFileSize());
        summary.setWidth(resource.getWidth());
        summary.setHeight(resource.getHeight());
        summary.setDuration(resource.getDuration());
        summary.setCreatedAt(resource.getCreateTime());
        summary.setUploaderId(resource.getUploaderId());
        return summary;
    }

    public AttachmentSummary toUploadSummary(FileResource resource, FileResource coverResource) {
        AttachmentSummary summary = toSummary(resource);
        if (summary == null) {
            return null;
        }
        if (coverResource != null) {
            summary.setCoverAttachmentId(coverResource.getId());
            if (!"VideoCoverPending".equals(coverResource.getSourceType())) {
                summary.setCoverUrl(storageUrlResolver.toClientUrl(
                        coverResource.getId(),
                        coverResource.getStoragePath()
                ));
            }
        }
        return summary;
    }

    public AttachmentSummary toDynamicPhotoSummary(FileResource coverResource, FileResource videoResource) {
        AttachmentSummary summary = new AttachmentSummary();
        summary.setCoverAttachmentId(coverResource.getId());
        summary.setVideoAttachmentId(videoResource.getId());
        summary.setType("DYNAMIC_PHOTO");
        summary.setSourceType(coverResource.getSourceType());
        summary.setCreatedAt(coverResource.getCreateTime());
        summary.setUploaderId(coverResource.getUploaderId());
        return summary;
    }
}

