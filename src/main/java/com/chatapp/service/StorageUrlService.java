package com.chatapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
}
