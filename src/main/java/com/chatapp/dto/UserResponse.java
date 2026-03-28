package com.chatapp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long userId;
    private String username;
    private String avatarUrl;
    private Byte status;
    private LocalDateTime createdAt;
}
