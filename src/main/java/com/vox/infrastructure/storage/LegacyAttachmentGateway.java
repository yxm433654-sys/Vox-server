package com.vox.infrastructure.storage;

import com.chatapp.dto.FileUploadResponse;
import com.chatapp.service.file.FileDeleteService;
import com.chatapp.service.file.FileQueryService;
import com.chatapp.service.file.FileStreamingService;
import com.chatapp.service.file.FileUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class LegacyAttachmentGateway implements AttachmentGateway {

    private final FileUploadService fileUploadService;
    private final FileQueryService fileQueryService;
    private final FileDeleteService fileDeleteService;
    private final FileStreamingService fileStreamingService;

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, Long userId) throws Exception {
        return fileUploadService.uploadFile(file, userId);
    }

    @Override
    public FileUploadResponse uploadLivePhoto(MultipartFile jpeg, MultipartFile mov, Long userId) throws Exception {
        return fileUploadService.uploadLivePhoto(jpeg, mov, userId);
    }

    @Override
    public FileUploadResponse uploadMotionPhoto(MultipartFile file, Long userId) throws Exception {
        return fileUploadService.uploadMotionPhoto(file, userId);
    }

    @Override
    public FileUploadResponse getFileInfo(Long id) {
        return fileQueryService.getFileInfo(id);
    }

    @Override
    public void deleteFile(Long id) throws Exception {
        fileDeleteService.deleteFile(id);
    }

    @Override
    public void streamFile(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        fileStreamingService.streamFile(id, request, response);
    }
}
