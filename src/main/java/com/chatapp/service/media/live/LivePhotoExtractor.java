package com.chatapp.service.media.live;

import com.chatapp.exception.ParseException;

import java.io.File;

public interface LivePhotoExtractor {
    boolean supports(File jpegFile, File movFile);

    String sourceType();

    LivePhotoMetadata extract(File jpegFile, File movFile) throws ParseException;
}
