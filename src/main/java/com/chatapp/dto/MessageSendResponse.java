package com.chatapp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageSendResponse {
    private Long messageId;
    private String status;
    private LocalDateTime createdAt;
}
