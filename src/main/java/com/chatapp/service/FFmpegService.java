package com.chatapp.service;

import com.chatapp.exception.TranscodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FFmpeg视频转码服务
 * 
 * 统一转码为H.264/AAC MP4格式,确保跨平台兼容性
 */
@Slf4j
@Service
public class FFmpegService {

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

    private static final Pattern DURATION_PATTERN = 
            Pattern.compile("Duration: (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    /**
     * 转码视频为标准MP4格式
     */
    public File transcodeToMp4(File inputFile, File outputFile) throws TranscodeException {
        List<String> command = buildTranscodeCommand(inputFile, outputFile);
        
        log.info("Starting video transcode: input={}, output={}", 
                inputFile.getName(), outputFile.getName());
        log.debug("FFmpeg command: {}", String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // 读取输出流(用于日志和进度监控)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    
                    // 解析进度信息
                    if (line.contains("time=")) {
                        log.debug("FFmpeg progress: {}", line.trim());
                    }
                }
            }

            // 等待进程完成
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

            log.info("Transcode completed: output={}, size={} bytes", 
                    outputFile.getName(), outputFile.length());
            
            return outputFile;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscodeException("Transcode interrupted", e);
        } catch (IOException e) {
            throw new TranscodeException("Failed to execute FFmpeg", e);
        }
    }

    /**
     * 提取视频封面帧
     */
    public File extractCoverFrame(File videoFile, File outputFile, float timeSeconds) 
            throws TranscodeException {
        
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(videoFile.getAbsolutePath());
        command.add("-ss");
        command.add(String.valueOf(timeSeconds));
        command.add("-vframes");
        command.add("1");
        command.add("-q:v");
        command.add("2"); // 高质量JPEG
        command.add("-y");
        command.add(outputFile.getAbsolutePath());

        log.info("Extracting cover frame at {}s: input={}, output={}", 
                timeSeconds, videoFile.getName(), outputFile.getName());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                throw new TranscodeException("Cover extraction timeout");
            }

            if (process.exitValue() != 0 || !outputFile.exists()) {
                throw new TranscodeException("Failed to extract cover frame");
            }

            log.info("Cover frame extracted: {}, size={} bytes", 
                    outputFile.getName(), outputFile.length());
            
            return outputFile;

        } catch (Exception e) {
            throw new TranscodeException("Failed to extract cover frame", e);
        }
    }

    /**
     * 获取视频时长(秒)
     */
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
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(10, TimeUnit.SECONDS);

            // 解析时长
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

    /**
     * 构建转码命令
     */
    private List<String> buildTranscodeCommand(File inputFile, File outputFile) {
        List<String> command = new ArrayList<>();
        
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        
        // 视频编码
        command.add("-c:v");
        command.add(videoCodec);
        command.add("-preset");
        command.add(preset);
        command.add("-crf");
        command.add(String.valueOf(crf));
        
        // 音频编码
        command.add("-c:a");
        command.add(audioCodec);
        command.add("-b:a");
        command.add(audioBitrate);
        
        // 像素格式(最大兼容性)
        command.add("-vf");
        command.add("format=" + pixelFormat);
        
        // 优化流式播放
        command.add("-movflags");
        command.add("+faststart");
        
        // 覆盖输出文件
        command.add("-y");
        
        command.add(outputFile.getAbsolutePath());
        
        return command;
    }

    /**
     * 检查FFmpeg是否可用
     */
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
}
