package com.chatapp.service.media;

import com.chatapp.exception.TranscodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MediaTranscodeService {

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${ffmpeg.timeout:300}")
    private int timeoutSeconds;

    @Value("${ffmpeg.codec.video:libx264}")
    private String videoCodec;

    @Value("${ffmpeg.codec.audio:aac}")
    private String audioCodec;

    @Value("${ffmpeg.preset:slow}")
    private String preset;

    @Value("${ffmpeg.crf:23}")
    private int crf;

    @Value("${ffmpeg.audio-bitrate:128k}")
    private String audioBitrate;

    @Value("${ffmpeg.pixel-format:yuv420p}")
    private String pixelFormat;

    @Value("${ffmpeg.cover-timeout:20}")
    private int coverTimeoutSeconds;

    public File transcodeToMp4(File inputFile, File outputFile) throws TranscodeException {
        List<String> command = buildTranscodeCommand(inputFile, outputFile);
        log.info("Starting video transcode: input={}, output={}", inputFile.getName(), outputFile.getName());
        log.debug("FFmpeg command: {}", String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("time=")) {
                        log.debug("FFmpeg progress: {}", line.trim());
                    }
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TranscodeException("Transcode timeout after " + timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("FFmpeg failed with exit code {}: {}", exitCode, output);
                throw new TranscodeException("Transcode failed with exit code: " + exitCode);
            }

            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new TranscodeException("Output file not created or empty");
            }

            log.info("Transcode completed: output={}, size={} bytes", outputFile.getName(), outputFile.length());
            return outputFile;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscodeException("Transcode interrupted", e);
        } catch (IOException e) {
            throw new TranscodeException("Failed to execute FFmpeg", e);
        }
    }

    public File extractCoverFrame(File videoFile, File outputFile, float timeSeconds) throws TranscodeException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-nostdin");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-ss");
        command.add(String.valueOf(Math.max(0f, timeSeconds)));
        command.add("-i");
        command.add(videoFile.getAbsolutePath());
        command.add("-frames:v");
        command.add("1");
        command.add("-q:v");
        command.add("2");
        command.add("-y");
        command.add(outputFile.getAbsolutePath());

        log.info("Extracting cover frame at {}s: input={}, output={}",
                timeSeconds, videoFile.getName(), outputFile.getName());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            Thread outputDrainer = drainOutput(process, output);
            outputDrainer.start();

            boolean finished = process.waitFor(coverTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputDrainer.join(1000);
                throw new TranscodeException("Cover extraction timeout: " + output);
            }

            outputDrainer.join(1000);
            if (process.exitValue() != 0 || !outputFile.exists() || outputFile.length() <= 0) {
                throw new TranscodeException("Failed to extract cover frame: " + output);
            }

            log.info("Cover frame extracted: {}, size={} bytes", outputFile.getName(), outputFile.length());
            return outputFile;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscodeException("Cover extraction interrupted", e);
        } catch (Exception e) {
            throw new TranscodeException("Failed to extract cover frame", e);
        }
    }

    public boolean checkFFmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.info("FFmpeg is available: {}", ffmpegPath);
                return true;
            }
        } catch (Exception e) {
            log.error("FFmpeg not available: {}", e.getMessage());
        }
        return false;
    }

    private Thread drainOutput(Process process, StringBuilder output) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
        }, "ffmpeg-cover-output");
    }

    private List<String> buildTranscodeCommand(File inputFile, File outputFile) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        command.add("-c:v");
        command.add(videoCodec);
        command.add("-preset");
        command.add(preset);
        command.add("-crf");
        command.add(String.valueOf(crf));
        command.add("-c:a");
        command.add(audioCodec);
        command.add("-b:a");
        command.add(audioBitrate);
        command.add("-vf");
        command.add("format=" + pixelFormat);
        command.add("-movflags");
        command.add("+faststart");
        command.add("-y");
        command.add(outputFile.getAbsolutePath());
        return command;
    }
}
