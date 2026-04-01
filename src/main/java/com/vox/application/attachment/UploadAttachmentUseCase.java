package com.vox.application.attachment;

import com.vox.domain.attachment.AttachmentSummary;
import com.vox.infrastructure.storage.AttachmentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UploadAttachmentUseCase {

    private final AttachmentGateway attachmentGateway;

    @Transactional
    public AttachmentSummary uploadFile(MultipartFile file, Long userId) throws Exception {
        return attachmentGateway.uploadFile(file, userId);
    }

    @Transactional
    public AttachmentSummary uploadLivePhoto(MultipartFile jpeg, MultipartFile mov, Long userId) throws Exception {
        return attachmentGateway.uploadLivePhoto(jpeg, mov, userId);
    }

    @Transactional
    public AttachmentSummary uploadMotionPhoto(MultipartFile file, Long userId) throws Exception {
        return attachmentGateway.uploadMotionPhoto(file, userId);
    }
}
