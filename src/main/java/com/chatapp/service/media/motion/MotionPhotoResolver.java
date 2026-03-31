package com.chatapp.service.media.motion;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class MotionPhotoResolver {

    private final List<MotionPhotoExtractor> extractors;

    public MotionPhotoResolver(List<MotionPhotoExtractor> extractors) {
        this.extractors = extractors;
    }

    public MotionPhotoExtractor resolve(File sourceFile) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(sourceFile))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No motion photo extractor supports the provided file"));
    }
}
