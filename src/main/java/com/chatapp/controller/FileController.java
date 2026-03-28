package com.chatapp.controller;

import com.chatapp.dto.FileUploadResponse;
import com.chatapp.dto.ApiResponse;
import com.chatapp.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传/下载控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/heic"
    );

    private static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList(
            "video/quicktime", "video/mp4", "video/mov"
    );

    /**
     * 上传单个文件(图片或视频)
     * 
     * @param file 上传的文件
     * @param userId 上传用户ID
     * @return 文件信息
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId) {
        
        try {
            // 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("File is empty"));
            }

            String contentType = file.getContentType();
            if (contentType != null) {
                contentType = contentType.trim().toLowerCase();
            }
            if (contentType == null || contentType.isBlank() || "application/octet-stream".equals(contentType)) {
                contentType = inferContentType(file.getOriginalFilename());
            }
            if (contentType == null) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body(ApiResponse.error("Unsupported file type: unknown"));
            }

            // 检查文件类型
            if (!ALLOWED_IMAGE_TYPES.contains(contentType) && 
                !ALLOWED_VIDEO_TYPES.contains(contentType)) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body(ApiResponse.error("Unsupported file type: " + contentType));
            }

            log.info("Uploading file: name={}, type={}, size={} bytes", 
                    file.getOriginalFilename(), contentType, file.getSize());

            // 处理上传
            FileUploadResponse response = fileService.handleFileUpload(file, userId, contentType);

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    private static String inferContentType(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        String name = originalFilename.toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "heic" -> "image/heic";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            default -> null;
        };
    }

    /**
     * 上传Live Photo(JPEG + MOV)
     * 
     * @param jpeg JPEG文件
     * @param mov MOV文件
     * @param userId 上传用户ID
     * @return 动态图片信息
     */
    @PostMapping(value = "/upload/live-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadLivePhoto(
            @RequestParam("jpeg") MultipartFile jpeg,
            @RequestParam("mov") MultipartFile mov,
            @RequestParam(value = "userId", required = false) Long userId) {
        
        try {
            if (jpeg.isEmpty() || mov.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Both JPEG and MOV files are required"));
            }

            log.info("Uploading Live Photo: jpeg={}, mov={}", 
                    jpeg.getOriginalFilename(), mov.getOriginalFilename());

            FileUploadResponse response = fileService.handleLivePhotoUpload(jpeg, mov, userId);

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Live Photo upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    /**
     * 上传Motion Photo(带XMP的JPEG)
     * 
     * @param file Motion Photo文件
     * @param userId 上传用户ID
     * @return 动态图片信息
     */
    @PostMapping(value = "/upload/motion-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadMotionPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId) {
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("File is empty"));
            }

            log.info("Uploading Motion Photo: name={}", file.getOriginalFilename());

            FileUploadResponse response = fileService.handleMotionPhotoUpload(file, userId);

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Motion Photo upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    /**
     * 获取文件
     * 
     * @param id 文件ID
     * @param response HTTP响应
     */
    @GetMapping("/{id}")
    public void getFile(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) {
        try {
            fileService.streamFile(id, request, response);
        } catch (Exception e) {
            log.error("Failed to get file: id={}", id, e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * 获取文件预览信息
     * 
     * @param id 文件ID
     * @return 文件元信息
     */
    @GetMapping("/preview/{id}")
    public ResponseEntity<ApiResponse<FileUploadResponse>> getFilePreview(@PathVariable Long id) {
        try {
            FileUploadResponse fileInfo = fileService.getFileInfo(id);
            return ResponseEntity.ok(ApiResponse.success(fileInfo));
        } catch (Exception e) {
            log.error("Failed to get file preview: id={}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("File not found"));
        }
    }

    /**
     * 删除文件
     * 
     * @param id 文件ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable Long id) {
        try {
            fileService.deleteFile(id);
            return ResponseEntity.ok(ApiResponse.success(null, "File deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete file: id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Delete failed: " + e.getMessage()));
        }
    }
}
