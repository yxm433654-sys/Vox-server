package com.vox.controller;

import com.vox.application.attachment.DeleteAttachmentUseCase;
import com.vox.application.attachment.GetAttachmentInfoUseCase;
import com.vox.application.attachment.StreamAttachmentUseCase;
import com.vox.application.attachment.UploadAttachmentUseCase;
import com.vox.controller.attachment.AttachmentResponse;
import com.vox.controller.attachment.AttachmentResponseMapper;
import com.vox.controller.common.ApiResponse;
import com.vox.domain.attachment.AttachmentSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping({"/api/attachments", "/api/files"})
@RequiredArgsConstructor
public class AttachmentController {

    private final UploadAttachmentUseCase uploadAttachmentUseCase;
    private final GetAttachmentInfoUseCase getAttachmentInfoUseCase;
    private final DeleteAttachmentUseCase deleteAttachmentUseCase;
    private final StreamAttachmentUseCase streamAttachmentUseCase;
    private final AttachmentResponseMapper attachmentResponseMapper;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
            }
            AttachmentSummary summary = uploadAttachmentUseCase.uploadFile(file, userId);
            return ResponseEntity.ok(ApiResponse.success(attachmentResponseMapper.toResponse(summary)));
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "Invalid request" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("unsupported")
                    ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(message));
        } catch (Exception e) {
            log.error("Attachment upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/upload/live-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadLivePhoto(
            @RequestParam("jpeg") MultipartFile jpeg,
            @RequestParam("mov") MultipartFile mov,
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        try {
            if (jpeg.isEmpty() || mov.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Both JPEG and MOV files are required"));
            }
            AttachmentSummary summary = uploadAttachmentUseCase.uploadLivePhoto(jpeg, mov, userId);
            return ResponseEntity.ok(ApiResponse.success(attachmentResponseMapper.toResponse(summary)));
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "Invalid request" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("unsupported")
                    ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(message));
        } catch (Exception e) {
            log.error("Live photo upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/upload/motion-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadMotionPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
            }
            AttachmentSummary summary = uploadAttachmentUseCase.uploadMotionPhoto(file, userId);
            return ResponseEntity.ok(ApiResponse.success(attachmentResponseMapper.toResponse(summary)));
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "Invalid request" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("unsupported")
                    ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(message));
        } catch (Exception e) {
            log.error("Motion photo upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public void getAttachment(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) {
        try {
            streamAttachmentUseCase.execute(id, request, response);
        } catch (Exception e) {
            log.error("Failed to stream attachment: id={}", id, e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<ApiResponse<AttachmentResponse>> getAttachmentPreview(@PathVariable Long id) {
        try {
            AttachmentSummary fileInfo = getAttachmentInfoUseCase.execute(id);
            return ResponseEntity.ok(ApiResponse.success(attachmentResponseMapper.toResponse(fileInfo)));
        } catch (Exception e) {
            log.error("Failed to get attachment preview: id={}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("File not found"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(@PathVariable Long id) {
        try {
            deleteAttachmentUseCase.execute(id);
            return ResponseEntity.ok(ApiResponse.success(null, "File deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete attachment: id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Delete failed: " + e.getMessage()));
        }
    }
}
