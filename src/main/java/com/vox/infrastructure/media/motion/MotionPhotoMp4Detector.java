package com.vox.infrastructure.media.motion;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Optional;

@Component
public class MotionPhotoMp4Detector {

    private static final int MIN_FILE_SIZE = 512 * 1024;
    private static final int MAX_TAIL_SCAN_SIZE = 32 * 1024 * 1024;
    private static final long MIN_MP4_BOX_SIZE = 16L;
    private static final Set<String> MP4_BRANDS = Set.of(
            "mp41", "mp42", "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
            "avc1", "hvc1", "hev1", "mif1", "msf1", "m4v ", "m4a ", "f4v ", "f4a ", "f4p ",
            "3gp4", "3gp5", "3gp6", "3g2a", "3g2b", "3g2c", "dash", "cmfc", "cmfs", "msnv", "qt  "
    );

    public boolean hasEmbeddedMp4(File sourceFile) {
        return detect(sourceFile).isPresent();
    }

    public Optional<EmbeddedMp4> detect(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            return Optional.empty();
        }

        try {
            long size = sourceFile.length();
            if (size < MIN_FILE_SIZE) {
                return Optional.empty();
            }

            int tailReadSize = (int) Math.min(size, MAX_TAIL_SCAN_SIZE);
            byte[] tail = readTail(sourceFile, tailReadSize);
            int tailOffset = findMp4Start(tail);
            if (tailOffset >= 0) {
                long tailStart = Math.max(0, size - tail.length);
                long videoStart = tailStart + tailOffset;
                long videoLength = size - videoStart;
                if (videoLength > 0) {
                    return Optional.of(new EmbeddedMp4(videoStart, videoLength));
                }
            }
            return Optional.empty();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private int findMp4Start(byte[] bytes) {
        if (bytes.length < 12) {
            return -1;
        }

        for (int i = 4; i <= bytes.length - 8; i += 1) {
            if (bytes[i] != 'f' || bytes[i + 1] != 't' || bytes[i + 2] != 'y' || bytes[i + 3] != 'p') {
                continue;
            }
            if (!hasValidMp4BoxHeader(bytes, i - 4)) {
                continue;
            }
            String brand = new String(bytes, i + 4, 4, StandardCharsets.US_ASCII).toLowerCase();
            if (!isLikelyMp4Brand(brand)) {
                continue;
            }
            return Math.max(0, i - 4);
        }
        return -1;
    }

    private boolean hasValidMp4BoxHeader(byte[] bytes, int boxStart) {
        if (boxStart < 0 || boxStart + 8 > bytes.length) {
            return false;
        }

        long boxSize = ((bytes[boxStart] & 0xFFL) << 24)
                | ((bytes[boxStart + 1] & 0xFFL) << 16)
                | ((bytes[boxStart + 2] & 0xFFL) << 8)
                | (bytes[boxStart + 3] & 0xFFL);
        if (boxSize == 1L) {
            return true;
        }

        long remaining = bytes.length - boxStart;
        return boxSize >= MIN_MP4_BOX_SIZE && boxSize <= remaining;
    }

    private boolean isLikelyMp4Brand(String brand) {
        if (brand == null || brand.length() != 4 || !isPrintableAscii(brand)) {
            return false;
        }
        return MP4_BRANDS.contains(brand)
                || brand.startsWith("mp4")
                || brand.startsWith("iso")
                || brand.startsWith("3g");
    }

    private boolean isPrintableAscii(String value) {
        for (int i = 0; i < value.length(); i += 1) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch > 0x7E) {
                return false;
            }
        }
        return true;
    }

    private byte[] readTail(File file, int maxBytes) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            long start = Math.max(0, length - maxBytes);
            raf.seek(start);
            int toRead = (int) Math.min(maxBytes, length - start);
            byte[] buffer = new byte[toRead];
            raf.readFully(buffer);
            return buffer;
        }
    }

    public File extractCoverImage(File sourceFile, File outputDir, EmbeddedMp4 embeddedMp4) throws IOException {
        File outputFile = new File(outputDir,
                sourceFile.getName().replaceFirst("\\.[^.]+$", "_cover.jpg"));
        copyRange(sourceFile, outputFile, 0, embeddedMp4.videoStart());
        return outputFile;
    }

    public File extractCoverImage(File sourceFile, File outputDir, long coverLength) throws IOException {
        File outputFile = new File(outputDir,
                sourceFile.getName().replaceFirst("\\.[^.]+$", "_cover.jpg"));
        copyRange(sourceFile, outputFile, 0, coverLength);
        return outputFile;
    }

    public File extractVideo(File sourceFile, File outputDir, EmbeddedMp4 embeddedMp4) throws IOException {
        File outputFile = new File(outputDir,
                sourceFile.getName().replaceFirst("\\.[^.]+$", "_video.mp4"));
        copyRange(sourceFile, outputFile, embeddedMp4.videoStart(), embeddedMp4.videoLength());
        return outputFile;
    }

    public File extractVideo(File sourceFile, File outputDir, long videoStart, long videoLength) throws IOException {
        File outputFile = new File(outputDir,
                sourceFile.getName().replaceFirst("\\.[^.]+$", "_video.mp4"));
        copyRange(sourceFile, outputFile, videoStart, videoLength);
        return outputFile;
    }

    private void copyRange(File sourceFile, File outputFile, long start, long length) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r");
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            long remaining = length;
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int bytesRead = raf.read(buffer, 0, toRead);
                if (bytesRead <= 0) {
                    break;
                }
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
    }

    public record EmbeddedMp4(long videoStart, long videoLength) {
    }
}
