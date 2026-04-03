package com.vox.infrastructure.media.motion;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class MotionPhotoContainerParser {

    private static final String XMP_START = "<x:xmpmeta";
    private static final String XMP_END = "</x:xmpmeta>";
    private static final int INITIAL_SEARCH_SIZE = 256 * 1024;
    private static final int MAX_SEARCH_SIZE = 1024 * 1024;

    public Optional<MotionPhotoLayout> parse(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            return Optional.empty();
        }
        try {
            String xmp = extractXmpMetadata(sourceFile);
            if (xmp == null || xmp.isBlank()) {
                return Optional.empty();
            }

            List<ContainerItem> items = parseContainerItems(xmp);
            if (items.isEmpty()) {
                return Optional.empty();
            }

            int motionIndex = -1;
            for (int i = 0; i < items.size(); i += 1) {
                if ("motionphoto".equals(items.get(i).semantic())) {
                    motionIndex = i;
                    break;
                }
            }
            if (motionIndex < 0) {
                return Optional.empty();
            }

            ContainerItem primary = items.get(0);
            long primaryLength = determinePrimaryLength(sourceFile, primary.mime());
            long cursor = primaryLength + Math.max(0L, primary.padding());
            for (int i = 1; i < motionIndex; i += 1) {
                cursor += Math.max(0L, items.get(i).length());
            }

            ContainerItem motion = items.get(motionIndex);
            long videoLength = motion.length();
            if (videoLength <= 0 || cursor < 0 || cursor + videoLength > sourceFile.length()) {
                return Optional.empty();
            }

            return Optional.of(new MotionPhotoLayout(
                    primary.mime(),
                    primaryLength,
                    cursor,
                    videoLength,
                    items
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String extractXmpMetadata(File sourceFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
            long fileSize = raf.length();
            int initialSize = (int) Math.min(fileSize, INITIAL_SEARCH_SIZE);
            byte[] initial = new byte[initialSize];
            raf.seek(0);
            raf.readFully(initial);

            String xmp = findXmp(new String(initial, StandardCharsets.ISO_8859_1));
            if (xmp != null) {
                return xmp;
            }

            if (fileSize > initialSize) {
                int extendedSize = (int) Math.min(fileSize, MAX_SEARCH_SIZE);
                byte[] extended = new byte[extendedSize];
                raf.seek(0);
                raf.readFully(extended);
                xmp = findXmp(new String(extended, StandardCharsets.ISO_8859_1));
                if (xmp != null) {
                    return xmp;
                }
            }
        }
        return null;
    }

    private String findXmp(String content) {
        int start = content.indexOf(XMP_START);
        int end = content.indexOf(XMP_END);
        if (start >= 0 && end > start) {
            return content.substring(start, end + XMP_END.length());
        }
        return null;
    }

    private List<ContainerItem> parseContainerItems(String xmp) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmp)));

        NodeList listItems = doc.getElementsByTagName("rdf:li");
        List<ContainerItem> items = new ArrayList<>();
        for (int i = 0; i < listItems.getLength(); i += 1) {
            Node node = listItems.item(i);
            if (!(node instanceof Element li)) {
                continue;
            }

            Element itemElement = findItemElement(li);
            if (itemElement == null) {
                continue;
            }

            String mime = attrBySuffix(itemElement, "Mime");
            String semantic = attrBySuffix(itemElement, "Semantic");
            long length = parseLong(attrBySuffix(itemElement, "Length"));
            long padding = parseLong(attrBySuffix(itemElement, "Padding"));
            if (mime == null || semantic == null) {
                continue;
            }
            items.add(new ContainerItem(
                    mime.trim().toLowerCase(Locale.ROOT),
                    semantic.trim().toLowerCase(Locale.ROOT),
                    length,
                    padding
            ));
        }
        return items;
    }

    private Element findItemElement(Element li) {
        String liName = li.getNodeName();
        if (liName != null && liName.toLowerCase(Locale.ROOT).endsWith(":item")) {
            return li;
        }
        NodeList children = li.getChildNodes();
        for (int i = 0; i < children.getLength(); i += 1) {
            Node child = children.item(i);
            if (!(child instanceof Element element)) {
                continue;
            }
            String name = element.getNodeName();
            if (name != null && name.toLowerCase(Locale.ROOT).endsWith(":item")) {
                return element;
            }
        }
        return li;
    }

    private String attrBySuffix(Element element, String suffix) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i += 1) {
            Node attr = attrs.item(i);
            String name = attr.getNodeName();
            if (name == null) {
                continue;
            }
            if (name.equalsIgnoreCase(suffix) || name.toLowerCase(Locale.ROOT).endsWith(":" + suffix.toLowerCase(Locale.ROOT))) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    private long determinePrimaryLength(File sourceFile, String mime) throws IOException {
        if ("image/jpeg".equalsIgnoreCase(mime)) {
            return determineJpegLength(sourceFile);
        }
        throw new IOException("Unsupported primary image MIME for motion photo parsing: " + mime);
    }

    private long determineJpegLength(File sourceFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
            if ((raf.readUnsignedByte() != 0xFF) || (raf.readUnsignedByte() != 0xD8)) {
                throw new IOException("Not a JPEG file");
            }

            while (raf.getFilePointer() < raf.length()) {
                int prefix;
                do {
                    prefix = raf.readUnsignedByte();
                } while (prefix != 0xFF && raf.getFilePointer() < raf.length());

                int marker;
                do {
                    marker = raf.readUnsignedByte();
                } while (marker == 0xFF && raf.getFilePointer() < raf.length());

                if (marker == 0xD9) {
                    return raf.getFilePointer();
                }

                if (marker == 0xDA) {
                    while (raf.getFilePointer() < raf.length() - 1) {
                        int current = raf.readUnsignedByte();
                        if (current != 0xFF) {
                            continue;
                        }
                        int next = raf.readUnsignedByte();
                        if (next == 0x00 || (next >= 0xD0 && next <= 0xD7)) {
                            continue;
                        }
                        if (next == 0xD9) {
                            return raf.getFilePointer();
                        }
                        raf.seek(raf.getFilePointer() - 2);
                        break;
                    }
                    continue;
                }

                if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                    continue;
                }

                int segmentLength = raf.readUnsignedShort();
                if (segmentLength < 2) {
                    throw new IOException("Invalid JPEG segment length");
                }
                raf.seek(raf.getFilePointer() + segmentLength - 2L);
            }
        }
        throw new IOException("JPEG end marker not found");
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public record MotionPhotoLayout(
            String primaryMime,
            long primaryLength,
            long videoStart,
            long videoLength,
            List<ContainerItem> items
    ) {
    }

    public record ContainerItem(String mime, String semantic, long length, long padding) {
    }
}
