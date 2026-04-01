package com.vox.controller.message;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageResponse {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String type;
    private String content;
    private Long resourceId;
    private Long videoResourceId;
    private String coverUrl;
    private String videoUrl;
    private MessageMediaResponse media;
    private String status;
    private LocalDateTime createdAt;
}
