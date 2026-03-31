package com.chatapp.service.file;

import com.chatapp.entity.FileResource;
import com.chatapp.repository.FileResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileDeleteService {

    private final FileResourceRepository fileResourceRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public void deleteFile(Long id) throws Exception {
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
