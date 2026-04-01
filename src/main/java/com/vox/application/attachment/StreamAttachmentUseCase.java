package com.vox.application.attachment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.vox.infrastructure.storage.AttachmentGateway;

@Service
@RequiredArgsConstructor
public class StreamAttachmentUseCase {

    private final AttachmentGateway attachmentGateway;

    public void execute(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        attachmentGateway.streamAttachment(id, request, response);
    }
}
