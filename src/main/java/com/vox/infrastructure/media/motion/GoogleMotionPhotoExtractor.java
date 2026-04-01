package com.vox.infrastructure.media.motion;

import com.vox.infrastructure.media.ParseException;
import com.vox.infrastructure.media.MediaSourceTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class GoogleMotionPhotoExtractor implements MotionPhotoExtractor {

    private static final String XMP_START = "<x:xmpmeta";
    private static final String XMP_END = "</x:xmpmeta>";
    private static final int INITIAL_SEARCH_SIZE = 65536;
    private static final int MAX_SEARCH_SIZE = 1024 * 1024;

    @Override
    public boolean supports(File sourceFile) {
        return sourceFile != null && sourceFile.exists() && isMotionPhoto(sourceFile);
    }

    @Override
    public String sourceType() {
        return MediaSourceTypes.ANDROID_MOTION_PHOTO;
    }

    @Override
    public File extractVideo(File sourceFile, File outputDir) throws IOException, ParseException {
        String xmp = extractXmpMetadata(sourceFile);
        if (xmp == null) {
            throw new ParseException("No XMP metadata found");
        }

        MotionPhotoMetadata metadata = parseMetadata(xmp);
        long fileSize = sourceFile.length();
        long videoStart = fileSize - metadata.getVideoOffset();

        File outputFile = new File(outputDir,
                sourceFile.getName().replaceFirst("\\.[^.]+$", "_video.mp4"));

        try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r");
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            raf.seek(videoStart);
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = raf.read(buffer)) != -1 && totalRead < metadata.getVideoOffset()) {
                int toWrite = (int) Math.min(bytesRead, metadata.getVideoOffset() - totalRead);
                fos.write(buffer, 0, toWrite);
                totalRead += toWrite;
            }
        }

        return outputFile;
    }

    @Override
    public File extractCoverImage(File sourceFile, File outputDir) throws IOException, ParseException {
        String xmp = extractXmpMetadata(sourceFile);
        if (xmp == null) {
            throw new ParseException("No XMP metadata found");
        }

        MotionPhotoMetadata metadata = parseMetadata(xmp);
        long imageSize = sourceFile.length() - metadata.getVideoOffset();

        File outputFile = new File(outputDir,
                sourceFile.getName().replaceFirst("\\.[^.]+$", "_cover.jpg"));

        try (FileInputStream fis = new FileInputStream(sourceFile);
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

        return outputFile;
    }

    private boolean isMotionPhoto(File file) {
        try {
            String xmp = extractXmpMetadata(file);
            return xmp != null && xmp.contains("GContainer:Item");
        } catch (Exception e) {
            log.debug("File is not a Motion Photo: {}", file.getName());
            return false;
        }
    }

    private String extractXmpMetadata(File jpegFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(jpegFile, "r")) {
            long fileSize = raf.length();

            int searchSize = (int) Math.min(fileSize, INITIAL_SEARCH_SIZE);
            byte[] buffer = new byte[searchSize];
            raf.seek(0);
            raf.readFully(buffer);

            String content = new String(buffer, StandardCharsets.ISO_8859_1);
            int start = content.indexOf(XMP_START);
            int end = content.indexOf(XMP_END);
            if (start >= 0 && end > start) {
                return content.substring(start, end + XMP_END.length());
            }

            if (fileSize > INITIAL_SEARCH_SIZE) {
                int extendedSize = (int) Math.min(fileSize, MAX_SEARCH_SIZE);
                byte[] extBuffer = new byte[extendedSize];
                raf.seek(0);
                int bytesRead = raf.read(extBuffer);
                content = new String(extBuffer, 0, bytesRead, StandardCharsets.ISO_8859_1);
                start = content.indexOf(XMP_START);
                end = content.indexOf(XMP_END);
                if (start >= 0 && end > start) {
                    return content.substring(start, end + XMP_END.length());
                }
            }

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
                    return content.substring(start, end + XMP_END.length());
                }
            }
        }
        return null;
    }

    private MotionPhotoMetadata parseMetadata(String xmp) throws ParseException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xmp)));

            MotionPhotoMetadata metadata = new MotionPhotoMetadata();
            NodeList items = doc.getElementsByTagName("rdf:li");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);

                String mime = getAttrOrChild(item, "Item:Mime");
                String semantic = getAttrOrChild(item, "Item:Semantic");
                String length = getAttrOrChild(item, "Item:Length");

                if ("video/mp4".equals(mime) && "MotionPhoto".equals(semantic)) {
                    metadata.setVideoOffset(Long.parseLong(length));
                    metadata.setVideoMimeType(mime);
                }
            }

            if (metadata.getVideoOffset() == 0) {
                throw new ParseException("Video offset not found in XMP metadata");
            }
            return metadata;
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse XMP metadata", e);
        }
    }

    private String getAttrOrChild(Element parent, String name) {
        String value = parent.getAttribute(name);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private static class MotionPhotoMetadata {
        private long videoOffset;
        private String videoMimeType;

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
    }
}
