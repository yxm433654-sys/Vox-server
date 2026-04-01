package com.vox.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class AttachmentStorageUrlResolver implements StorageUrlResolver {

    private final String endpoint;
    private final String bucket;

    public AttachmentStorageUrlResolver(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.bucket}") String bucket
    ) {
        this.endpoint = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
        this.bucket = bucket;
    }

    @Override
    public String toClientUrl(Long resourceId, String storagePath) {
        if (resourceId == null) {
            return null;
        }
        if (isLoopbackEndpoint(endpoint)) {
            return "/api/files/" + resourceId;
        }
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }
        String normalized = storagePath.startsWith("/")
                ? storagePath.substring(1)
                : storagePath;
        return endpoint + "/" + bucket + "/" + normalized;
    }

    private static boolean isLoopbackEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return true;
            }
            String lower = host.toLowerCase();
            return "localhost".equals(lower)
                    || "127.0.0.1".equals(lower)
                    || "::1".equals(lower);
        } catch (Exception ignored) {
            return true;
        }
    }
}
