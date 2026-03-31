package com.chatapp.service.media.live;

import com.chatapp.exception.ParseException;
import com.chatapp.service.media.MediaSourceTypes;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.mov.QuickTimeDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AppleLivePhotoExtractor implements LivePhotoExtractor {

    private static final String XMP_NAMESPACE = "http://ns.adobe.com/xap/1.0/";
    private static final Pattern ASSET_ID_PATTERN =
            Pattern.compile("apple-fi:AssetIdentifier\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern CONTENT_ID_ATTR_PATTERN =
            Pattern.compile("apple-fi:ContentIdentifier\\s*=\\s*\"([^\"]+)\"");

    @Override
    public boolean supports(File jpegFile, File movFile) {
        return jpegFile != null && movFile != null && jpegFile.exists() && movFile.exists();
    }

    @Override
    public String sourceType() {
        return MediaSourceTypes.IOS_LIVE_PHOTO;
    }

    @Override
    public LivePhotoMetadata extract(File jpegFile, File movFile) throws ParseException {
        LivePhotoMetadata metadata = new LivePhotoMetadata();

        try {
            metadata.setAssetIdentifier(extractAssetIdentifier(jpegFile));
            metadata.setJpegFile(jpegFile);
            metadata.setContentIdentifier(extractContentIdentifier(movFile));
            metadata.setMovFile(movFile);

            Metadata movMetadata = ImageMetadataReader.readMetadata(movFile);
            for (Directory directory : movMetadata.getDirectories()) {
                if (directory instanceof QuickTimeDirectory qtDir) {
                    Integer duration = qtDir.getInteger(QuickTimeDirectory.TAG_DURATION);
                    Integer timeScale = qtDir.getInteger(QuickTimeDirectory.TAG_TIME_SCALE);
                    if (duration != null && timeScale != null && timeScale > 0) {
                        metadata.setDuration((float) duration / timeScale);
                    }
                }
            }

            metadata.setVerified(verifyLivePhoto(jpegFile, movFile));
            return metadata;
        } catch (Exception e) {
            throw new ParseException("Failed to extract Live Photo metadata", e);
        }
    }

    public String extractAssetIdentifier(File jpegFile) throws ParseException {
        try {
            String xmp = extractXmpFromJpeg(jpegFile);
            if (xmp != null) {
                Matcher matcher = ASSET_ID_PATTERN.matcher(xmp);
                if (matcher.find()) {
                    return matcher.group(1);
                }
                matcher = CONTENT_ID_ATTR_PATTERN.matcher(xmp);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }

            Metadata metadata = ImageMetadataReader.readMetadata(jpegFile);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    String description = tag.getDescription();
                    if (description == null || description.isEmpty()) {
                        continue;
                    }
                    String name = tag.getTagName();
                    if ("Content Identifier".equalsIgnoreCase(name)
                            || "Asset Identifier".equalsIgnoreCase(name)) {
                        return description;
                    }
                }
            }

            String fileName = jpegFile.getName();
            return fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;
        } catch (Exception e) {
            throw new ParseException("Failed to extract asset identifier from JPEG", e);
        }
    }

    public String extractContentIdentifier(File movFile) throws ParseException {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(movFile);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    String tagName = tag.getTagName();
                    String description = tag.getDescription();
                    if (description == null || description.isEmpty() || tagName == null) {
                        continue;
                    }
                    String lower = tagName.toLowerCase();
                    if (lower.contains("content identifier")
                            || lower.equals("com.apple.quicktime.content.identifier")) {
                        return description;
                    }
                }
            }

            String fileName = movFile.getName();
            return fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;
        } catch (Exception e) {
            throw new ParseException("Failed to extract content identifier from MOV", e);
        }
    }

    public boolean verifyLivePhoto(File jpegFile, File movFile) {
        try {
            String jpegId = extractAssetIdentifier(jpegFile);
            String movId = extractContentIdentifier(movFile);
            return jpegId != null && jpegId.equals(movId);
        } catch (Exception e) {
            log.error("Failed to verify Live Photo", e);
            return false;
        }
    }

    private String extractXmpFromJpeg(File jpegFile) {
        try (RandomAccessFile raf = new RandomAccessFile(jpegFile, "r")) {
            int b1 = raf.read();
            int b2 = raf.read();
            if (b1 != 0xFF || b2 != 0xD8) {
                return null;
            }

            while (raf.getFilePointer() < raf.length() - 4) {
                int m1 = raf.read();
                if (m1 != 0xFF) {
                    continue;
                }
                int m2 = raf.read();
                if (m2 == 0xDA) {
                    break;
                }
                if (m2 == 0x00 || m2 == 0x01
                        || (m2 >= 0xD0 && m2 <= 0xD7) || m2 == 0xD8 || m2 == 0xD9) {
                    continue;
                }

                int lenHigh = raf.read();
                int lenLow = raf.read();
                int segLength = ((lenHigh & 0xFF) << 8) | (lenLow & 0xFF);
                if (segLength < 2) {
                    break;
                }
                int dataLength = segLength - 2;

                if (m2 == 0xE1 && dataLength > XMP_NAMESPACE.length() + 1) {
                    byte[] data = new byte[dataLength];
                    raf.readFully(data);
                    String content = new String(data, StandardCharsets.ISO_8859_1);
                    if (content.startsWith(XMP_NAMESPACE)) {
                        int nullPos = content.indexOf('\0');
                        return nullPos >= 0
                                ? content.substring(nullPos + 1)
                                : content.substring(XMP_NAMESPACE.length());
                    }
                } else {
                    raf.skipBytes(dataLength);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read XMP from JPEG: {}", e.getMessage());
        }
        return null;
    }
}
