package com.vox.application.attachment;

import com.vox.domain.attachment.AttachmentSummary;
import com.vox.infrastructure.storage.AttachmentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAttachmentInfoUseCase {

    private final AttachmentGateway attachmentGateway;

    @Transactional(readOnly = true)
    public AttachmentSummary execute(Long id) {
        return attachmentGateway.getAttachmentInfo(id);
    }
}
