package com.chatapp.service.parser;

import com.chatapp.exception.ParseException;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;
import com.drew.metadata.mov.QuickTimeDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * iOS Live Photo 解析器
 * 
 * Live Photo格式:
 * - JPEG图像文件 (含XMP中的apple-fi:AssetIdentifier)
 * - MOV视频文件 (QuickTime metadata中的content.identifier)
 * - 两者通过相同的标识符UUID关联
 *
 * 解析策略(按优先级):
 * 1. 从JPEG的APP1 XMP段提取 apple-fi:AssetIdentifier
 * 2. 用metadata-extractor遍历MakerNote查找 "Content Identifier"
 * 3. 文件名前缀后备
 */
@Slf4j
@Component
public class LivePhotoParser {

    private static final String XMP_NAMESPACE = "http://ns.adobe.com/xap/1.0/";

    /** 匹配 apple-fi:AssetIdentifier="UUID" */
    private static final Pattern ASSET_ID_PATTERN = Pattern.compile(
            "apple-fi:AssetIdentifier\\s*=\\s*\"([^\"]+)\"");

    /** 匹配 apple-fi:ContentIdentifier="UUID"（某些旧版固件使用这个名称） */
    private static final Pattern CONTENT_ID_ATTR_PATTERN = Pattern.compile(
            "apple-fi:ContentIdentifier\\s*=\\s*\"([^\"]+)\"");

    // ─────────────── JPEG 端 ───────────────

    /**
     * 从JPEG中提取Live Photo标识符
     * 
     * 策略:
     * 1) 解析JPEG APP1中的XMP，正则匹配 apple-fi:AssetIdentifier
     * 2) metadata-extractor遍历所有Directory，精确匹配 "Content Identifier" tag名
     * 3) 文件名前缀后备
     */
    public String extractAssetIdentifier(File jpegFile) throws ParseException {
        try {
            // ---- 策略1: XMP ----
            String xmp = extractXmpFromJpeg(jpegFile);
            if (xmp != null) {
                Matcher m = ASSET_ID_PATTERN.matcher(xmp);
                if (m.find()) {
                    String id = m.group(1);
                    log.info("Found AssetIdentifier in XMP: {}", id);
                    return id;
                }
                m = CONTENT_ID_ATTR_PATTERN.matcher(xmp);
                if (m.find()) {
                    String id = m.group(1);
                    log.info("Found ContentIdentifier in XMP: {}", id);
                    return id;
                }
            }

            // ---- 策略2: metadata-extractor 全目录遍历 ----
            Metadata metadata = ImageMetadataReader.readMetadata(jpegFile);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    String desc = tag.getDescription();
                    if (desc == null || desc.isEmpty())
                        continue;
                    String name = tag.getTagName();
                    if ("Content Identifier".equalsIgnoreCase(name)
                            || "Asset Identifier".equalsIgnoreCase(name)) {
                        log.info("Found identifier via metadata-extractor: {}={}", name, desc);
                        return desc;
                    }
                }
            }

