package com.are.account.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * JWT token generation service.
 * Tokens are issued by account-service and validated by api-gateway.
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtService(
            @Value("${jwt.secret:auto-recovery-engine-secret-key-for-jwt-token-signing-256bit}") String jwtSecret,
            @Value("${jwt.expiration-seconds:86400}") long expirationSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "email", email,
                        "role", role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationSeconds, ChronoUnit.SECONDS)))
                .signWith(secretKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
