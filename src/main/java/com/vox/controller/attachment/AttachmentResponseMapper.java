package com.vox.controller.attachment;

import com.vox.domain.attachment.AttachmentSummary;
import org.springframework.stereotype.Component;

@Component
public class AttachmentResponseMapper {

    public AttachmentResponse toResponse(AttachmentSummary summary) {
        if (summary == null) {
            return null;
        }
        AttachmentResponse response = new AttachmentResponse();
        response.setId(summary.getAttachmentId());
        response.setCoverId(summary.getCoverAttachmentId());
        response.setVideoId(summary.getVideoAttachmentId());
        response.setUrl(summary.getUrl());
        response.setCoverUrl(summary.getCoverUrl());
        response.setVideoUrl(summary.getVideoUrl());
        response.setType(summary.getType());
        response.setSourceType(summary.getSourceType());
        response.setOriginalName(summary.getOriginalName());
        response.setMetadataName(summary.getMetadataName());
        response.setStoredName(summary.getStoredName());
        response.setMimeType(summary.getMimeType());
        response.setSize(summary.getSize());
        response.setWidth(summary.getWidth());
        response.setHeight(summary.getHeight());
        response.setDuration(summary.getDuration());
        response.setVerified(summary.getVerified());
        response.setVideoOffset(summary.getVideoOffset());
        response.setCoverMetadataName(summary.getCoverMetadataName());
        response.setVideoMetadataName(summary.getVideoMetadataName());
        response.setCoverStoredName(summary.getCoverStoredName());
        response.setVideoStoredName(summary.getVideoStoredName());
        response.setCreatedAt(summary.getCreatedAt());
        response.setUploaderId(summary.getUploaderId());
        return response;
    }
}
