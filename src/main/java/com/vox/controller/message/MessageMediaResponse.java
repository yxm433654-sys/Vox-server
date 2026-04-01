package com.vox.controller.message;

import lombok.Data;

@Data
public class MessageMediaResponse {
    private String mediaKind;
    private String processingStatus;
    private Long resourceId;
    private Long coverResourceId;
    private Long playResourceId;
    private String coverUrl;
    private String playUrl;
    private Integer width;
    private Integer height;
    private Float duration;
    private Float aspectRatio;
    private String sourceType;
}
