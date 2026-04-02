package com.vox.infrastructure.storage;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import com.vox.infrastructure.storage.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentStreamingService {

    private final FileResourceRepository fileResourceRepository;
    private final FileStorageService fileStorageService;

    public void streamAttachment(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        String contentType = resource.getMimeType();
        response.setContentType(contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType);
        response.setHeader("Accept-Ranges", "bytes");

        long totalSize = fileStorageService.resolveObjectSize(resource);
        String range = request.getHeader("Range");
        if (range == null || range.isBlank() || totalSize <= 0) {
            if (totalSize > 0) {
                response.setContentLengthLong(totalSize);
            }
            try (InputStream stream = fileStorageService.openObject(resource.getStoragePath())) {
                transferToClient(stream, response, resource.getId());
            }
            return;
        }

        long[] resolved = resolveRange(range, totalSize);
        long start = resolved[0];
        long end = resolved[1];
        if (start < 0 || start >= totalSize || end < start) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + totalSize);
            return;
        }

        long length = end - start + 1;
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);
        response.setContentLengthLong(length);

        try (InputStream stream = fileStorageService.openObjectRange(resource.getStoragePath(), start, length)) {
            transferToClient(stream, response, resource.getId());
        }
    }


    private void transferToClient(InputStream stream, HttpServletResponse response, Long resourceId) throws IOException {
        try {
            stream.transferTo(response.getOutputStream());
        } catch (IOException exception) {
            if (isClientAbort(exception)) {
                log.debug("Attachment stream interrupted by client for resource {}", resourceId);
                return;
            }
            throw exception;
        }
    }

    private boolean isClientAbort(IOException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset")
                || normalized.contains("forcibly closed")
                || normalized.contains("abort")
                || normalized.contains("closed");
    }

    private static long[] resolveRange(String rangeHeader, long totalSize) {
        String value = rangeHeader.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("bytes=")) {
            return new long[]{-1, -1};
        }
        String spec = value.substring("bytes=".length()).trim();
        int comma = spec.indexOf(',');
        if (comma >= 0) {
            spec = spec.substring(0, comma).trim();
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return new long[]{-1, -1};
        }
        String startStr = spec.substring(0, dash).trim();
        String endStr = spec.substring(dash + 1).trim();

        try {
            if (startStr.isEmpty()) {
                long suffix = Long.parseLong(endStr);
                if (suffix <= 0) {
                    return new long[]{-1, -1};
                }
                long start = Math.max(0, totalSize - suffix);
                long end = totalSize - 1;
                return new long[]{start, end};
            }
            long start = Long.parseLong(startStr);
            long end = endStr.isEmpty() ? (totalSize - 1) : Long.parseLong(endStr);
            if (end >= totalSize) {
                end = totalSize - 1;
            }
            return new long[]{start, end};
        } catch (Exception ignored) {
            return new long[]{-1, -1};
        }
    }
}

