package com.vox.infrastructure.storage;

import com.chatapp.dto.FileUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentGateway {
    FileUploadResponse uploadFile(MultipartFile file, Long userId) throws Exception;

    FileUploadResponse uploadLivePhoto(MultipartFile jpeg, MultipartFile mov, Long userId) throws Exception;

    FileUploadResponse uploadMotionPhoto(MultipartFile file, Long userId) throws Exception;

    FileUploadResponse getFileInfo(Long id);

    void deleteFile(Long id) throws Exception;

    void streamFile(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception;
}
