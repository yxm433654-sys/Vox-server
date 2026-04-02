package com.vox.infrastructure.storage;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Resolves MIME type from declared content-type, filename, and magic bytes.
 * Extracted from AttachmentCommandService to be independently testable.
 */
@Component
public class MimeTypeResolver {

    public String resolve(String declaredContentType, String originalFilename, File tempFile)
            throws IOException {
        String normalized = normalizeMime(declaredContentType);
        if (normalized != null && !"application/octet-stream".equals(normalized)) {
            return normalized;
        }
        String fromName = fromFilename(originalFilename);
        if (fromName != null) {
            return fromName;
        }
        String fromMagic = fromMagicBytes(tempFile);
        if (fromMagic != null) {
            return fromMagic;
        }
        return normalized;
    }

    public String normalizeMime(String contentType) {
        if (contentType == null) {
            return null;
        }
        String value = contentType.trim();
        if (value.isEmpty()) {
            return null;
        }
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon);
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public String fromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String name = filename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "mp4" -> "video/mp4";
            case "m4v" -> "video/x-m4v";
            case "mov", "qt" -> "video/quicktime";
            case "3gp" -> "video/3gpp";
            case "3g2" -> "video/3gpp2";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "zip" -> "application/zip";
            case "rar" -> "application/vnd.rar";
            case "7z" -> "application/x-7z-compressed";
            default -> null;
        };
    }

    public String fromMagicBytes(File file) throws IOException {
        byte[] header = new byte[16];
        int read;
        try (InputStream in = new FileInputStream(file)) {
            read = in.read(header);
        }
        if (read <= 0) {
            return null;
        }
        // JPEG: FF D8 FF
        if (read >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (read >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A
                && header[6] == 0x1A && header[7] == 0x0A) {
            return "image/png";
        }
        // GIF: GIF87a / GIF89a
        if (read >= 6
                && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                && header[3] == '8' && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return "image/gif";
        }
        // WebP: RIFF????WEBP
        if (read >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        // ISO Base Media (MP4/MOV/HEIC): offset 4 = ftyp
        if (read >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            String brand = new String(header, 8, 4, StandardCharsets.US_ASCII);
            if ("qt  ".equals(brand)) {
                return "video/quicktime";
            }
            if (brand.startsWith("3gp")) {
                return "video/3gpp";
            }
            if (isHeifBrand(brand)) {
                return "image/heic";
            }
            return "video/mp4";
        }
        return null;
    }

    private boolean isHeifBrand(String brand) {
        return "heic".equals(brand) || "heix".equals(brand)
                || "hevc".equals(brand) || "hevx".equals(brand)
                || "mif1".equals(brand) || "msf1".equals(brand);
    }
}
