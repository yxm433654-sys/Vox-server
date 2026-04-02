package com.vox.infrastructure.media;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Schedules dynamic-photo processing after the current transaction commits.
 *
 * Actual async execution is delegated to DynamicPhotoProcessingExecutor, which is a
 * separate Spring bean. This eliminates the ObjectProvider<Self> pattern that was
 * required to make @Async work via the proxy when calling methods on 'this'.
 */
@Service
@RequiredArgsConstructor
public class DynamicPhotoProcessingService {

    private final DynamicPhotoProcessingExecutor executor;

    public void scheduleLivePhotoProcessing(
            String jpegPath, String movPath, Long coverId, Long videoId) {
        runAfterCommit(() -> executor.processLivePhoto(jpegPath, movPath, coverId, videoId));
    }

    public void scheduleMotionPhotoProcessing(
            String sourcePath, Long coverId, Long videoId) {
        runAfterCommit(() -> executor.processMotionPhoto(sourcePath, coverId, videoId));
    }

    private void runAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            runnable.run();
                        }
                    });
        } else {
            runnable.run();
        }
    }
}
