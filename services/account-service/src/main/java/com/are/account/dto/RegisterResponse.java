package com.are.account.dto;

public record RegisterResponse(
        Long userId,
        String accountNumber,
        String message) {
}
