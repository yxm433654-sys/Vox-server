package com.vox.infrastructure.storage;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import com.vox.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttachmentDeleteService {

    private final FileResourceRepository fileResourceRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public void deleteAttachment(Long id) throws Exception {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        fileStorageService.deleteObjectQuietly(resource.getStoragePath());
        fileResourceRepository.delete(resource);

        Long relatedId = resource.getRelatedFileId();
        if (relatedId != null) {
            fileResourceRepository.findById(relatedId).ifPresent(related -> {
                try {
                    fileStorageService.deleteObjectQuietly(related.getStoragePath());
                } catch (Exception ignored) {
                }
                fileResourceRepository.delete(related);
            });
        }
    }
}

