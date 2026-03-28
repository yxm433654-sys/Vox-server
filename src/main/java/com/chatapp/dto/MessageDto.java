package com.chatapp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String type;
    private String content;
    private Long resourceId;
    private Long videoResourceId;
    private String coverUrl;
    private String videoUrl;
    private String status;
    private LocalDateTime createdAt;
}
