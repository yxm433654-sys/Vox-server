package com.chatapp.service.parser;

import com.chatapp.exception.ParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Android Motion Photo 解析器
 * 
 * Motion Photo格式:
 * - JPEG图像 + 内嵌MP4视频（视频追加在JPEG数据尾部）
 * - XMP元数据记录视频偏移和长度（XMP位于JPEG头部的APP1标记段中）
 * - 参考: Android开发者文档 Motion Photo规范
 */
@Slf4j
@Component
public class MotionPhotoParser {

    private static final String XMP_START = "<x:xmpmeta";
    private static final String XMP_END = "</x:xmpmeta>";
    private static final int INITIAL_SEARCH_SIZE = 65536; // 64KB — 头部初次搜索
    private static final int MAX_SEARCH_SIZE = 1024 * 1024; // 1MB — 扩大搜索上限

    /**
     * 检测文件是否为Motion Photo
     */
    public boolean isMotionPhoto(File file) {
        try {
            String xmp = extractXmpMetadata(file);
            return xmp != null && xmp.contains("GContainer:Item");
        } catch (Exception e) {
            log.debug("File is not a Motion Photo: {}", file.getName());
            return false;
        }
    }

    /**
     * 从JPEG中提取XMP元数据
     *
     * XMP存储在JPEG的APP1标记段中，位于文件头部（通常在前几KB范围内）。
     * 先搜索头部64KB，若未找到则扩大到1MB搜索整个元数据区域。
     */
    public String extractXmpMetadata(File jpegFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(jpegFile, "r")) {
            long fileSize = raf.length();

            // ── 第1轮: 从文件头部搜索（XMP在APP1中,位于文件开头） ──
            int searchSize = (int) Math.min(fileSize, INITIAL_SEARCH_SIZE);
            byte[] buffer = new byte[searchSize];
            raf.seek(0);
            raf.readFully(buffer);

            String content = new String(buffer, StandardCharsets.ISO_8859_1);
            int start = content.indexOf(XMP_START);
            int end = content.indexOf(XMP_END);

            if (start >= 0 && end > start) {
                log.debug("Found XMP in first {}KB of file", searchSize / 1024);
                return content.substring(start, end + XMP_END.length());
            }

            // ── 第2轮: 扩大搜索范围到1MB（兼容大EXIF/MakerNote的情况） ──
            if (fileSize > INITIAL_SEARCH_SIZE) {
                int extendedSize = (int) Math.min(fileSize, MAX_SEARCH_SIZE);
                byte[] extBuffer = new byte[extendedSize];
                raf.seek(0);
                int bytesRead = raf.read(extBuffer);
                content = new String(extBuffer, 0, bytesRead, StandardCharsets.ISO_8859_1);
                start = content.indexOf(XMP_START);
                end = content.indexOf(XMP_END);

                if (start >= 0 && end > start) {
                    log.debug("Found XMP in extended search ({}KB)", extendedSize / 1024);
                    return content.substring(start, end + XMP_END.length());
                }
            }

            // ── 第3轮: 少数机型(如部分三星)将XMP重复写在尾部，回退搜索 ──
            if (fileSize > INITIAL_SEARCH_SIZE) {
                long tailStart = Math.max(0, fileSize - INITIAL_SEARCH_SIZE);
                int tailSize = (int) (fileSize - tailStart);
                byte[] tailBuffer = new byte[tailSize];
                raf.seek(tailStart);
                raf.readFully(tailBuffer);
                content = new String(tailBuffer, StandardCharsets.ISO_8859_1);
                start = content.indexOf(XMP_START);
                end = content.indexOf(XMP_END);

                if (start >= 0 && end > start) {
                    log.debug("Found XMP in file tail (fallback)");
                    return content.substring(start, end + XMP_END.length());
                }
            }
        }

