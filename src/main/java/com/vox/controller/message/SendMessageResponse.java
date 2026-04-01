package com.vox.controller.message;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SendMessageResponse {
    private Long messageId;
    private String status;
    private LocalDateTime createdAt;
}
