package com.vox.infrastructure.storage;

import com.vox.domain.attachment.AttachmentSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class AttachmentStorageGateway implements AttachmentGateway {

    private final AttachmentStorageClient attachmentStorageClient;

    @Override
    public AttachmentSummary uploadFile(MultipartFile file, Long userId) throws Exception {
        return attachmentStorageClient.uploadFile(file, userId);
    }

    @Override
    public AttachmentSummary uploadLivePhoto(MultipartFile jpeg, MultipartFile mov, Long userId) throws Exception {
        return attachmentStorageClient.uploadLivePhoto(jpeg, mov, userId);
    }

    @Override
    public AttachmentSummary uploadMotionPhoto(MultipartFile file, Long userId) throws Exception {
        return attachmentStorageClient.uploadMotionPhoto(file, userId);
    }

    @Override
    public AttachmentSummary getAttachmentInfo(Long id) {
        return attachmentStorageClient.getAttachmentInfo(id);
    }

    @Override
    public void deleteAttachment(Long id) throws Exception {
        attachmentStorageClient.deleteAttachment(id);
    }

    @Override
    public void streamAttachment(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        attachmentStorageClient.streamAttachment(id, request, response);
    }
}
