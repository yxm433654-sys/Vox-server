package com.vox.application.message;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SendMessageResult {
    private Long messageId;
    private String status;
    private LocalDateTime createdAt;
}
