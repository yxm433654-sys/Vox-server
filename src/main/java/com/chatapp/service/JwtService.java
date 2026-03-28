package com.chatapp.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirySeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-seconds}") long expirySeconds
    ) {
        this.key = Keys.hmacShaKeyFor(normalizeSecret(secret));
        this.expirySeconds = expirySeconds;
    }

    public String generateToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirySeconds);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public LocalDateTime getExpiryAt() {
        return LocalDateTime.now().plusSeconds(expirySeconds);
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateForUser(String token, Long userId) {
        try {
            Claims claims = parseClaims(token);
            String sub = claims.getSubject();
            if (sub == null) {
                return false;
            }
            return Long.parseLong(sub) == userId;
        } catch (Exception e) {
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
        } catch (Exception e) {
            return bytes;
        }
    }
}
