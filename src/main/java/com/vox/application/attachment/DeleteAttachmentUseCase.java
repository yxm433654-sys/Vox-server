package com.vox.application.attachment;

import com.vox.infrastructure.storage.AttachmentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteAttachmentUseCase {

    private final AttachmentGateway attachmentGateway;

    @Transactional
    public void execute(Long id) throws Exception {
        attachmentGateway.deleteFile(id);
    }
}
