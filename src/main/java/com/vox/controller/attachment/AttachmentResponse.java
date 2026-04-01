package com.vox.controller.attachment;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentResponse {
    private Long id;
    private Long coverId;
    private Long videoId;
    private String url;
    private String coverUrl;
    private String videoUrl;
    private String type;
    private String sourceType;
    private String originalName;
    private String mimeType;
    private Long size;
    private Integer width;
    private Integer height;
    private Float duration;
    private Boolean verified;
    private Long videoOffset;
    private LocalDateTime createdAt;
    private Long uploaderId;
}