            // ---- 策略3: 文件名后备 ----
            String fileName = jpegFile.getName();
            String baseName = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;
            log.warn("AssetIdentifier not found in metadata, using filename: {}", baseName);
            return baseName;

        } catch (Exception e) {
            throw new ParseException("Failed to extract asset identifier from JPEG", e);
        }
    }

    // ─────────────── MOV 端 ───────────────

    /**
     * 从MOV中提取Live Photo标识符
     * 
     * QuickTime mdta handler记录了 com.apple.quicktime.content.identifier ，
     * metadata-extractor会将其解析到 QuickTimeMetadataDirectory 中。
     * 这里遍历所有Directory以兼容不同版本的metadata-extractor。
     */
    public String extractContentIdentifier(File movFile) throws ParseException {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(movFile);

            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    String tagName = tag.getTagName();
                    String desc = tag.getDescription();
                    if (desc == null || desc.isEmpty())
                        continue;
                    if (tagName == null)
                        continue;

                    String lower = tagName.toLowerCase();
                    // metadata-extractor 解析后的tag名可能是:
                    // "Content Identifier"
                    // "com.apple.quicktime.content.identifier"
                    if (lower.contains("content identifier")
                            || lower.equals("com.apple.quicktime.content.identifier")) {
                        log.info("Found content identifier in MOV: {}={}", tagName, desc);
                        return desc;
                    }
                }
            }

            // 文件名后备
            String fileName = movFile.getName();
            String baseName = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;
            log.warn("Content identifier not found in MOV metadata, using filename: {}", baseName);
            return baseName;

        } catch (Exception e) {
            throw new ParseException("Failed to extract content identifier from MOV", e);
        }
    }

    // ─────────────── XMP 底层读取 ───────────────

    /**
     * 从JPEG文件中提取XMP数据
     *
     * 直接解析JPEG标记结构，定位APP1段中以
     * "http://ns.adobe.com/xap/1.0/" 开头的XMP数据，
     * 避免依赖metadata-extractor对Apple私有XMP的支持程度。
     */
    private String extractXmpFromJpeg(File jpegFile) {
        try (RandomAccessFile raf = new RandomAccessFile(jpegFile, "r")) {
            // 验证 SOI (0xFFD8)
            int b1 = raf.read();
            int b2 = raf.read();
            if (b1 != 0xFF || b2 != 0xD8) {
                log.debug("Not a valid JPEG file: {}", jpegFile.getName());
                return null;
            }

            // 逐段遍历JPEG标记
            while (raf.getFilePointer() < raf.length() - 4) {
                int m1 = raf.read();
                if (m1 != 0xFF)
                    continue;
                int m2 = raf.read();

                // SOS(0xDA) 之后是压缩图像数据，不再有元数据段
                if (m2 == 0xDA)
                    break;

                // 跳过无长度标记(RST, SOI, EOI等)
                if (m2 == 0x00 || m2 == 0x01
                        || (m2 >= 0xD0 && m2 <= 0xD7) || m2 == 0xD8 || m2 == 0xD9) {
                    continue;
                }

                // 读取段长度(大端序, 包含自身2字节)
                int lenHigh = raf.read();
                int lenLow = raf.read();
                int segLength = ((lenHigh & 0xFF) << 8) | (lenLow & 0xFF);
                if (segLength < 2)
                    break;
                int dataLength = segLength - 2;

                // APP1 = 0xFFE1
                if (m2 == 0xE1 && dataLength > XMP_NAMESPACE.length() + 1) {
                    byte[] data = new byte[dataLength];
                    raf.readFully(data);
                    String content = new String(data, StandardCharsets.ISO_8859_1);

                    if (content.startsWith(XMP_NAMESPACE)) {
                        // XMP namespace 后跟一个 null 字节, 之后是XML正文
                        int nullPos = content.indexOf('\0');
                        String xmpXml = (nullPos >= 0)
                                ? content.substring(nullPos + 1)
                                : content.substring(XMP_NAMESPACE.length());
                        log.debug("Extracted XMP from JPEG APP1, length={}", xmpXml.length());
                        return xmpXml;
                    }
                    // 不是XMP的APP1段(可能是Exif), 继续
                } else {
                    raf.skipBytes(dataLength);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read XMP from JPEG: {}", e.getMessage());
        }
        return null;
    }

    // ─────────────── 校验 & 元数据 ───────────────

    /**
     * 验证JPEG和MOV是否为配对的Live Photo
     */
    public boolean verifyLivePhoto(File jpegFile, File movFile) {
        try {
            String jpegId = extractAssetIdentifier(jpegFile);
            String movId = extractContentIdentifier(movFile);

            if (jpegId == null || movId == null) {
                log.warn("Cannot verify Live Photo: missing identifiers");
                return false;
            }

            boolean matched = jpegId.equals(movId);

            if (matched) {
                log.info("Live Photo verified: JPEG={}, MOV={}, identifier={}",
                        jpegFile.getName(), movFile.getName(), jpegId);
            } else {
                log.warn("Live Photo mismatch: JPEG_ID={}, MOV_ID={}", jpegId, movId);
            }

            return matched;

        } catch (Exception e) {
            log.error("Failed to verify Live Photo", e);
            return false;
        }
    }

    /**
     * 提取Live Photo的所有元数据
     */
    public LivePhotoMetadata extractMetadata(File jpegFile, File movFile) throws ParseException {
        LivePhotoMetadata meta = new LivePhotoMetadata();

        try {
            meta.setAssetIdentifier(extractAssetIdentifier(jpegFile));
            meta.setJpegFile(jpegFile);
            meta.setContentIdentifier(extractContentIdentifier(movFile));
            meta.setMovFile(movFile);

            // 提取视频时长
            Metadata movMeta = ImageMetadataReader.readMetadata(movFile);
            for (Directory directory : movMeta.getDirectories()) {
                if (directory instanceof QuickTimeDirectory qtDir) {
                    Integer duration = qtDir.getInteger(QuickTimeDirectory.TAG_DURATION);
                    Integer timeScale = qtDir.getInteger(QuickTimeDirectory.TAG_TIME_SCALE);
                    if (duration != null && timeScale != null && timeScale > 0) {
                        float durationSeconds = (float) duration / timeScale;
                        meta.setDuration(durationSeconds);
                        log.debug("Video duration: {} seconds", durationSeconds);
                    }
                }
            }

            meta.setVerified(verifyLivePhoto(jpegFile, movFile));

        } catch (Exception e) {
            throw new ParseException("Failed to extract Live Photo metadata", e);
        }

        return meta;
    }

    // ─────────────── 内部数据类 ───────────────

    /**
     * Live Photo元数据
     */
    public static class LivePhotoMetadata {
        private String assetIdentifier;
        private String contentIdentifier;
        private File jpegFile;
        private File movFile;
        private float duration;
        private boolean verified;

        public String getAssetIdentifier() {
            return assetIdentifier;
        }

        public void setAssetIdentifier(String assetIdentifier) {
            this.assetIdentifier = assetIdentifier;
        }

        public String getContentIdentifier() {
            return contentIdentifier;
        }

        public void setContentIdentifier(String contentIdentifier) {
            this.contentIdentifier = contentIdentifier;
        }

        public File getJpegFile() {
            return jpegFile;
        }

        public void setJpegFile(File jpegFile) {
            this.jpegFile = jpegFile;
        }

        public File getMovFile() {
            return movFile;
        }

        public void setMovFile(File movFile) {
            this.movFile = movFile;
        }

        public float getDuration() {
            return duration;
        }

        public void setDuration(float duration) {
            this.duration = duration;
        }

        public boolean isVerified() {
            return verified;
        }

        public void setVerified(boolean verified) {
            this.verified = verified;
        }
    }
}
