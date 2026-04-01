package com.vox.application.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginResult {
    private Long userId;
    private String username;
    private String token;
    private LocalDateTime expiresAt;
}
