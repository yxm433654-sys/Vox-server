package com.vox.infrastructure.media;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Manages video cover placeholder creation and schedules async cover generation.
 *
 * Async execution is now delegated to VideoCoverGenerationExecutor, eliminating the
 * ObjectProvider<Self> workaround previously needed for @Async proxy invocation.
 */
@Service
@RequiredArgsConstructor
public class VideoCoverWorkflowService {

    private final FileResourceRepository fileResourceRepository;
    private final VideoCoverGenerationExecutor coverGenerationExecutor;

    public FileResource createPlaceholderAndSchedule(
            FileResource videoResource, String originalName, Long userId) {
        if (videoResource == null || videoResource.getId() == null
                || videoResource.getStoragePath() == null) {
            return null;
        }

        String coverObjectName = "video/cover_" + UUID.randomUUID() + ".jpg";

        FileResource cover = new FileResource();
        cover.setOriginalName(originalName);
        cover.setStoragePath(coverObjectName);
        cover.setStoredName(extractStoredName(coverObjectName));
        cover.setFileType(FileResource.FileType.IMAGE);
        cover.setSourceType("VideoCoverPending");
        cover.setMimeType("image/jpeg");
        cover.setFileSize(0L);
        cover.setUploaderId(userId);
        cover.setRelatedFileId(videoResource.getId());
        FileResource savedCover = fileResourceRepository.save(cover);

        videoResource.setRelatedFileId(savedCover.getId());
        fileResourceRepository.save(videoResource);

        final Long videoId = videoResource.getId();
        final Long coverId = savedCover.getId();
        Runnable trigger = () -> coverGenerationExecutor.regenerateCover(videoId, coverId);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            trigger.run();
                        }
                    });
        } else {
            trigger.run();
        }

        return savedCover;
    }

    private String extractStoredName(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }
        int slash = storagePath.lastIndexOf('/');
        return slash >= 0 ? storagePath.substring(slash + 1) : storagePath;
    }
}
