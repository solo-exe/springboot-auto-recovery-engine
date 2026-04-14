package com.are.account.dto;

public record OtpVerifyResponse(
        Long userId,
        Long confirmationId) {
}
