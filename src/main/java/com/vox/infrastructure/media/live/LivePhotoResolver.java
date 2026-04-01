package com.vox.infrastructure.media.live;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class LivePhotoResolver {

    private final List<LivePhotoExtractor> extractors;

    public LivePhotoResolver(List<LivePhotoExtractor> extractors) {
        this.extractors = extractors;
    }

    public LivePhotoExtractor resolve(File jpegFile, File movFile) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(jpegFile, movFile))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No live photo extractor supports the provided files"));
    }
}
