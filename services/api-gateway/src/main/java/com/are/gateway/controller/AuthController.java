package com.are.gateway.controller;

import io.jsonwebtoken.Jwts;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SecretKey secretKey;

    public AuthController(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    @PostMapping("/token")
    public Map<String, String> generateToken(@RequestBody Map<String, String> request) {
        String username = request.getOrDefault("username", "anonymous");

        String token = Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(secretKey)
                .compact();

        return Map.of(
                "token", token,
                "type", "Bearer",
                "expiresIn", "3600");
    }
}
