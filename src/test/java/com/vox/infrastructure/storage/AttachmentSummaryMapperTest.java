package com.vox.infrastructure.storage;

import com.vox.domain.attachment.AttachmentSummary;
import com.vox.infrastructure.persistence.entity.FileResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttachmentSummaryMapperTest {

    private final AttachmentSummaryMapper mapper = new AttachmentSummaryMapper(
            (resourceId, storagePath) -> "client://" + resourceId + "/" + storagePath
    );

    @Test
    void toUploadSummaryPopulatesVideoFieldsForVideoUploads() {
        FileResource video = new FileResource();
        video.setId(101L);
        video.setFileType(FileResource.FileType.VIDEO);
        video.setStoragePath("files/video.mp4");

        FileResource cover = new FileResource();
        cover.setId(202L);
        cover.setStoragePath("files/cover.jpg");
        cover.setSourceType("VideoCoverReady");

        AttachmentSummary summary = mapper.toUploadSummary(video, cover);

        assertEquals(101L, summary.getAttachmentId());
        assertEquals(101L, summary.getVideoAttachmentId());
        assertEquals("client://101/files/video.mp4", summary.getUrl());
        assertEquals("client://101/files/video.mp4", summary.getVideoUrl());
        assertEquals(202L, summary.getCoverAttachmentId());
        assertEquals("client://202/files/cover.jpg", summary.getCoverUrl());
    }

    @Test
    void toUploadSummaryKeepsPendingCoverUrlNull() {
        FileResource video = new FileResource();
        video.setId(101L);
        video.setFileType(FileResource.FileType.VIDEO);
        video.setStoragePath("files/video.mp4");

        FileResource pendingCover = new FileResource();
        pendingCover.setId(202L);
        pendingCover.setStoragePath("files/cover.jpg");
        pendingCover.setSourceType("VideoCoverPending");

        AttachmentSummary summary = mapper.toUploadSummary(video, pendingCover);

        assertEquals(101L, summary.getVideoAttachmentId());
        assertEquals("client://101/files/video.mp4", summary.getVideoUrl());
        assertNull(summary.getCoverUrl());
    }
}
