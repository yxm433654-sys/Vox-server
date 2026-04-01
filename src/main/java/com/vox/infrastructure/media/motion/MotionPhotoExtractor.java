package com.vox.infrastructure.media.motion;

import com.vox.infrastructure.media.ParseException;

import java.io.File;
import java.io.IOException;

public interface MotionPhotoExtractor {
    boolean supports(File sourceFile);

    String sourceType();

    File extractVideo(File sourceFile, File outputDir) throws IOException, ParseException;

    File extractCoverImage(File sourceFile, File outputDir) throws IOException, ParseException;
}
