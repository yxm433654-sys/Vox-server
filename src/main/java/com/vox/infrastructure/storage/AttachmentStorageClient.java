package com.vox.infrastructure.storage;

import com.vox.domain.attachment.AttachmentSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class AttachmentStorageClient {

    private final AttachmentCommandService attachmentCommandService;
    private final AttachmentQueryService attachmentQueryService;
    private final AttachmentDeleteService attachmentDeleteService;
    private final AttachmentStreamingService attachmentStreamingService;

    public AttachmentSummary uploadFile(MultipartFile file, Long userId) throws Exception {
        return attachmentCommandService.uploadFile(file, userId);
    }

    public AttachmentSummary uploadFile(MultipartFile file, Long userId, boolean skipMotionDetect) throws Exception {
        return attachmentCommandService.uploadFile(file, userId, skipMotionDetect);
    }

    public AttachmentSummary uploadLivePhoto(MultipartFile jpeg, MultipartFile mov, Long userId) throws Exception {
        return attachmentCommandService.uploadLivePhoto(jpeg, mov, userId);
    }

    public AttachmentSummary uploadMotionPhoto(MultipartFile file, Long userId) throws Exception {
        return attachmentCommandService.uploadMotionPhoto(file, userId);
    }

    public AttachmentSummary getAttachmentInfo(Long id) {
        return attachmentQueryService.getAttachmentInfo(id);
    }

    public void deleteAttachment(Long id) throws Exception {
        attachmentDeleteService.deleteAttachment(id);
    }

    public void streamAttachment(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        attachmentStreamingService.streamAttachment(id, request, response);
    }
}
