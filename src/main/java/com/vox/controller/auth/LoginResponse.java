package com.vox.controller.auth;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginResponse {
    private Long userId;
    private String username;
    private String token;
    private LocalDateTime expiresAt;
}
