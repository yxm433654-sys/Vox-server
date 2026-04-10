package com.vox.infrastructure.media.motion;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
public class MotionPhotoMp4Detector {

    private static final int MIN_FILE_SIZE = 512 * 1024;
    private static final int TAIL_READ_SIZE = 4 * 1024 * 1024;
    private static final List<String> MP4_BRANDS = List.of("mp42", "isom", "avc1", "qt");

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

            byte[] tail = readTail(sourceFile, TAIL_READ_SIZE);
            int tailOffset = findMp4Start(tail);
            if (tailOffset >= 0) {
                long tailStart = Math.max(0, size - TAIL_READ_SIZE);
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

    private boolean hasMp4Ftyp(String value) {
        if (!value.contains("ftyp")) {
            return false;
        }
        return MP4_BRANDS.stream().anyMatch(value::contains);
    }

    private int findMp4Start(byte[] bytes) {
        if (bytes.length < 12) {
            return -1;
        }

        for (int i = 4; i <= bytes.length - 8; i += 1) {
            if (bytes[i] != 'f' || bytes[i + 1] != 't' || bytes[i + 2] != 'y' || bytes[i + 3] != 'p') {
                continue;
            }
            String brand = new String(bytes, i + 4, 4, StandardCharsets.US_ASCII).toLowerCase();
            if (!MP4_BRANDS.contains(brand)) {
                continue;
            }
            return Math.max(0, i - 4);
        }
        return -1;
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
