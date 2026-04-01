package com.vox.infrastructure.storage;

import com.vox.domain.attachment.AttachmentSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentGateway {
    AttachmentSummary uploadFile(MultipartFile file, Long userId) throws Exception;

    AttachmentSummary uploadLivePhoto(MultipartFile jpeg, MultipartFile mov, Long userId) throws Exception;

    AttachmentSummary uploadMotionPhoto(MultipartFile file, Long userId) throws Exception;

    AttachmentSummary getAttachmentInfo(Long id);

    void deleteAttachment(Long id) throws Exception;

    void streamAttachment(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception;
}
