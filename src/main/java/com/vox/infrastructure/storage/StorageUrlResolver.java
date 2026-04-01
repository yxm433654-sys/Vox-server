package com.vox.infrastructure.storage;

public interface StorageUrlResolver {
    String toClientUrl(Long resourceId, String storagePath);
}
