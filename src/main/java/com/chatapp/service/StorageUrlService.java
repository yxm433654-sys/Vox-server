package com.chatapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class StorageUrlService {

    private final String endpoint;
    private final String bucket;

    public StorageUrlService(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.bucket}") String bucket
    ) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucket = bucket;
    }

    public String toPublicUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        String normalized = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        return endpoint + "/" + bucket + "/" + normalized;
    }

    public String toFileApiPath(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return "/api/files/" + fileId;
    }

    public String toClientUrl(Long fileId, String objectName) {
        if (fileId == null) {
            return null;
        }
        if (isLoopbackEndpoint(endpoint)) {
            return toFileApiPath(fileId);
        }
        return toPublicUrl(objectName);
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
            String h = host.toLowerCase();
            return "localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h);
        } catch (Exception ignored) {
            return true;
        }
    }
}
