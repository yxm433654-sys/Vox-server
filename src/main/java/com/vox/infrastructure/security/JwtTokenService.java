package com.vox.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;

@Component
public class JwtTokenService implements TokenService {

    private final SecretKey key;
    private final long expirySeconds;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-seconds}") long expirySeconds
    ) {
        this.key = Keys.hmacShaKeyFor(normalizeSecret(secret));
        this.expirySeconds = expirySeconds;
    }

    @Override
    public String issueToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirySeconds);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Override
    public LocalDateTime expiresAt() {
        return LocalDateTime.now().plusSeconds(expirySeconds);
    }

    @Override
    public boolean validateForUser(String token, Long userId) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String subject = claims.getSubject();
            if (subject == null) {
                return false;
            }
            return Long.parseLong(subject) == userId;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[] normalizeSecret(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 32) {
            return bytes;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (Exception ignored) {
            return bytes;
        }
    }
}
