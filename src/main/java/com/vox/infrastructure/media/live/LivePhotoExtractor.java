package com.vox.infrastructure.media.live;

import com.vox.infrastructure.media.ParseException;

import java.io.File;

public interface LivePhotoExtractor {
    boolean supports(File jpegFile, File movFile);

    String sourceType();

    LivePhotoMetadata extract(File jpegFile, File movFile) throws ParseException;
}
