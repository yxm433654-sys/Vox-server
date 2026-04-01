package com.vox.infrastructure.security;

import java.time.LocalDateTime;

public interface TokenService {
    String issueToken(Long userId, String username);

    LocalDateTime expiresAt();

    boolean validateForUser(String token, Long userId);
}
