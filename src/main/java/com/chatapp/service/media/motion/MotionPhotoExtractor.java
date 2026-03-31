package com.chatapp.service.media.motion;

import com.chatapp.exception.ParseException;

import java.io.File;
import java.io.IOException;

public interface MotionPhotoExtractor {
    boolean supports(File sourceFile);

    String sourceType();

    File extractVideo(File sourceFile, File outputDir) throws IOException, ParseException;

    File extractCoverImage(File sourceFile, File outputDir) throws IOException, ParseException;
}
