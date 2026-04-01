package com.vox.application.message;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageView {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String type;
    private String content;
    private Long resourceId;
    private Long videoResourceId;
    private String coverUrl;
    private String videoUrl;
    private MessageMediaView media;
    private String status;
    private LocalDateTime createdAt;
}
