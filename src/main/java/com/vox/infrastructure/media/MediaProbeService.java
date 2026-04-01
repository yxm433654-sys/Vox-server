package com.vox.infrastructure.media;

import com.vox.infrastructure.media.TranscodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MediaProbeService {

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("Duration: (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("(?i)\\b(\\d{2,5})x(\\d{2,5})\\b");

    public float getVideoDuration(File videoFile) throws TranscodeException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(videoFile.getAbsolutePath());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(10, TimeUnit.SECONDS);

            Matcher matcher = DURATION_PATTERN.matcher(output);
            if (matcher.find()) {
                int hours = Integer.parseInt(matcher.group(1));
                int minutes = Integer.parseInt(matcher.group(2));
                int seconds = Integer.parseInt(matcher.group(3));
                int centiseconds = Integer.parseInt(matcher.group(4));
                float duration = hours * 3600 + minutes * 60 + seconds + centiseconds / 100f;
                log.debug("Video duration: {} seconds", duration);
                return duration;
            }

            throw new TranscodeException("Could not parse video duration");
        } catch (Exception e) {
            throw new TranscodeException("Failed to get video duration", e);
        }
    }

    public int[] getVideoDimensions(File videoFile) throws TranscodeException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(videoFile.getAbsolutePath());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(10, TimeUnit.SECONDS);

            String text = output.toString();
            for (String line : text.split("\\R")) {
                if (!line.toLowerCase().contains("video:")) {
                    continue;
                }
                Matcher matcher = DIMENSION_PATTERN.matcher(line);
                if (matcher.find()) {
                    int width = Integer.parseInt(matcher.group(1));
                    int height = Integer.parseInt(matcher.group(2));
                    if (width > 0 && height > 0) {
                        return new int[]{width, height};
                    }
                }
            }

            Matcher matcher = DIMENSION_PATTERN.matcher(text);
            if (matcher.find()) {
                int width = Integer.parseInt(matcher.group(1));
                int height = Integer.parseInt(matcher.group(2));
                if (width > 0 && height > 0) {
                    return new int[]{width, height};
                }
            }

            throw new TranscodeException("Could not parse video dimensions");
        } catch (Exception e) {
            throw new TranscodeException("Failed to get video dimensions", e);
        }
    }
}
