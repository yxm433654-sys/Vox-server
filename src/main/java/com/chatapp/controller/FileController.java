package com.chatapp.controller;

import com.chatapp.dto.FileUploadResponse;
import com.chatapp.dto.ApiResponse;
import com.chatapp.service.file.FileDeleteService;
import com.chatapp.service.file.FileQueryService;
import com.chatapp.service.file.FileStreamingService;
import com.chatapp.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件上传/下载控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;
    private final FileStreamingService fileStreamingService;
    private final FileQueryService fileQueryService;
    private final FileDeleteService fileDeletionService;

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
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("File is empty"));
            }

            log.info("Uploading file: name={}, contentType={}, size={} bytes",
                    file.getOriginalFilename(), file.getContentType(), file.getSize());

            FileUploadResponse response = fileUploadService.uploadFile(file, userId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "Invalid request" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("unsupported")
                    ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(message));
        } catch (Exception e) {
            log.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
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

            FileUploadResponse response = fileUploadService.uploadLivePhoto(jpeg, mov, userId);

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

            FileUploadResponse response = fileUploadService.uploadMotionPhoto(file, userId);

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
            fileStreamingService.streamFile(id, request, response);
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
            FileUploadResponse fileInfo = fileQueryService.getFileInfo(id);
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
            fileDeletionService.deleteFile(id);
            return ResponseEntity.ok(ApiResponse.success(null, "File deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete file: id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Delete failed: " + e.getMessage()));
        }
    }
}
