package com.are.account.dto;

public record LoginResponse(
        String token,
        Long userId,
        String email,
        String role,
        long expiresIn) {
}