        log.debug("No XMP metadata found in: {}", jpegFile.getName());
        return null;
    }

    /**
     * 解析XMP元数据获取视频信息
     */
    public MotionPhotoMetadata parseMetadata(String xmp) throws ParseException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xmp)));

            MotionPhotoMetadata metadata = new MotionPhotoMetadata();

            // 查找GContainer:Item元素
            NodeList items = doc.getElementsByTagName("rdf:li");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);

                String mime = getAttrOrChild(item, "Item:Mime");
                String semantic = getAttrOrChild(item, "Item:Semantic");
                String length = getAttrOrChild(item, "Item:Length");

                if ("video/mp4".equals(mime) && "MotionPhoto".equals(semantic)) {
                    metadata.setVideoOffset(Long.parseLong(length));
                    metadata.setVideoMimeType(mime);
                    log.debug("Found Motion Photo video: length={}, mime={}", length, mime);
                }
            }

            if (metadata.getVideoOffset() == 0) {
                throw new ParseException("Video offset not found in XMP metadata");
            }

            return metadata;
        } catch (ParseException pe) {
            throw pe;
        } catch (Exception e) {
            throw new ParseException("Failed to parse XMP metadata", e);
        }
    }

    /**
     * 从Motion Photo中提取视频文件
     */
    public File extractVideo(File motionPhotoFile, File outputDir) throws IOException, ParseException {
        String xmp = extractXmpMetadata(motionPhotoFile);
        if (xmp == null) {
            throw new ParseException("No XMP metadata found");
        }

        MotionPhotoMetadata metadata = parseMetadata(xmp);
        long fileSize = motionPhotoFile.length();
        long videoStart = fileSize - metadata.getVideoOffset();

        log.info("Extracting video from Motion Photo: file={}, videoStart={}, videoSize={}",
                motionPhotoFile.getName(), videoStart, metadata.getVideoOffset());

        File outputFile = new File(outputDir,
                motionPhotoFile.getName().replaceFirst("\\.[^.]+$", "_video.mp4"));

        try (RandomAccessFile raf = new RandomAccessFile(motionPhotoFile, "r");
                FileOutputStream fos = new FileOutputStream(outputFile)) {

            raf.seek(videoStart);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = raf.read(buffer)) != -1
                    && totalRead < metadata.getVideoOffset()) {
                int toWrite = (int) Math.min(bytesRead, metadata.getVideoOffset() - totalRead);
                fos.write(buffer, 0, toWrite);
                totalRead += toWrite;
            }
        }

        log.info("Video extracted successfully: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 提取封面图(去除视频部分的纯JPEG)
     */
    public File extractCoverImage(File motionPhotoFile, File outputDir) throws IOException, ParseException {
        String xmp = extractXmpMetadata(motionPhotoFile);
        if (xmp == null) {
            throw new ParseException("No XMP metadata found");
        }

        MotionPhotoMetadata metadata = parseMetadata(xmp);
        long fileSize = motionPhotoFile.length();
        long imageSize = fileSize - metadata.getVideoOffset();

        File outputFile = new File(outputDir,
                motionPhotoFile.getName().replaceFirst("\\.[^.]+$", "_cover.jpg"));

        try (FileInputStream fis = new FileInputStream(motionPhotoFile);
                FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = fis.read(buffer)) != -1 && totalRead < imageSize) {
                int toWrite = (int) Math.min(bytesRead, imageSize - totalRead);
                fos.write(buffer, 0, toWrite);
                totalRead += toWrite;
            }
        }

        log.info("Cover image extracted: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 读取属性值：先尝试XML属性，再尝试子元素文本
     */
    private String getAttrOrChild(Element parent, String name) {
        // 尝试作为属性
        String val = parent.getAttribute(name);
        if (val != null && !val.isEmpty())
            return val;

        // 尝试作为子元素
        NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    /**
     * Motion Photo元数据
     */
    public static class MotionPhotoMetadata {
        private long videoOffset;
        private String videoMimeType;
        private String coverMimeType = "image/jpeg";

        public long getVideoOffset() {
            return videoOffset;
        }

        public void setVideoOffset(long videoOffset) {
            this.videoOffset = videoOffset;
        }

        public String getVideoMimeType() {
            return videoMimeType;
        }

        public void setVideoMimeType(String videoMimeType) {
            this.videoMimeType = videoMimeType;
        }

        public String getCoverMimeType() {
            return coverMimeType;
        }
    }
}
